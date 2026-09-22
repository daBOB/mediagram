package setup

import data.CoreStorage
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import settings.InMemoryLibrarySettings
import settings.InMemoryTmdbSettings
import settings.LibrarySettings
import settings.InMemoryTelegramSettings
import settings.TelegramSettings
import settings.TmdbSettings

internal const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"

/**
 * One device's stored answers, wired to a real [StoredCoreProvider] rather
 * than a stand-in for it: what the ViewModel shows is a function of how
 * that provider answers, so faking it out would test the wrong half.
 */
internal class SetupFixture(
    val core: FakeCore = FakeCore(),
    val telegram: TelegramSettings = InMemoryTelegramSettings(),
    val library: LibrarySettings = InMemoryLibrarySettings(),
    val tmdb: TmdbSettings = InMemoryTmdbSettings(),
    val storage: CoreStorage = InMemoryCoreStorage(),
    private val build: () -> data.CoreClient = { core },
) {
    val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    fun viewModel(): SetupViewModel {
        val provider = StoredCoreProvider(telegram, dispatcher) { build() }
        return SetupViewModel(provider, Libraries(provider, library, dispatcher), tmdb, storage, dispatcher)
    }

    fun settingsViewModel(): SettingsViewModel {
        val provider = StoredCoreProvider(telegram, dispatcher) { build() }
        return SettingsViewModel(provider, Libraries(provider, library, dispatcher), storage, telegram, dispatcher)
    }

    /** A device that has answered everything up to the library question. */
    suspend fun signedIn(): SetupFixture = apply {
        telegram.write(1234, WELL_FORMED_HASH)
        core.authorized = true
    }
}
