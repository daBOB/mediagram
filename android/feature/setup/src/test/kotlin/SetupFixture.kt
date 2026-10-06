package setup

import data.CoreStorage
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import settings.InMemoryLibrarySettings
import settings.InMemoryTelegramSettings
import settings.InMemoryTmdbSettings
import settings.LibrarySettings
import settings.TelegramSettings
import settings.TmdbSettings
import testing.FakeCore
import testing.FakeCoreHandle
import uniffi.mediagram_core.LibraryChoice

internal const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"

/**
 * One device's stored answers, wired to a real [StoredCoreProvider] rather
 * than a stand-in for it: what the ViewModel shows is a function of how
 * that provider answers, so faking it out would test the wrong half.
 *
 * [FakeCore.authorized] defaults `false` here, unlike the shared fake's own
 * default: most of this module's tests are about a device mid setup, which
 * a device that already looks signed in would skip past. [build] answers
 * [FakeCoreHandle] rather than [core] itself so a test that needs the
 * provider to hand out something other than a plain [FakeCore] — a
 * replacement identity, a close that fails once — still satisfies the
 * provider's own `CoreInterface`/`AutoCloseable` bound.
 */
internal class SetupFixture(
    val core: FakeCore = FakeCore(authorized = false),
    val telegram: TelegramSettings = InMemoryTelegramSettings(),
    val library: LibrarySettings = InMemoryLibrarySettings(),
    val tmdb: TmdbSettings = InMemoryTmdbSettings(),
    val storage: CoreStorage = InMemoryCoreStorage(),
    private val build: () -> FakeCoreHandle = { core },
) {
    val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
    val provider = StoredCoreProvider(telegram, dispatcher) { build() }
    val watchState = DefaultWatchStateRepository(provider, dispatcher)

    fun viewModel(): SetupViewModel =
        SetupViewModel(provider, Libraries(provider, library), tmdb, storage, dispatcher, watchState)

    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(provider, Libraries(provider, library), storage, telegram, dispatcher, watchState)

    /** A device that has answered everything up to the library question. */
    suspend fun signedIn(): SetupFixture =
        apply {
            telegram.write(1234, WELL_FORMED_HASH)
            core.authorized = true
        }
}

/** A library as the core would list it, so a test only names its title. */
fun choice(
    title: String,
    handle: String = title.lowercase().replace(" ", "-"),
): LibraryChoice = LibraryChoice(handle = handle, title = title)
