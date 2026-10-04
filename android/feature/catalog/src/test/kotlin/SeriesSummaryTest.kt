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
        slang: List<String> = emptyList(),
        alang: List<String> = emptyList(),
    ): MediaSet =
        MediaSet(
            setId = "s${season}e$episode", kind = Kind.EPISODE, title = "Ep $season.$episode", show = "Show",
            chapter = null, path = null, season = season, episodeFirst = episode, episodeLast = null,
            year = year, durationSecs = durationSecs, posterPath = null, totalBytes = totalBytes,
            quality = quality, hdr = hdr, vcodec = vcodec, acodec = acodec, slang = slang, alang = alang,
        )

    /** What the provider says about the show as a whole — only the parts these lines compare against. */
    private fun provider(
        firstAir: String? = null,
        lastAir: String? = null,
        totalSeasons: UInt? = null,
        totalEpisodes: UInt? = null,
    ) = TitleInfo(
        overview = null, tagline = null, genres = null, rating = null, network = null, status = null,
        firstAir = firstAir, lastAir = lastAir, totalSeasons = totalSeasons, totalEpisodes = totalEpisodes,
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

    /** Spelled, as `series-summary.js` spells its own `countOf`. */
    @Test
    fun scaleLineNeverClaimsATotalTheProviderNeverNamed() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, durationSecs = 60, totalBytes = 10_485_760)))))
        assertEquals("one episode · one season · 1m · 10 MB", scaleLine(facts))
    }

    /** Figures once there is a comparison: "8 of 16" is arithmetic, not prose. */
    @Test
    fun scaleLineCountsAgainstWhatTheProviderSaysExists() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1), ep(1, 2)))))
        assertEquals("2 of 16 episodes · 1 of 2 seasons", scaleLine(facts, provider(totalSeasons = 2u, totalEpisodes = 16u)))
    }

    /** "16 of 16" says nothing a viewer needs; holding all of it reads as holding it. */
    @Test
    fun scaleLineHoldingEverythingNamesNoTotal() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1), ep(1, 2)))))
        assertEquals("two episodes · one season", scaleLine(facts, provider(totalSeasons = 1u, totalEpisodes = 2u)))
    }

    @Test
    fun yearLineRangesTheYearsHeldAndIsNullWithNone() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, year = 2001), ep(1, 2, year = 2004)))))
        assertEquals("2001–2004", yearLine(facts))
        assertNull(yearLine(summarize(listOf(division(1, listOf(ep(1, 1)))))))
    }

    /** One season held of a show that ran eight still says when the show ran. */
    @Test
    fun yearLinePrefersTheProvidersAirDatesToTheYearsHeld() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, year = 2011)))))
        assertEquals("2011–2019", yearLine(facts, provider(firstAir = "2011-04-17", lastAir = "2019-05-19")))
        // Still running: no last date, and no dash to nowhere.
        assertEquals("2011", yearLine(facts, provider(firstAir = "2011-04-17")))
        assertEquals("2011", yearLine(facts, provider(firstAir = "2011-04-17", lastAir = "2011-06-19")))
        // A date that is not one falls back to what the files say.
        assertEquals("2011", yearLine(summarize(listOf(division(1, listOf(ep(1, 1, year = 2011))))), provider(firstAir = "")))
    }

    @Test
    fun seriesFactsLineSpellsItsSeasonsAndNamesThreeGenres() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, year = 1987))), division(2, listOf(ep(2, 1, year = 1994)))))
        assertEquals("1987–1994 · two seasons · Sci-Fi, Drama, Mystery", seriesFactsLine(facts, null, listOf("Sci-Fi", "Drama", "Mystery", "War")))
        assertEquals(
            "2011–2019 · two seasons",
            seriesFactsLine(facts, provider(firstAir = "2011-04-17", lastAir = "2019-05-19"), emptyList()),
        )
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
    fun detailRowsNameAudioThenSubtitleLanguagesAcrossEveryEpisode() {
        val facts =
            summarize(
                listOf(division(1, listOf(ep(1, 1, alang = listOf("ja"), slang = listOf("en", "de")), ep(1, 2, alang = listOf("ja", "en"))))),
            )
        assertEquals(listOf("Audio" to "Japanese, English", "Subtitles" to "English, German"), detailRows(facts))
    }

    @Test
    fun detailRowsLeaveOutALanguageKindNobodyRecorded() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1, slang = listOf("en", "de"))))))
        assertEquals(listOf("Subtitles" to "English, German"), detailRows(facts))
    }

    @Test
    fun detailRowsIsEmptyWithNoSubtitleTrack() {
        val facts = summarize(listOf(division(1, listOf(ep(1, 1)))))
        assertEquals(emptyList(), detailRows(facts))
    }
}
