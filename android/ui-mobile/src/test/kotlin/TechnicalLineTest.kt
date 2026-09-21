package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import model.Kind
import model.MediaSet

/**
 * The same line the web player prints, in the same order. A viewer who reads
 * both surfaces should not have to learn two ways of describing one file.
 * `format.js` is the reference.
 */
class TechnicalLineTest {

    @Test
    fun aFilmIsDescribedInTheOrderTheWebPlayerUses() {
        // humanSize(15_246_565_376) rounds to whole GB once the scaled value
        // is no longer under ten, per the threshold already in `humanSize`
        // (and in `format.js`'s own copy of it) — so this reads "14 GB", not
        // "14.2 GB".
        assertEquals(
            "1080p · HDR10 · mkv · hevc · eac3 · 14 GB · 5 parts · 9.4 Mbps",
            technicalLine(
                setFixture(
                    quality = "1080p", hdr = "HDR10", container = "mkv",
                    vcodec = "hevc", acodec = "eac3",
                    total = 15_246_565_376, duration = 12_980, partCount = 5,
                ),
            ),
        )
    }

    /**
     * SDR is the absence of a fact rather than a fact, and a shelf where
     * every card says SDR says nothing at all.
     */
    @Test
    fun anSdrTitleSaysNothingAboutItsDynamicRange() {
        assertNull(hdrLabel("SDR"))
        assertNull(hdrLabel(null))
        assertEquals("HDR10", hdrLabel("HDR10"))
    }

    /** One part is the ordinary case; saying so is noise. */
    @Test
    fun aSinglePartSetDoesNotMentionItsParts() {
        assertEquals(
            "mp4 · 954 MB · 2.2 Mbps",
            technicalLine(setFixture(container = "mp4", total = 1_000_000_000, duration = 3_600, partCount = 1)),
        )
    }

    /** Under ten the first decimal is the difference between links that carry it. */
    @Test
    fun aBitrateIsPreciseWhereItMatters() {
        assertEquals("9.4 Mbps", bitrateLabel(totalBytes = 15_246_565_376, durationSeconds = 12_980))
        assertEquals("24 Mbps", bitrateLabel(totalBytes = 15_246_565_376, durationSeconds = 5_082))
    }

    /** A percentage of an unknown length is a number with nothing behind it. */
    @Test
    fun aBitrateWithNoRuntimeIsNotGuessed() {
        assertNull(bitrateLabel(totalBytes = 15_246_565_376, durationSeconds = null))
        assertNull(bitrateLabel(totalBytes = 0, durationSeconds = 3_600))
    }

    /**
     * Container and codecs are printed as the index stored them. A surface
     * that wants the shout-case form upper-cases at render time; this line
     * does not, so the web and Android read the same file the same way.
     */
    @Test
    fun containerAndCodecsArePrintedAsStored() {
        val line = technicalLine(setFixture(container = "mkv", vcodec = "hevc", acodec = "eac3", total = 1, duration = 1))

        assert(line.contains("mkv · hevc · eac3"))
    }

    /** A field the index never recorded is left out, not printed empty. */
    @Test
    fun anUnknownFieldIsOmittedRatherThanBlank() {
        assertEquals(
            "mkv · 1 B · 0.0 Mbps",
            technicalLine(setFixture(container = "mkv", vcodec = null, acodec = null, total = 1, duration = 1)),
        )
    }
}

private fun setFixture(
    quality: String? = null,
    hdr: String? = null,
    container: String = "mkv",
    vcodec: String? = null,
    acodec: String? = null,
    total: Long = 0,
    duration: Int? = null,
    partCount: Int = 1,
): MediaSet = MediaSet(
    setId = "set-1",
    kind = Kind.MOVIE,
    title = "A Film",
    show = null,
    chapter = null,
    path = null,
    season = null,
    episodeFirst = null,
    episodeLast = null,
    year = null,
    durationSecs = duration,
    posterPath = null,
    totalBytes = total,
    container = container,
    vcodec = vcodec,
    acodec = acodec,
    quality = quality,
    hdr = hdr,
    partCount = partCount,
    posterKey = null,
)
