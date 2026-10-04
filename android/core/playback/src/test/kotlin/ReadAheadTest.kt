package playback

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bytes a set of [mbps] average needs to last [seconds]. */
private fun bytesFor(
    mbps: Double,
    seconds: Long,
): Long = (mbps * 1_000_000 / 8 * seconds).toLong()

private const val TWO_HOURS = 7_200L

class ReadAheadTest {
    @Test
    fun episodesAndOrdinaryFilmsStaySequential() {
        assertEquals(1, readAheadWidth(bytesFor(3.0, 2_700), 2_700), "a 3 Mbit/s episode")
        assertEquals(1, readAheadWidth(bytesFor(8.0, TWO_HOURS), TWO_HOURS), "an 8 Mbit/s 1080p film")
    }

    @Test
    fun heavierSetsGetJustEnoughFetchesToArriveTwiceAsFastAsTheyPlay() {
        assertEquals(2, readAheadWidth(bytesFor(12.0, TWO_HOURS), TWO_HOURS), "a 1080p remux")
        assertEquals(3, readAheadWidth(bytesFor(20.0, TWO_HOURS), TWO_HOURS))
    }

    @Test
    fun the4kFilmsThatRebufferedGetTheWholeWindow() {
        assertEquals(MAX_READ_AHEAD, readAheadWidth(bytesFor(26.7, 10_560), 10_560), "The Batman, Dolby Vision")
        assertEquals(MAX_READ_AHEAD, readAheadWidth(bytesFor(28.0, 10_200), 10_200), "Heat, HDR10")
        assertEquals(MAX_READ_AHEAD, readAheadWidth(bytesFor(65.0, TWO_HOURS), TWO_HOURS), "never more than the cap")
    }

    @Test
    fun aSetWithNoKnownDurationStaysSequential() {
        assertEquals(1, readAheadWidth(bytesFor(30.0, TWO_HOURS), null))
        assertEquals(1, readAheadWidth(bytesFor(30.0, TWO_HOURS), 0))
    }

    @Test
    fun aSetsDurationIsLookedUpOnce() =
        runTest {
            val asked = mutableListOf<String>()
            val readAhead = ReadAhead(durationSecs = { asked += it; TWO_HOURS })

            repeat(3) { assertEquals(MAX_READ_AHEAD, readAhead.width("film", bytesFor(30.0, TWO_HOURS))) }

            assertEquals(listOf("film"), asked)
        }

    @Test
    fun aLookupThatFailsCostsOnlyTheWindowAndIsAskedAgainNextTime() =
        runTest {
            var calls = 0
            val readAhead =
                ReadAhead(durationSecs = {
                    calls++
                    if (calls == 1) throw IllegalStateException("no core yet") else TWO_HOURS
                })

            assertEquals(1, readAhead.width("film", bytesFor(30.0, TWO_HOURS)))
            assertEquals(MAX_READ_AHEAD, readAhead.width("film", bytesFor(30.0, TWO_HOURS)))
        }

    @Test
    fun everyFailureRestartsTheBackoff() =
        runTest {
            var now = 0L
            val readAhead = ReadAhead(durationSecs = { TWO_HOURS }, backoffMs = 60_000, clock = { now })
            val film = bytesFor(30.0, TWO_HOURS)

            readAhead.failed()
            now = 50_000
            readAhead.failed()
            now = 100_000
            assertEquals(1, readAhead.width("film", film), "50 s after the second failure is still inside its backoff")

            now = 110_000
            assertEquals(MAX_READ_AHEAD, readAhead.width("film", film))
        }
}
