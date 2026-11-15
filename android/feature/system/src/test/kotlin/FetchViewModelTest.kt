package system

import data.CatalogEnrichmentFetcher
import data.CoreProvider
import data.settings.InMemoryTmdbSettings
import data.settings.TmdbSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun fetchViewModel(
    provider: CoreProvider,
    settings: TmdbSettings,
) = FetchViewModel(CatalogEnrichmentFetcher(provider, settings, dispatcher = Dispatchers.Unconfined))

class FetchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aFreshInstallHasNoKeyStored() =
        runTest {
            val viewModel = fetchViewModel(FakeCoreProvider(FakeCore()), InMemoryTmdbSettings())

            assertFalse(viewModel.state.value.hasKey)
        }

    @Test
    fun savingAKeyIsReflectedWithoutEverBeingHeldItself() =
        runTest {
            val settings = InMemoryTmdbSettings()
            val viewModel = fetchViewModel(FakeCoreProvider(FakeCore()), settings)

            viewModel.saveKey("a-fake-key")

            assertTrue(viewModel.state.value.hasKey)
        }
}
