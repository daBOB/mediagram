package player

import playback.PlaybackTotals
import playback.TrafficSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the overlay says, from what the player reports. These read the
 * decoder rather than the catalog, so a title whose index is wrong about its
 * codec shows the truth here.
 */
class PlaybackStatRowsTest {
    @Test
    fun videoIsSizeCodecAndRate() {
        assertEquals("1920×800 HEVC 9.4 Mbps", videoStatLine(width = 1920, height = 800, codec = "video/hevc", bitrate = 9_400_000))
    }

    /** A format the player has not resolved yet is said plainly. */
    @Test
    fun aFormatNotYetKnownSaysSo() {
        assertEquals("not yet known", videoStatLine(width = null, height = null, codec = null, bitrate = null))
    }

    /** media3 reports an unset bitrate as -1, which is not a rate. */
    @Test
    fun anUnsetBitrateIsOmittedRatherThanPrintedAsMinusOne() {
        assertEquals("1920×800 HEVC", videoStatLine(width = 1920, height = 800, codec = "video/hevc", bitrate = -1))
    }

    /** A MIME name that is not the codec's own name goes through a small map rather than the fallback. */
    @Test
    fun aCodecWhoseMimeNameIsNotItsOwnNameGoesThroughTheMap() {
        assertEquals("1920×800 VP9", videoStatLine(width = 1920, height = 800, codec = "video/x-vnd.on2.vp9", bitrate = null))
    }

    /**
     * Most MIME names are already the codec's own name once stripped and
     * upper-cased, so only the exceptions need a map entry — a table of
     * every name media3 can emit is work nobody asked for.
     */
    @Test
    fun aCodecAbsentFromTheMapGoesThroughTheFallbackUnchanged() {
        assertEquals("1920×800 AVC", videoStatLine(width = 1920, height = 800, codec = "video/avc", bitrate = null))
    }

    @Test
    fun audioNamesItsChannelsAndLanguage() {
        assertEquals("EAC3 5.1 German", audioStatLine(codec = "audio/eac3", channels = 6, language = "de"))
    }

    /** Stereo is worth saying; an unknown channel count is not worth guessing. */
    @Test
    fun audioWithoutChannelsOmitsThem() {
        assertEquals("AAC English", audioStatLine(codec = "audio/mp4a-latm", channels = 0, language = "en"))
    }

    /**
     * A duration alone. ExoPlayer reports how far ahead it has loaded and no
     * count of the bytes that came to, so there is no second half to print.
     */
    @Test
    fun bufferIsTimeAhead() {
        assertEquals("1:23 ahead", bufferStatLine(aheadMs = 83_000))
    }

    /** Nothing loaded past the playhead is a real reading, not a missing one. */
    @Test
    fun anEmptyBufferSaysNoTimeAhead() {
        assertEquals("0:00 ahead", bufferStatLine(aheadMs = 0))
    }

    @Test
    fun cacheIsTheShareServedFromDisk() {
        assertEquals(
            "81% from disk",
            cacheStatLine(PlaybackTotals(fromCacheBytes = 810, fromUpstreamBytes = 190, fetches = 4, failedReads = 0)),
        )
    }

    /** Before anything has been read there is no share to take. */
    @Test
    fun anUntouchedCacheSaysNothingReadYet() {
        assertEquals("nothing read yet", cacheStatLine(PlaybackTotals(0, 0, 0, 0)))
    }

    @Test
    fun readsAreTheCountAndWhatTheyCarried() {
        assertEquals("34 fetches · 34 MB", readsStatLine(PlaybackTotals(0, 35_651_584, 34, 0)))
    }

    /** A failure is only mentioned when there has been one. */
    @Test
    fun failedReadsAreNamedOnlyWhenTheyHappened() {
        assertEquals("34 fetches · 34 MB · 2 failed", readsStatLine(PlaybackTotals(0, 35_651_584, 34, 2)))
    }

    /** Zero dropped frames is the ordinary case, and saying so is noise. */
    @Test
    fun noDroppedFramesIsNoRow() {
        assertNull(droppedStatLine(0))
        assertEquals("12 frames", droppedStatLine(12))
    }

    @Test
    fun theNetworkRowIsTheRateAndWhatIsInFlight() {
        assertEquals("14 Mbps · 2 in flight", networkStatLine(TrafficSnapshot(foregroundBitsPerSecond = 14_200_000, foregroundInFlight = 2)))
        assertEquals("9.4 Mbps", networkStatLine(TrafficSnapshot(foregroundBitsPerSecond = 9_400_000)))
    }

    /** A full buffer with nothing to fetch is the healthy case, and says so in a word. */
    @Test
    fun nothingArrivingAndNothingInFlightIsIdle() {
        assertEquals("idle", networkStatLine(TrafficSnapshot()))
    }

    @Test
    fun onDiskIsHeldAgainstTheWholeRoundedDown() {
        assertEquals("512 MB of 1.0 GB · 50%", onDiskStatLine(heldBytes = 512L shl 20, totalBytes = 1L shl 30))
        assertEquals("99%", onDiskStatLine(heldBytes = (1L shl 30) - 1, totalBytes = 1L shl 30)?.substringAfterLast(" "))
        assertNull(onDiskStatLine(heldBytes = 0, totalBytes = 0))
    }

    @Test
    fun theNextRowSaysWhenThePreloadIsWaitingForPlayback() {
        val next = NextDownload(title = "Der Sexmarathon", heldBytes = 34, totalBytes = 100)
        assertEquals(
            "Der Sexmarathon · 34% · waiting for playback",
            nextStatLine(next, TrafficSnapshot(backgroundWaiting = true, backgroundBitsPerSecond = 3_000_000)),
        )
        assertEquals("Der Sexmarathon · 34% · 12 Mbps", nextStatLine(next, TrafficSnapshot(backgroundBitsPerSecond = 11_600_000)))
        assertEquals("Der Sexmarathon · 34%", nextStatLine(next, TrafficSnapshot()))
        assertNull(nextStatLine(null, TrafficSnapshot()))
    }
}
