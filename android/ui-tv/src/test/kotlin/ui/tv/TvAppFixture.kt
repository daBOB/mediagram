package ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import data.CoreClient
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import settings.InMemoryLibrarySettings
import settings.InMemoryTelegramSettings
import settings.InMemoryTmdbSettings
import setup.Libraries
import setup.SetupViewModel

/**
 * A real [SetupViewModel] over a mocked [CoreClient] — the minimum TvApp's
 * start rule needs, not ui-mobile's full login/library fixture. TvApp only
 * ever asks whether the outstanding step is `Ready`; it never drives sign-in
 * or library selection itself, so nothing here has to either.
 *
 * [ready] alone decides the outcome: an application identity is always on
 * record (otherwise the step would always be `NeedsApplication`, telling
 * this test nothing about the branch it exists to check), and only a
 * "ready" fixture also authorises the core and picks a library, the two
 * remaining questions [SetupViewModel] asks before it reports `Ready`.
 */
internal class TvAppFixture(ready: Boolean) : ViewModelStoreOwner, AutoCloseable {
    override val viewModelStore = ViewModelStore()
    val setup: SetupViewModel

    init {
        val core = mockk<CoreClient>()
        every { core.isAuthorized() } returns ready
        val telegram = InMemoryTelegramSettings()
        val library = InMemoryLibrarySettings()
        val dispatcher = Dispatchers.Main.immediate
        val provider = StoredCoreProvider(telegram, dispatcher) { core }
        val libraries = Libraries(provider, library, dispatcher)
        val watchState = DefaultWatchStateRepository(provider, dispatcher)
        runBlocking {
            telegram.write(1234, "0123456789abcdef0123456789abcdef")
            if (ready) library.write("films")
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
        val models = mapOf<Class<out ViewModel>, ViewModel>(SetupViewModel::class.java to setup)
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
