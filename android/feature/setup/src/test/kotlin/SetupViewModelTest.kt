package setup

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import uniffi.mediagram_core.CoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * A device is one of a handful of arrangements of three stored things, and
 * the step shown has to be a function of that arrangement alone — no
 * remembered progress, because progress and reality disagree the moment
 * Telegram invalidates a session somewhere else.
 */
private const val NOTHING_PINNED = "That channel has nothing pinned. Run `mediagram push-index` there."

class SetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = SetupFixture()

    @Test
    fun aFreshInstallAsksForTheTelegramApplication() =
        runTest {
            assertIs<SetupUiState.NeedsApplication>(fixture.viewModel().state.value)
        }

    @Test
    fun anIdentityWithNoSessionAsksForSignIn() =
        runTest {
            fixture.telegram.write(1234, WELL_FORMED_HASH)

            assertEquals(SetupUiState.NeedsSignIn, fixture.viewModel().state.value)
        }

    @Test
    fun aSignedInDeviceWithNoLibraryAsksWhichLibrary() =
        runTest {
            fixture.signedIn()

            assertIs<SetupUiState.NeedsLibrary>(fixture.viewModel().state.value)
        }

    /**
     * The step cannot be shown without asking Telegram first, and nothing
     * else on the way here would have asked. A picker that arrived empty
     * and stayed empty until something else happened to poke it is the
     * failure this rules out.
     */
    @Test
    fun reachingTheLibraryStepFetchesWhatThereIsToChooseFrom() =
        runTest {
            val fixture = SetupFixture(core = FakeCore(libraries = listOf(choice("Films"), choice("Series"))))
            fixture.signedIn()

            val state = assertIs<SetupUiState.NeedsLibrary>(fixture.viewModel().state.value)

            assertEquals(listOf("Films", "Series"), state.choices?.map(LibraryOption::title))
        }

    @Test
    fun aDeviceWithALibraryAlreadyChosenOpensTheCatalog() =
        runTest {
            fixture.signedIn()
            fixture.library.write("a1b2c3")

            assertEquals(SetupUiState.Ready, fixture.viewModel().state.value)
        }

    @Test
    fun answeringTheFirstStepMovesOnToSigningIn() =
        runTest {
            val vm = fixture.viewModel()

            vm.submitApplication("1234", WELL_FORMED_HASH)

            assertEquals(SetupUiState.NeedsSignIn, vm.state.value)
            assertEquals(1234, fixture.telegram.read()?.apiId)
        }

    @Test
    fun aMalformedApiHashIsRejectedBeforeAnythingIsStored() =
        runTest {
            val vm = fixture.viewModel()

            vm.submitApplication("1234", "not a hash")

            assertIs<SetupUiState.NeedsApplication>(vm.state.value)
            assertNull(fixture.telegram.read(), "a value that failed its check must not reach storage")
        }

    @Test
    fun anApiIdThatIsNotANumberIsRejectedBeforeAnythingIsStored() =
        runTest {
            val vm = fixture.viewModel()

            vm.submitApplication("my application", WELL_FORMED_HASH)

            assertIs<SetupUiState.NeedsApplication>(vm.state.value)
            assertNull(fixture.telegram.read())
        }

    @Test
    fun aRejectedApiHashIsNeverQuotedBackOnScreen() =
        runTest {
            val vm = fixture.viewModel()
            val typed = "0123456789abcdef0123456789abcdefTOOLONG"

            vm.submitApplication("1234", typed)

            val message = assertIs<SetupUiState.NeedsApplication>(vm.state.value).error
            assertFalse(message.orEmpty().contains(typed), "a malformed hash is still key material")
        }

    @Test
    fun choosingALibraryInstallsItsCatalogAndOpensIt() =
        runTest {
            val fixture = SetupFixture(core = FakeCore(libraries = listOf(choice("Films", handle = "h-films"))))
            fixture.signedIn()
            val vm = fixture.viewModel()

            vm.chooseLibrary("h-films")

            assertEquals(SetupUiState.Ready, vm.state.value)
            assertEquals("h-films", fixture.core.installedHandle)
            assertEquals("h-films", fixture.library.read())
        }

    /**
     * The handle is written only once the catalog it names is on disk. One
     * written first would send every later launch straight past this step
     * to a library that was never installed, with no way back to the list.
     */
    @Test
    fun aLibraryThatCannotBeInstalledIsNotRememberedAsChosen() =
        runTest {
            val fixture = SetupFixture(core = FakeCore(libraries = listOf(choice("Films", handle = "h-films"))))
            fixture.signedIn()
            fixture.core.installFailure = CoreException.Library(NOTHING_PINNED)
            val vm = fixture.viewModel()

            vm.chooseLibrary("h-films")

            assertNull(fixture.library.read())
            val state = assertIs<SetupUiState.NeedsLibrary>(vm.state.value)
            assertEquals(listOf("Films"), state.choices?.map(LibraryOption::title), "the list is what to try next")
            assertEquals(NOTHING_PINNED, state.error)
        }

    /**
     * What the core says about a channel is written to be read: it names
     * what is wrong and what fixes it. The generated exception's own
     * message renders as `v1=...`, which is not that sentence.
     */
    @Test
    fun whatTheChannelSaidIsWhatTheScreenShows() =
        runTest {
            val fixture = SetupFixture(core = FakeCore(listFailure = CoreException.Library(NOTHING_PINNED)))
            fixture.signedIn()

            val state = assertIs<SetupUiState.NeedsLibrary>(fixture.viewModel().state.value)

            assertEquals(NOTHING_PINNED, state.error)
        }

    /**
     * A listing is the one answer that depends on a network being there.
     * Failing it must not cost the account its session — looking again is
     * the whole remedy.
     */
    @Test
    fun aListingThatFailedCanBeAskedForAgain() =
        runTest {
            val fixture =
                SetupFixture(
                    core =
                        FakeCore(
                            libraries = listOf(choice("Films")),
                            listFailure = CoreException.Network("the connection went away"),
                        ),
                )
            fixture.signedIn()
            val vm = fixture.viewModel()
            assertNull(assertIs<SetupUiState.NeedsLibrary>(vm.state.value).choices)

            fixture.core.listFailure = null
            vm.listLibraries()

            assertEquals(listOf("Films"), assertIs<SetupUiState.NeedsLibrary>(vm.state.value).choices?.map(LibraryOption::title))
        }

    @Test
    fun aSessionInvalidatedElsewhereFallsBackToSigningInRatherThanStranding() =
        runTest {
            fixture.signedIn()
            fixture.library.write("a1b2c3")
            val vm = fixture.viewModel()
            assertEquals(SetupUiState.Ready, vm.state.value)

            fixture.core.authorized = false
            vm.recheck()

            assertEquals(SetupUiState.NeedsSignIn, vm.state.value)
        }

    /**
     * Returning to the foreground re-derives the step, and that must not
     * wipe the reason the last thing typed was refused — a person coming
     * back has not stopped needing to read it.
     */
    @Test
    fun aMessageAlreadyOnScreenSurvivesComingBackToTheApp() =
        runTest {
            val vm = fixture.viewModel()
            vm.submitApplication("1234", "not a hash")
            val shown = assertIs<SetupUiState.NeedsApplication>(vm.state.value).error

            vm.recheck()

            assertEquals(shown, assertIs<SetupUiState.NeedsApplication>(vm.state.value).error)
        }

    /**
     * The same rule, for the step that paid for what is on screen: coming
     * back to the app must not throw the list away and fetch it again.
     */
    @Test
    fun aListAlreadyOnScreenIsNotFetchedAgainOnComingBack() =
        runTest {
            val fixture = SetupFixture(core = FakeCore(libraries = listOf(choice("Films"))))
            fixture.signedIn()
            val vm = fixture.viewModel()
            assertEquals(1, fixture.core.listCalls)

            vm.recheck()

            assertEquals(1, fixture.core.listCalls)
            assertEquals(listOf("Films"), assertIs<SetupUiState.NeedsLibrary>(vm.state.value).choices?.map(LibraryOption::title))
        }
}
