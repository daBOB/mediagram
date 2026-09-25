package ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import catalog.profile.ProfileViewModel
import data.CoreClient
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import model.Profile
import settings.InMemoryLibrarySettings
import settings.InMemoryTelegramSettings
import settings.InMemoryTmdbSettings
import setup.Libraries
import setup.SetupViewModel
import setup.login.LoginViewModel
import uniffi.mediagram_core.LibraryChoice

/** Which of [SetupViewModel]'s outstanding steps a [TvAppFixture] should land on. */
internal enum class TvSetupStage { APPLICATION, SIGN_IN, LIBRARY, READY }

/**
 * A real [SetupViewModel] over a mocked [CoreClient] — the minimum TvApp's
 * setup screens need, not ui-mobile's full login/library fixture. TvApp and
 * `TvSetupStep` only ever drive [SetupViewModel] itself; sign-in and library
 * selection each have their own ViewModel, which nothing under test here
 * reaches into.
 *
 * [stage] alone decides the outcome: an application identity is on record
 * for every stage but [TvSetupStage.APPLICATION] (otherwise the step would
 * always be `NeedsApplication`, telling a test nothing about the branch it
 * exists to check), the core reports authorized from [TvSetupStage.LIBRARY]
 * on, and only [TvSetupStage.READY] also picks a library — the three
 * remaining questions [SetupViewModel] asks before it reports `Ready`.
 *
 * [profiles] and [chosenProfileId] back a real [ProfileViewModel] the same
 * way — `TvApp` resolves one through `TvProfileGate` the moment `Ready` is
 * reached, so any test that reaches `READY` needs one ready to resolve too,
 * over [FakeWatchStateRepository] rather than the `CoreClient` mock the
 * setup plumbing above uses: that mock is stubbed only for the calls
 * `SetupViewModel` itself makes.
 */
internal class TvAppFixture(
    stage: TvSetupStage,
    profiles: List<Profile> = emptyList(),
    chosenProfileId: String? = null,
) : ViewModelStoreOwner, AutoCloseable {
    override val viewModelStore = ViewModelStore()
    val setup: SetupViewModel
    private val login: LoginViewModel
    private val profile: ProfileViewModel

    init {
        val core = mockk<CoreClient>()
        every { core.isAuthorized() } returns (stage == TvSetupStage.LIBRARY || stage == TvSetupStage.READY)
        coEvery { core.listLibraries() } returns listOf(LibraryChoice("films", "Family films"))
        val telegram = InMemoryTelegramSettings()
        val library = InMemoryLibrarySettings()
        val dispatcher = Dispatchers.Main.immediate
        val provider = StoredCoreProvider(telegram, dispatcher) { core }
        val libraries = Libraries(provider, library, dispatcher)
        val watchState = DefaultWatchStateRepository(provider, dispatcher)
        runBlocking {
            if (stage != TvSetupStage.APPLICATION) telegram.write(1234, "0123456789abcdef0123456789abcdef")
            if (stage == TvSetupStage.READY) library.write("films")
        }
        setup =
            SetupViewModel(
                coreProvider = provider,
                libraries = libraries,
                tmdbSettings = InMemoryTmdbSettings(),
                coreStorage = InMemoryCoreStorage(),
                dispatcher = dispatcher,
                watchState = watchState,
            )
        // TvSignInScreen and TvProfileGate each resolve their own ViewModel
        // through hiltViewModel(), the same way TvApp resolves SetupViewModel
        // — this ViewModelStoreOwner has to be able to hand back all three,
        // or reaching that step through TvApp falls back to
        // ViewModelProvider's default factory, which cannot construct one
        // with no Hilt entry point to supply its arguments.
        login = LoginViewModel(provider, dispatcher)
        profile = ProfileViewModel(FakeWatchStateRepository(profiles, chosenProfileId), NoopWatchSync)
        val models =
            mapOf<Class<out ViewModel>, ViewModel>(
                SetupViewModel::class.java to setup,
                LoginViewModel::class.java to login,
                ProfileViewModel::class.java to profile,
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

    override fun close() = viewModelStore.clear()
}
