package setup

import data.CoreStorage
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import settings.InMemoryPackageSettings
import settings.InMemoryTelegramSettings
import settings.TelegramSettings

internal const val WELL_FORMED_HASH = "0123456789abcdef0123456789abcdef"
internal const val WELL_FORMED_KEY = "TfDJPK7L9tF2sVQm0aYcXbNrHgEuZiWoS4lKpQdRt1A="
internal const val LIBRARY_URL = "https://example.com/latest.json"

/**
 * One device's three stored answers, wired to a real [StoredCoreProvider]
 * rather than a stand-in for it: what the ViewModel shows is a function of
 * how that provider answers, so faking it out would test the wrong half.
 */
internal class SetupFixture(
    val core: FakeCore = FakeCore(),
    val telegram: TelegramSettings = InMemoryTelegramSettings(),
    val library: InMemoryPackageSettings = InMemoryPackageSettings(),
    val storage: CoreStorage = InMemoryCoreStorage(),
    private val build: () -> data.CoreClient = { core },
) {
    val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    fun viewModel(): SetupViewModel = SetupViewModel(
        StoredCoreProvider(telegram, dispatcher) { build() },
        library,
        storage,
        dispatcher,
    )
}
