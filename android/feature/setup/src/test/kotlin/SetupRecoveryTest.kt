package setup

import data.InMemoryCoreStorage
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What happens when the things setup asks are refused.
 *
 * Every question here goes through keystore-backed storage and the native
 * core, and both can fail for reasons no retype fixes — a backup restored
 * onto another device, a key the keystore has invalidated, a file a delete
 * will not touch. The flow has to land somewhere a person can act on, and
 * it must not leave the device in a state a first run could never produce.
 */
class SetupRecoveryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startingOverClearsTheIdentityTheLibraryAndTheSessionTogether() =
        runTest {
            val fixture = SetupFixture()
            fixture.signedIn()
            fixture.library.write("a1b2c3")
            val vm = fixture.viewModel()

            vm.startOver()

            assertNull(fixture.telegram.read())
            assertNull(fixture.library.read())
            assertTrue(
                (fixture.storage as InMemoryCoreStorage).cleared,
                "forgetting the credentials leaves a working session behind on its own",
            )
            assertIs<SetupUiState.NeedsApplication>(vm.state.value)
        }

    /**
     * Storage refusing is not a channel refusing. A keystore that will not
     * take the chosen handle is past what picking differently can fix, so
     * it has to land on the state that offers the way out rather than back
     * on the list, where every further pick would fail the same way.
     */
    @Test
    fun aChoiceThatCannotBeStoredOffersTheWayOutRatherThanThePickerAgain() =
        runTest {
            val fixture =
                SetupFixture(
                    core = FakeCore(libraries = listOf(choice("Films", handle = "h-films"))),
                    library = RefusingLibrarySettings(),
                )
            fixture.signedIn()
            val vm = fixture.viewModel()

            vm.chooseLibrary("h-films")

            assertIs<SetupUiState.Failed>(vm.state.value)
        }

    /**
     * A Telegram auth key binds to the datacentre, not to the api id it was
     * obtained under. So a session left on disk with no identity beside it
     * is inherited wholesale by whatever identity is typed in next — which
     * is why the files go first and the credentials last, and why a reset
     * that could not finish must leave both halves standing.
     */
    @Test
    fun aResetThatCannotDeleteTheSessionKeepsTheIdentityAndSaysSo() =
        runTest {
            val fixture = SetupFixture(storage = InMemoryCoreStorage(failWith = IllegalStateException("read-only")))
            fixture.signedIn()
            fixture.library.write("a1b2c3")
            val vm = fixture.viewModel()

            vm.startOver()

            assertNotNull(fixture.telegram.read(), "the identity must not be dropped while its session survives")
            assertNotNull(fixture.library.read())
            assertIs<SetupUiState.Failed>(vm.state.value)
        }

    /**
     * The keystore refuses after a restore onto another device. Every
     * question the flow asks goes through it, so an unguarded failure is a
     * spinner that never resolves — or, escaping the ViewModel's scope, a
     * crash on every launch with no screen reached to offer a way out.
     */
    @Test
    fun storageThatWillNotAnswerLandsOnAFailureRatherThanASpinner() =
        runTest {
            val vm = SetupFixture(telegram = RefusingTelegramSettings()).viewModel()

            assertIs<SetupUiState.Failed>(vm.state.value)
        }

    /**
     * An identity written before the core it produces is proven to build
     * turns one bad entry into a launch crash loop: every later launch
     * reads it back, fails the same way, and never reaches a screen that
     * could clear it.
     */
    @Test
    fun anIdentityThatCannotBuildACoreIsNotLeftBehindToFailAgain() =
        runTest {
            val fixture = SetupFixture(build = { error("the native core would not load") })
            val vm = fixture.viewModel()

            vm.submitApplication("1234", WELL_FORMED_HASH)

            assertIs<SetupUiState.Failed>(vm.state.value)
            assertNull(fixture.telegram.read(), "a stored identity that always fails is a crash loop with no way out")
        }
}
