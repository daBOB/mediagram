package setup

import data.InMemoryCoreStorage
import data.StoredCoreProvider
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.InMemoryPackageSettings
import settings.InMemoryTelegramSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"
private const val WELL_FORMED_KEY = "TfDJPK7L9tF2sVQm0aYcXbNrHgEuZiWoS4lKpQdRt1A="
private const val LIBRARY_URL = "https://example.com/latest.json"

/**
 * A device is one of a handful of arrangements of three stored things, and
 * the step shown has to be a function of that arrangement alone — no
 * remembered progress, because progress and reality disagree the moment
 * Telegram invalidates a session somewhere else.
 */
class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val telegram = InMemoryTelegramSettings()
    private val library = InMemoryPackageSettings()
    private val storage = InMemoryCoreStorage()
    private val core = FakeCore()

    private fun viewModel() =
        SetupViewModel(StoredCoreProvider(telegram) { core }, library, storage)

    @Test
    fun aFreshInstallAsksForTheTelegramApplication() = runTest {
        assertIs<SetupUiState.NeedsApplication>(viewModel().state.value)
    }

    @Test
    fun anIdentityWithNoSessionAsksForSignIn() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)

        assertEquals(SetupUiState.NeedsSignIn, viewModel().state.value)
    }

    @Test
    fun aSignedInDeviceWithNoLibraryAsksForTheLibrary() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true

        assertIs<SetupUiState.NeedsLibrary>(viewModel().state.value)
    }

    @Test
    fun aDeviceWithEverythingStoredOpensTheCatalog() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true
        library.write(LIBRARY_URL, WELL_FORMED_KEY)

        assertEquals(SetupUiState.Ready, viewModel().state.value)
    }

    @Test
    fun answeringTheFirstStepMovesOnToSigningIn() = runTest {
        val vm = viewModel()

        vm.submitApplication("1234", WELL_FORMED_HASH)

        assertEquals(SetupUiState.NeedsSignIn, vm.state.value)
        assertEquals(1234, telegram.read()?.apiId)
    }

    @Test
    fun aMalformedApiHashIsRejectedBeforeAnythingIsStored() = runTest {
        val vm = viewModel()

        vm.submitApplication("1234", "not a hash")

        assertIs<SetupUiState.NeedsApplication>(vm.state.value)
        assertNull(telegram.read(), "a value that failed its check must not reach storage")
    }

    @Test
    fun anApiIdThatIsNotANumberIsRejectedBeforeAnythingIsStored() = runTest {
        val vm = viewModel()

        vm.submitApplication("my application", WELL_FORMED_HASH)

        assertIs<SetupUiState.NeedsApplication>(vm.state.value)
        assertNull(telegram.read())
    }

    @Test
    fun aRejectedApiHashIsNeverQuotedBackOnScreen() = runTest {
        val vm = viewModel()
        val typed = "0123456789abcdef0123456789abcdefTOOLONG"

        vm.submitApplication("1234", typed)

        val message = assertIs<SetupUiState.NeedsApplication>(vm.state.value).error
        assertFalse(message.orEmpty().contains(typed), "a malformed hash is still key material")
    }

    @Test
    fun aMalformedLibraryKeyIsRejectedBeforeAnythingIsStored() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true
        val vm = viewModel()

        vm.submitLibrary(LIBRARY_URL, "not a key")

        assertIs<SetupUiState.NeedsLibrary>(vm.state.value)
        assertNull(library.read())
    }

    @Test
    fun aLibraryAddressWithNoSchemeIsRejectedBeforeAnythingIsStored() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true
        val vm = viewModel()

        vm.submitLibrary("example.com/latest.json", WELL_FORMED_KEY)

        assertIs<SetupUiState.NeedsLibrary>(vm.state.value)
        assertNull(library.read())
    }

    @Test
    fun aSessionInvalidatedElsewhereFallsBackToSigningInRatherThanStranding() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true
        library.write(LIBRARY_URL, WELL_FORMED_KEY)
        val vm = viewModel()
        assertEquals(SetupUiState.Ready, vm.state.value)

        core.authorized = false
        vm.recheck()

        assertEquals(SetupUiState.NeedsSignIn, vm.state.value)
    }

    @Test
    fun startingOverClearsTheIdentityTheLibraryAndTheSessionTogether() = runTest {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true
        library.write(LIBRARY_URL, WELL_FORMED_KEY)
        val vm = viewModel()

        vm.startOver()

        assertNull(telegram.read())
        assertNull(library.read())
        assertTrue(storage.cleared, "forgetting the credentials leaves a working session behind on its own")
        assertIs<SetupUiState.NeedsApplication>(vm.state.value)
    }
}
