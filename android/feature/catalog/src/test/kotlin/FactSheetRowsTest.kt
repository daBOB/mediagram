package catalog

import model.Kind
import model.MediaSet
import uniffi.mediagram_core.TitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fact sheets both Android surfaces draw on a title page, pinned to the
 * rows `film-page.js` and `series-page.js` print — label, order, and the
 * rows a missing value leaves out.
 */
class FactSheetRowsTest {
    private val film =
        MediaSet(
            setId = "f", kind = Kind.MOVIE, title = "A Film", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2004, durationSecs = 6780,
            posterPath = null, totalBytes = 15_246_565_376, container = "mkv", vcodec = "hevc", acodec = "eac3",
            quality = "1080p", hdr = "HDR10", partCount = 5, fsk = "12", genres = listOf("Drama", "Comedy"),
            alang = listOf("en", "de"), slang = listOf("de"),
        )

    private fun info(rating: Double? = null) =
        TitleInfo(overview = null, tagline = null, genres = null, rating = rating, network = "HBO", status = "Ended")

    @Test
    fun aFilmsOverviewNamesItsFactsThenItsGenresThenItsFranchise() {
        val franchise = Franchise(id = 9, name = "The Saga", films = emptyList(), art = null)
        assertEquals(
            listOf(
                "Released" to FactValue.Words("2004"),
                "Runtime" to FactValue.Words("1h 53m"),
                "Rated" to FactValue.Words("FSK 12"),
                "Score" to FactValue.Words("★ 7.5"),
                "Genres" to FactValue.Genres(listOf("Drama", "Comedy")),
                "Part of" to FactValue.PartOf(franchise),
            ),
            filmOverviewFacts(film, info(rating = 7.5), franchise),
        )
    }

    @Test
    fun anOverviewLeavesOutWhatIsNotKnown() {
        val bare = film.copy(year = 0, durationSecs = null, fsk = null, genres = emptyList())
        assertEquals(emptyList(), filmOverviewFacts(bare, null, null))
    }

    @Test
    fun aFilmsDetailsAreTheFileAsTheWebPrintsIt() {
        assertEquals(
            listOf(
                "Quality" to "1080p · HDR10",
                "Video" to "hevc",
                "Audio" to "eac3",
                "Audio languages" to "English, German",
                "Subtitles" to "German",
                "Container" to "mkv",
                "Size" to "14 GB",
                "Bitrate" to "18 Mbps",
                "Parts" to "5",
            ),
            filmDetailFacts(film).map { (label, value) -> label to (value as FactValue.Words).text },
        )
    }

    @Test
    fun detailsLeaveOutAnEmptyContainerAndASinglePart() {
        val plain = film.copy(container = "", partCount = 1, hdr = "SDR", alang = emptyList(), slang = emptyList())
        val labels = filmDetailFacts(plain).map { it.first }
        assertEquals(listOf("Quality", "Video", "Audio", "Size", "Bitrate"), labels)
    }

    @Test
    fun aShowsAboutRunsAiredHeldFromGenresPictureThenLanguages() {
        val episode = film.copy(setId = "e1", kind = Kind.EPISODE, show = "Show", season = 1, episodeFirst = 1, year = 2011)
        val facts = summarize(listOf(Division("Season 1", 1, listOf(episode), emptyList())))
        val provider =
            TitleInfo(
                overview = null, tagline = null, genres = null, rating = 8.1, network = "HBO", status = "Ended",
                firstAir = "2011-04-17", lastAir = "2019-05-19", totalSeasons = 8u, totalEpisodes = 73u,
            )
        val rows = seriesAboutFacts(facts, provider, listOf("Drama"))

        assertEquals(listOf("Aired", "Held", "From", "Genres", "Picture", "Audio", "Subtitles"), rows.map { it.first })
        assertEquals(FactValue.Words("2011–2019"), rows[0].second)
        assertEquals(FactValue.Words("1 of 73 episodes · 1 of 8 seasons · 1h 53m · 14 GB"), rows[1].second)
        assertEquals(FactValue.Words("★ 8.1 · HBO · Ended"), rows[2].second)
        assertEquals(FactValue.Genres(listOf("Drama")), rows[3].second)
        assertEquals(FactValue.Words("English, German"), rows[5].second)
    }
}
