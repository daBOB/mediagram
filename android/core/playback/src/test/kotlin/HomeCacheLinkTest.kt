package playback

import data.CatalogEnrichmentFetcher
import data.HomeCacheServer
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val URL = "http://192.168.0.240:7788"
private val SERVER = LanServer(URL, "192.168.0.240:7788")

class HomeCacheLinkTest {
    @Test
    fun aServerChangeReachesTheCore() =
        runTest {
            val core = FakeCore()
            val home = HomeCacheServer()
            val server = MutableStateFlow<LanServer?>(null)
            val changes = MutableStateFlow(0)
            var token = "first-token"
            backgroundScope.linkHomeCache(MutableStateFlow(core), server, changes, home, enabled = { true }, token = { token })
            runCurrent()

            server.value = SERVER
            runCurrent()
            assertTrue(home.connected.value)
            token = "second-token"
            changes.value = 1
            runCurrent()
            server.value = null
            runCurrent()

            assertEquals(listOf(null to null, URL to "first-token", URL to "second-token", null to null), core.homeCacheCalls)
            assertFalse(home.connected.value)
        }

    @Test
    fun switchingTheLanCacheOffTellsTheCoreThereIsNoServer() =
        runTest {
            val core = FakeCore()
            val home = HomeCacheServer()
            val changes = MutableStateFlow(0)
            var enabled = true
            backgroundScope.linkHomeCache(MutableStateFlow(core), MutableStateFlow(SERVER), changes, home, enabled = { enabled }, token = { "token" })
            runCurrent()

            enabled = false
            changes.value = 1
            runCurrent()

            assertEquals(listOf(URL to "token", null to null), core.homeCacheCalls)
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings(), homeCache = home)
            assertNull(enrichment.fetch())
            assertEquals(0, core.fetchCalls, "with the switch off a device with no key fetches nothing, as away from home")
            assertFalse(enrichment.state.value.viaHomeCache)
        }
}
