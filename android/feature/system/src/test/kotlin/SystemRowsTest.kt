package system

import kotlin.test.Test
import model.heldOfBudget
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How the System screen says a number. Mirrors the web player's status panel:
 * a row whose value is not known is left out entirely rather than shown
 * blank, because a blank row reads as a broken value rather than an absent one.
 */
class SystemRowsTest {
    /**
     * Two rows two apart, both byte counts, one spelling. They had two: the
     * Held row said "500.0 MB" where the Upstream row two lines below said
     * "500 MB" for the same quantity.
     */
    @Test
    fun bothByteFiguresOnTheScreenAreSpelledTheSameWay() {
        val state = facts(heldBytes = 524_288_000, fromUpstreamBytes = 524_288_000)

        assertEquals("500 MB", cacheRows(state).toMap()["Held"]?.substringBefore(" of "))
        assertEquals("500 MB", upstreamRows(state).toMap()["Since starting"])
    }

    @Test
    fun theCacheBlockNamesTheVolumeInUse() {
        val state = facts(volumeLabel = "SD card")

        assertEquals("SD card", cacheRows(state).toMap()["Where"])
    }

    @Test
    fun aFellBackCacheSaysSoNextToTheVolumeItLandedOn() {
        val state = facts(volumeLabel = "Internal storage", fellBack = true)

        assertEquals("Internal storage (the chosen volume could not be used)", cacheRows(state).toMap()["Where"])
    }

    @Test
    fun anEmptyCacheStillSaysWhatItMayHold() {
        assertEquals("nothing yet of 2.0 GB", heldOfBudget(held = 0, budget = 2_147_483_648))
    }

    @Test
    fun readsAreAShareAndTheRoundTripsBehindIt() {
        assertEquals(
            "81% from disk (34 fetches upstream)",
            cacheReadsLine(fromCache = 81, fromUpstream = 19, fetches = 34),
        )
    }

    /** "1 fetches upstream" is the kind of thing that makes a careful app look careless. */
    @Test
    fun aSingleRoundTripIsSaidInTheSingular() {
        assertEquals("50% from disk (1 fetch upstream)", cacheReadsLine(fromCache = 10, fromUpstream = 10, fetches = 1))
    }

    /** Before anything has played there is no share to take. */
    @Test
    fun nothingReadYetIsSaidPlainly() {
        assertEquals("nothing read yet", cacheReadsLine(fromCache = 0, fromUpstream = 0, fetches = 0))
    }

    /**
     * What the screen hands over, not only what the sentence does with it.
     * These rows called Telegram round trips "hits" and reads that raised
     * "misses", and said the second of those twice on one screen under two
     * names — all of it invisible to a test that only called the sentence
     * with numbers named after what it wanted them to be.
     */
    @Test
    fun theCacheBlockCountsRoundTripsRatherThanCacheHits() {
        val rows = facts(fromCacheBytes = 81, fromUpstreamBytes = 19, fetches = 34, failedReads = 8)

        assertEquals("81% from disk (34 fetches upstream)", cacheRows(rows).toMap()["Reads"])
    }

    /** The reads that raised have one name and one row, in the block that owns them. */
    @Test
    fun failedReadsAreSaidOnceAndOnlyUpstream() {
        val state = facts(fromCacheBytes = 81, fromUpstreamBytes = 19, fetches = 34, failedReads = 8)

        assertEquals("8", upstreamRows(state).toMap()["Failed reads"])
    }

    /** An absent fact is an omitted row, not an empty one. */
    @Test
    fun aValueNobodyKnowsHasNoRow() {
        assertNull(telegramLine(connected = null))
        assertEquals("connected", telegramLine(connected = true))
        assertEquals("disconnected", telegramLine(connected = false))
    }

    @Test
    fun uptimeIsSaidCoarsely() {
        assertEquals("14m", uptimeLine(seconds = 840))
        assertEquals("2h 14m", uptimeLine(seconds = 8_040))
    }

    /** A process with no start time to measure from has no row either. */
    @Test
    fun anUnknownUptimeHasNoRow() {
        assertNull(uptimeLine(seconds = null))
    }

    /** Only the counters under test vary; the rest are whatever a working install would report. */
    private fun facts(
        heldBytes: Long = 0,
        budgetBytes: Long = 2_147_483_648,
        volumeLabel: String = "Internal storage",
        fellBack: Boolean = false,
        fromCacheBytes: Long = 0,
        fromUpstreamBytes: Long = 0,
        fetches: Int = 0,
        failedReads: Int = 0,
    ) = SystemUiState(
        origin = "channel",
        sets = 540,
        posters = 0,
        schema = 1,
        publishedAt = null,
        lastRefresh = null,
        heldBytes = heldBytes,
        budgetBytes = budgetBytes,
        volumeLabel = volumeLabel,
        fellBack = fellBack,
        fromCacheBytes = fromCacheBytes,
        fromUpstreamBytes = fromUpstreamBytes,
        fetches = fetches,
        failedReads = failedReads,
        connected = true,
        versionName = "0.4.0",
        uptimeSeconds = 0,
    )

    /**
     * The last read alone hides what the server carried: read-ahead past
     * what it holds ends every session on a Telegram fetch, so a title
     * served almost entirely from the LAN still read "Telegram".
     */
    @Test
    fun theSourceRowCountsWhatTheHomeServerServed() {
        assertEquals("Telegram; 77 chunks from the home server", sourceLine(false, null, lanHits = 77))
        assertEquals("LAN (nas); 1 chunk from the home server", sourceLine(true, "nas", lanHits = 1))
        assertEquals("Telegram", sourceLine(false, null, lanHits = 0))
        assertNull(sourceLine(null, null, lanHits = 0))
    }
}
