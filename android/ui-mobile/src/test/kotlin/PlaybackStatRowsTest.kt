package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import playback.PlaybackTotals

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

    @Test
    fun bufferIsTimeAheadAndBytesHeld() {
        assertEquals("1:23 ahead · 47 MB", bufferStatLine(aheadMs = 83_000, heldBytes = 49_283_072))
    }

    @Test
    fun cacheIsTheShareServedFromDisk() {
        assertEquals("81% from disk", cacheStatLine(PlaybackTotals(fromCacheBytes = 810, fromUpstreamBytes = 190, fetches = 4, failedReads = 0)))
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
}
