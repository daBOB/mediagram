package playback

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the byte path has done since the process started, which is the honest
 * scope: these are read to answer "what is this app doing", and a counter
 * reset per title would answer a different question.
 */
class PlaybackCountersTest {
    @Test
    fun afreshProcessHasDoneNothing() {
        assertEquals(PlaybackTotals(0, 0, 0, 0), PlaybackCounters().totals())
    }

    @Test
    fun aFetchIsCountedOnceAndCarriesItsBytes() {
        val counters = PlaybackCounters()

        counters.fetched(1_048_576)
        counters.fetched(524_288)

        val totals = counters.totals()
        assertEquals(2, totals.fetches)
        assertEquals(1_572_864, totals.fromUpstreamBytes)
    }

    /**
     * A read served from disk never reached Telegram, and counting it as
     * upstream would make a cache that is working look like a link that is
     * busy — the exact opposite of what the number is read for.
     */
    @Test
    fun bytesFromDiskAreNotBytesFromTheNetwork() {
        val counters = PlaybackCounters()

        counters.servedFromCache(2_000)
        counters.fetched(1_000)

        val totals = counters.totals()
        assertEquals(2_000, totals.fromCacheBytes)
        assertEquals(1_000, totals.fromUpstreamBytes)
        assertEquals(1, totals.fetches)
    }

    /**
     * A failure brought no bytes. Counting it as a fetch would quietly
     * improve the average size of one.
     */
    @Test
    fun aFailedReadCountsAsAFailureAndNothingElse() {
        val counters = PlaybackCounters()

        counters.readFailed()

        val totals = counters.totals()
        assertEquals(1, totals.failedReads)
        assertEquals(0, totals.fetches)
        assertEquals(0, totals.fromUpstreamBytes)
    }

    @Test
    fun beforeAnyReadThereIsNoLastSource() {
        assertEquals(null, PlaybackCounters().lastRead())
    }

    @Test
    fun aLanHitIsCountedAndBecomesTheLastSource() {
        val counters = PlaybackCounters()

        counters.lanHit("192.168.1.5:7788")

        assertEquals(1, counters.totals().lanHits)
        assertEquals(LastRead(ReadSource.LAN, "192.168.1.5:7788"), counters.lastRead())
    }

    @Test
    fun aLanMissIsCountedButDoesNotChangeTheLastSourceOnItsOwn() {
        val counters = PlaybackCounters()

        counters.lanMiss()

        assertEquals(1, counters.totals().lanMisses)
        assertEquals(null, counters.lastRead())
    }

    @Test
    fun theMostRecentReadWinsRegardlessOfWhichSourceItCameFrom() {
        val counters = PlaybackCounters()

        counters.lanHit("192.168.1.5:7788")
        counters.fetched(1_000)

        assertEquals(LastRead(ReadSource.TELEGRAM, null), counters.lastRead())
    }
}
