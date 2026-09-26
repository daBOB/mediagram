package catalog

import model.Kind
import model.MediaSet
import uniffi.mediagram_core.TitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeriesSummaryTest {
    private fun ep(
        season: Int,
        episode: Int,
        year: Int? = null,
        durationSecs: Int? = null,
        totalBytes: Long = 0,
        quality: String? = null,
        hdr: String? = null,
        vcodec: String? = null,
        acodec: String? = null,
        subtitleLanguages: List<String> = emptyList(),
    ): MediaSet =
        MediaSet(
            setId = "s${season}e$episode", kind = Kind.EPISODE, title = "Ep $season.$episode", show = "Show",
            chapter = null, path = null, season = season, episodeFirst = episode, episodeLast = null,
            year = year, durationSecs = durationSecs, posterPath = null, totalBytes = totalBytes,
            quality = quality, hdr = hdr, vcodec = vcodec, acodec = acodec, subtitleLanguages = subtitleLanguages,
        )

    private fun division(
        season: Int,
        items: List<MediaSet>,
    ) = Division("Season $season", season, items, emptyList())

    @Test
    fun summarizeCountsEpisodesSeasonsRuntimeAndSizeAcrossEveryDivision() {
        val facts =
            summarize(
                listOf(
                    division(1, listOf(ep(1, 1, durationSecs = 1200, totalBytes = 100), ep(1, 2, durationSecs = 1800, totalBytes = 200))),
                    division(2, listOf(ep(2, 1, durationSecs = 1500, totalBytes = 300))),
                ),
            )
        assertEquals(3, facts.episodes)
        assertEquals(2, facts.seasons)
        assertEquals(4500, facts.runtimeSecs)
        assertEquals(600L, facts.totalBytes)
    }

    @Test
    fun qualitiesSortSmallestFirstAndSdrIsLeftOutOfHdr() {
        val facts =
            summarize(
                listOf(
                    division(
                        1,
                        listOf(
                            ep(1, 1, quality = "1080p", hdr = "SDR"),
                            ep(1, 2, quality = "720p", hdr = "HDR10"),
                        ),
                    ),
                ),
            )
        assertEquals(listOf("720p", "1080p"), facts.qualities)
        assertEquals(listOf("HDR10"), facts.hdr)
        assertEquals("720p–1080p · HDR10", pictureLine(facts))
    }

    @Test
    fun scaleLineNeverClaimsATotalTheProviderNeverNamed() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, durationSecs = 60, totalBytes = 10_485_760)))))
        assertEquals("1 episode · 1 season · 1m · 10 MB", scaleLine(facts))
    }

    @Test
    fun yearLineRangesTheYearsHeldAndIsNullWithNone() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, year = 2001), ep(1, 2, year = 2004)))))
        assertEquals("2001–2004", yearLine(facts))
        assertNull(yearLine(summarize(listOf(division(1, listOf(ep(1, 1)))))))
    }

    @Test
    fun provenanceJoinsRatingNetworkAndStatusButNeverGenres() {
        val info = TitleInfo(overview = null, tagline = null, genres = "Drama, Crime", rating = 8.4, network = "HBO", status = "Ended")
        assertEquals("★ 8.4 · HBO · Ended", provenance(info))
    }

    @Test
    fun provenanceIsNullWithNoProviderEntry() {
        assertNull(provenance(null))
    }

    @Test
    fun detailRowsOnlyEverHasSubtitlesNotAudioLanguages() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, subtitleLanguages = listOf("en", "de"))))))
        assertEquals(listOf("Subtitles" to "English, German"), detailRows(facts))
    }

    @Test
    fun detailRowsIsEmptyWithNoSubtitleTrack() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1)))))
        assertEquals(emptyList(), detailRows(facts))
    }
}
