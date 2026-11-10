package playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCacheLinkTest {
    @Test
    fun aServerChangeReachesTheCore() =
        runTest {
            val core = FakeCore()
            val server = MutableStateFlow<LanServer?>(null)
            val saves = MutableStateFlow(0)
            var token = "first-token"
            backgroundScope.linkHomeCache(MutableStateFlow(core), server, saves) { token }
            runCurrent()

            server.value = LanServer("http://192.168.0.240:7788", "192.168.0.240:7788")
            runCurrent()
            token = "second-token"
            saves.value = 1
            runCurrent()
            server.value = null
            runCurrent()

            assertEquals(
                listOf(
                    null to null,
                    "http://192.168.0.240:7788" to "first-token",
                    "http://192.168.0.240:7788" to "second-token",
                    null to null,
                ),
                core.homeCacheCalls,
            )
        }
}
