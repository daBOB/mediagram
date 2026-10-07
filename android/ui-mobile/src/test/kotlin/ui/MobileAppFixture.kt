package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import data.settings.InMemoryLibrarySettings
import data.settings.InMemoryTelegramSettings
import data.settings.InMemoryTmdbSettings
import designsystem.InMemoryAppearanceSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import setup.AppearanceViewModel
import setup.ProfileSettingsViewModel
import setup.Libraries
import setup.SettingsViewModel
import setup.SetupViewModel
import setup.login.LoginViewModel
import testing.FakeCore
import uniffi.mediagram_core.AccountSummary
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.LibraryChoice

/** Real setup, login, settings and provider; native network/storage calls are the controlled boundary. */
internal class MobileAppFixture :
    ViewModelStoreOwner,
    AutoCloseable {
    val core =
        FakeCore(
            authorized = false,
            accountAnswer = AccountSummary("Viewer", "viewer"),
            libraries = listOf(LibraryChoice("films", "Family films")),
        )
    var passwordRequired = false

    /** Every (token, code) pair [setup.login.LoginViewModel.submitCode] actually sent, in order. */
    val signInCalls = mutableListOf<Pair<String, String>>()

    /** Every password [setup.login.LoginViewModel.submitPassword] actually sent, in order. */
    val passwordChecks = mutableListOf<String>()
    val installs = mutableListOf<String>()
    val installReady = CompletableDeferred<Unit>()
    val telegram = InMemoryTelegramSettings()
    val library = InMemoryLibrarySettings()
    val storage = InMemoryCoreStorage()
    private val dispatcher = Dispatchers.Main.immediate
    val provider = StoredCoreProvider(telegram, dispatcher) { core }
    private val libraries = Libraries(provider, library)
    val setup: SetupViewModel
    val login: LoginViewModel
    val flow: LibraryFlowFixture
    override val viewModelStore get() = flow.viewModelStore

    init {
        core.requestCodeAnswer = { "attempt-${core.requestedPhones.size}" }
        core.signInAnswer = { token, code ->
            signInCalls += token to code
            if (passwordRequired) {
                AuthOutcome.PASSWORD_NEEDED
            } else {
                core.authorized = true
                AuthOutcome.DONE
            }
        }
        core.checkPasswordAnswer = { password ->
            passwordChecks += password
            core.authorized = true
        }
        core.refreshLibraryAnswer = { handle ->
            installs += handle
            installReady.await()
            2L
        }
        val watchState = DefaultWatchStateRepository(provider, dispatcher)
        val settings = SettingsViewModel(provider, libraries, storage, telegram, dispatcher, watchState)
        flow = LibraryFlowFixture(settingsModel = settings)
        setup = SetupViewModel(provider, libraries, InMemoryTmdbSettings(), storage, dispatcher, watchState)
        login = LoginViewModel(provider, dispatcher)
        val models =
            mapOf<Class<out ViewModel>, ViewModel>(
                SetupViewModel::class.java to setup,
                LoginViewModel::class.java to login,
                // MobileApp itself resolves an AppearanceViewModel through
                // hiltViewModel() for MediagramTheme; this owner has to hand it
                // back too, the same reason it hands back the other two above.
                AppearanceViewModel::class.java to AppearanceViewModel(InMemoryAppearanceSettings()),
                ProfileSettingsViewModel::class.java to profileSettingsModel(),
            )
        val held =
            ViewModelProvider(
                viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.getValue(modelClass))!!
                },
            )
        models.keys.forEach { held[it] }
    }

    override fun close() = flow.close()
}
