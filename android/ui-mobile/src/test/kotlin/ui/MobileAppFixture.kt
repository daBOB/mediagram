package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import data.CoreClient
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import designsystem.InMemoryAppearanceSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import settings.InMemoryLibrarySettings
import settings.InMemoryTelegramSettings
import settings.InMemoryTmdbSettings
import setup.AppearanceViewModel
import setup.Libraries
import setup.SettingsViewModel
import setup.SetupViewModel
import setup.login.LoginViewModel
import uniffi.mediagram_core.AccountSummary
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.LibraryChoice

/** Real setup, login, settings and provider; native network/storage calls are the controlled boundary. */
internal class MobileAppFixture :
    ViewModelStoreOwner,
    AutoCloseable {
    val core = mockk<CoreClient>()
    var authorized = false
    var authorizationReads = 0
    var passwordRequired = false
    val phoneRequests = mutableListOf<String>()
    val installs = mutableListOf<String>()
    val installReady = CompletableDeferred<Unit>()
    val telegram = InMemoryTelegramSettings()
    val library = InMemoryLibrarySettings()
    val storage = InMemoryCoreStorage()
    private val dispatcher = Dispatchers.Main.immediate
    val provider = StoredCoreProvider(telegram, dispatcher) { core }
    private val libraries = Libraries(provider, library, dispatcher)
    val setup: SetupViewModel
    val login: LoginViewModel
    val flow: LibraryFlowFixture
    override val viewModelStore get() = flow.viewModelStore

    init {
        every { core.isAuthorized() } answers {
            authorizationReads++
            authorized
        }
        every { core.dcId() } returns 4
        every { core.close() } returns Unit
        coEvery { core.account() } returns AccountSummary("Viewer", "viewer")
        coEvery { core.requestCode(any()) } coAnswers {
            phoneRequests += firstArg<String>()
            "attempt-${phoneRequests.size}"
        }
        coEvery { core.signIn(any(), any()) } coAnswers {
            if (passwordRequired) {
                AuthOutcome.PASSWORD_NEEDED
            } else {
                authorized = true
                AuthOutcome.DONE
            }
        }
        coEvery { core.checkPassword(any()) } coAnswers { authorized = true }
        coEvery { core.signOut() } coAnswers { authorized = false }
        coEvery { core.listLibraries() } returns listOf(LibraryChoice("films", "Family films"))
        coEvery { core.refreshLibrary(any()) } coAnswers {
            installs += firstArg<String>()
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
