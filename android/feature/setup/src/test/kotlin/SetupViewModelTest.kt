package setup

import kotlinx.coroutines.test.runTest
import org.junit.Rule
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
class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = SetupFixture()

    @Test
    fun aFreshInstallAsksForTheTelegramApplication() = runTest {
        assertIs<SetupUiState.NeedsApplication>(fixture.viewModel().state.value)
    }

    @Test
    fun anIdentityWithNoSessionAsksForSignIn() = runTest {
        fixture.telegram.write(1234, WELL_FORMED_HASH)

        assertEquals(SetupUiState.NeedsSignIn, fixture.viewModel().state.value)
    }

    @Test
    fun aSignedInDeviceWithNoLibraryAsksForTheLibrary() = runTest {
        fixture.telegram.write(1234, WELL_FORMED_HASH)
        fixture.core.authorized = true

        assertIs<SetupUiState.NeedsLibrary>(fixture.viewModel().state.value)
    }

    @Test
    fun aDeviceWithEverythingStoredOpensTheCatalog() = runTest {
        fixture.telegram.write(1234, WELL_FORMED_HASH)
        fixture.core.authorized = true
        fixture.library.write(LIBRARY_URL, WELL_FORMED_KEY)

        assertEquals(SetupUiState.Ready, fixture.viewModel().state.value)
    }

    @Test
    fun answeringTheFirstStepMovesOnToSigningIn() = runTest {
        val vm = fixture.viewModel()

        vm.submitApplication("1234", WELL_FORMED_HASH)

        assertEquals(SetupUiState.NeedsSignIn, vm.state.value)
        assertEquals(1234, fixture.telegram.read()?.apiId)
    }

    @Test
    fun aMalformedApiHashIsRejectedBeforeAnythingIsStored() = runTest {
        val vm = fixture.viewModel()

        vm.submitApplication("1234", "not a hash")

        assertIs<SetupUiState.NeedsApplication>(vm.state.value)
        assertNull(fixture.telegram.read(), "a value that failed its check must not reach storage")
    }

    @Test
    fun anApiIdThatIsNotANumberIsRejectedBeforeAnythingIsStored() = runTest {
        val vm = fixture.viewModel()

        vm.submitApplication("my application", WELL_FORMED_HASH)

        assertIs<SetupUiState.NeedsApplication>(vm.state.value)
        assertNull(fixture.telegram.read())
    }

    @Test
    fun aRejectedApiHashIsNeverQuotedBackOnScreen() = runTest {
        val vm = fixture.viewModel()
        val typed = "0123456789abcdef0123456789abcdefTOOLONG"

        vm.submitApplication("1234", typed)

        val message = assertIs<SetupUiState.NeedsApplication>(vm.state.value).error
        assertFalse(message.orEmpty().contains(typed), "a malformed hash is still key material")
    }

    @Test
    fun aMalformedLibraryKeyIsRejectedBeforeAnythingIsStored() = runTest {
        fixture.telegram.write(1234, WELL_FORMED_HASH)
        fixture.core.authorized = true
        val vm = fixture.viewModel()

        vm.submitLibrary(LIBRARY_URL, "not a key")

        assertIs<SetupUiState.NeedsLibrary>(vm.state.value)
        assertNull(fixture.library.read())
    }

    @Test
    fun aLibraryAddressWithNoSchemeIsRejectedBeforeAnythingIsStored() = runTest {
        fixture.telegram.write(1234, WELL_FORMED_HASH)
        fixture.core.authorized = true
        val vm = fixture.viewModel()

        vm.submitLibrary("example.com/latest.json", WELL_FORMED_KEY)

        assertIs<SetupUiState.NeedsLibrary>(vm.state.value)
        assertNull(fixture.library.read())
    }

    @Test
    fun aSessionInvalidatedElsewhereFallsBackToSigningInRatherThanStranding() = runTest {
        fixture.telegram.write(1234, WELL_FORMED_HASH)
        fixture.core.authorized = true
        fixture.library.write(LIBRARY_URL, WELL_FORMED_KEY)
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
    fun aMessageAlreadyOnScreenSurvivesComingBackToTheApp() = runTest {
        val vm = fixture.viewModel()
        vm.submitApplication("1234", "not a hash")
        val shown = assertIs<SetupUiState.NeedsApplication>(vm.state.value).error

        vm.recheck()

        assertEquals(shown, assertIs<SetupUiState.NeedsApplication>(vm.state.value).error)
    }
}
