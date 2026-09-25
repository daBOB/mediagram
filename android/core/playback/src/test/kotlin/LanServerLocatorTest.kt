package playback

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun probeThatVerifies(vararg goodUrls: String) = LanServerProbe { it in goodUrls }

class LanServerLocatorTest {
    @Test
    fun theFirstDiscoveredServerToVerifyIsPicked() =
        runTest {
            val discovered =
                listOf(
                    LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"),
                    LanServer("http://10.0.0.3:7788", "10.0.0.3:7788"),
                )

            val picked = pickLanServer(discovered, manualOverride = null, probe = probeThatVerifies("http://10.0.0.3:7788"))

            assertEquals(LanServer("http://10.0.0.3:7788", "10.0.0.3:7788"), picked)
        }

    @Test
    fun aServerThatDoesNotVerifyIsSkippedInFavourOfTheNextOne() =
        runTest {
            val discovered =
                listOf(
                    LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"),
                    LanServer("http://10.0.0.3:7788", "10.0.0.3:7788"),
                )

            val picked =
                pickLanServer(
                    discovered,
                    manualOverride = null,
                    probe = probeThatVerifies("http://10.0.0.2:7788", "http://10.0.0.3:7788"),
                )

            assertEquals(LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"), picked, "the first one listed wins when both verify")
        }

    @Test
    fun theManualOverrideWinsEvenWhenListedAfterADiscoveredServer() =
        runTest {
            val discovered = listOf(LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"))

            val picked =
                pickLanServer(
                    discovered,
                    manualOverride = "http://10.0.0.9:7788",
                    probe = probeThatVerifies("http://10.0.0.2:7788", "http://10.0.0.9:7788"),
                )

            assertEquals(LanServer("http://10.0.0.9:7788", "10.0.0.9:7788"), picked)
        }

    @Test
    fun aManualOverrideThatDoesNotVerifyFallsBackToDiscovery() =
        runTest {
            val discovered = listOf(LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"))

            val picked =
                pickLanServer(
                    discovered,
                    manualOverride = "http://10.0.0.9:7788",
                    probe = probeThatVerifies("http://10.0.0.2:7788"),
                )

            assertEquals(LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"), picked)
        }

    @Test
    fun nothingVerifyingMeansNoServer() =
        runTest {
            val discovered = listOf(LanServer("http://10.0.0.2:7788", "10.0.0.2:7788"))

            val picked = pickLanServer(discovered, manualOverride = null, probe = probeThatVerifies())

            assertNull(picked)
        }

    @Test
    fun noCandidatesAtAllMeansNoServer() =
        runTest {
            assertNull(pickLanServer(emptyList(), manualOverride = null, probe = probeThatVerifies()))
        }
}
