package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * A shelf is what a viewer scans to find something. The whole point of
 * grouping is that scanning "Series" means reading show names — not a
 * hundred and eighty episode titles under a hundred and eighty copies of
 * the same poster, which is what a flat list of sets gives.
 */
class ShelvesTest {

    @Test
    fun aFilmIsItsOwnCard() {
        val shelves = shelvesOf(listOf(film("Alien")))

        val entry = shelves.single { it.title == "Movies" }.entries.single()
        assertEquals("Alien", assertIs<Entry.Film>(entry).set.title)
    }

    /**
     * The reported bug: every episode was a card of its own, so "Series"
     * read as a list of films nobody recognised.
     */
    @Test
    fun aShowsEpisodesBecomeOneCardForTheShow() {
        val shelves = shelvesOf(
            listOf(
                episode("30 Rock", season = 7, episode = 1, title = "Ein Gouverneur zum Totlachen"),
                episode("30 Rock", season = 7, episode = 2, title = "Frauen sind witzig!"),
                episode("Psych", season = 1, episode = 1, title = "Pilot"),
            ),
        )

        val series = shelves.single { it.title == "Series" }.entries
        assertEquals(listOf("30 Rock", "Psych"), series.map { (it as Entry.Collection).name })
        assertEquals(2, (series.first() as Entry.Collection).count)
    }

    @Test
    fun aShowsSeasonsAreItsDivisions() {
        val shelves = shelvesOf(
            listOf(
                episode("30 Rock", season = 7, episode = 1, title = "One"),
                episode("30 Rock", season = 1, episode = 1, title = "Pilot"),
            ),
        )

        val show = shelves.single { it.title == "Series" }.entries.single() as Entry.Collection
        assertEquals(listOf("Season 1", "Season 7"), show.divisions.map { it.title })
    }

    /** Seasons run 1, 2, … 10, not 1, 10, 2, which is what text order gives. */
    @Test
    fun seasonsRunInNumberOrderNotTextOrder() {
        val seasons = (1..11).map { episode("Show", season = it, episode = 1, title = "E$it") }

        val show = shelvesOf(seasons).single { it.title == "Series" }.entries.single()

        assertEquals(
            (1..11).map { "Season $it" },
            (show as Entry.Collection).divisions.map { it.title },
        )
    }

    @Test
    fun episodesRunInTheOrderTheyWereMeantToBeWatched() {
        val shelves = shelvesOf(
            listOf(
                episode("Show", season = 1, episode = 10, title = "Ten"),
                episode("Show", season = 1, episode = 2, title = "Two"),
            ),
        )

        val show = shelves.single { it.title == "Series" }.entries.single() as Entry.Collection
        assertEquals(listOf("Two", "Ten"), show.divisions.single().items.map { it.title })
    }

    /**
     * The course this was built for runs from one folder deep to three. The
     * shape is kept: flattening it gives sibling headings that each repeat
     * their parents and say nothing about how the course is built.
     */
    @Test
    fun aCoursesFoldersAreKeptAsTheyWereOnDisk() {
        val shelves = shelvesOf(
            listOf(
                lesson("DEI", path = "Ausbildung Trading/1. Grundlagen/1. Trading", title = "Einführung"),
                lesson("DEI", path = "Ausbildung Trading/1. Grundlagen/1. Trading", title = "Definition"),
                lesson("DEI", path = "Basislektionen/1. Start", title = "Begrüßung"),
            ),
        )

        val course = shelves.single { it.title == "Tutorials" }.entries.single() as Entry.Collection
        assertEquals(listOf("Ausbildung Trading", "Basislektionen"), course.divisions.map { it.title })
        val trading = course.divisions.first().children.single()
        assertEquals("1. Grundlagen", trading.title)
        assertEquals(2, trading.children.single().items.size)
    }

    /**
     * A folder of folders is structure, not a chapter. Counting it would
     * tell a viewer the course has more parts than it has.
     */
    @Test
    fun onlyFoldersThatHoldLessonsCountAsChapters() {
        val shelves = shelvesOf(
            listOf(lesson("DEI", path = "Ausbildung/Grundlagen/Trading", title = "Einführung")),
        )

        val course = shelves.single { it.title == "Tutorials" }.entries.single() as Entry.Collection
        assertEquals(1, course.chapters, "three folders deep, one of them holds the lesson")
        assertEquals(1, course.count)
    }

    /** Every episode of a show carries the same artwork; the card needs one. */
    @Test
    fun aCollectionTakesThePosterOfWhatIsInside() {
        val shelves = shelvesOf(
            listOf(episode("30 Rock", season = 1, episode = 1, title = "Pilot", poster = "/art/30rock.jpg")),
        )

        val show = shelves.single { it.title == "Series" }.entries.single() as Entry.Collection
        assertEquals("/art/30rock.jpg", show.posterPath)
    }

    @Test
    fun anEmptyShelfIsOmittedRatherThanRenderedEmpty() {
        assertEquals(listOf("Movies"), shelvesOf(listOf(film("Alien"))).map { it.title })
    }

    /** A key has to survive the process being killed and find its way back. */
    @Test
    fun aCollectionIsFoundAgainByItsKey() {
        val shelves = shelvesOf(listOf(episode("30 Rock", season = 1, episode = 1, title = "Pilot")))
        val state = CatalogUiState.Ready(shelves)
        val key = (shelves.single().entries.single() as Entry.Collection).key

        assertEquals("30 Rock", state.collection(key)?.name)
        assertNull(state.collection("SHOW/Nothing here"), "a stale key names nothing rather than guessing")
    }

    @Test
    fun aShowWithNoNameIsStillReachable() {
        val shelves = shelvesOf(listOf(episode(show = null, season = 1, episode = 1, title = "Orphan")))

        val show = shelves.single().entries.single() as Entry.Collection
        assertEquals("Unknown show", show.name)
    }

    /**
     * A handout sits in the course tree beside the lessons it was uploaded
     * with. It is not a film: on the film shelf it would read as one that
     * will not play.
     */
    @Test
    fun aDocumentSitsInTheCourseThatHoldsIt() {
        val shelves = shelvesOf(
            listOf(
                lesson("Steuerkurs", path = "Grundlagen", title = "Lektion 1"),
                document("Steuerkurs", path = "Grundlagen", title = "Arbeitsbuch"),
            ),
        )

        assertNull(shelves.find { it.title == "Movies" }, "a handout is not a film")
        val course = assertIs<Entry.Collection>(shelves.single { it.title == "Tutorials" }.entries.single())
        assertEquals(2, course.count, "the workbook is counted with the lesson")
    }

    /**
     * A folder holding a workbook and no video at all still appears. When
     * documents were dropped the whole folder went with them.
     */
    @Test
    fun aFolderOfDocumentsAloneSurvives() {
        val shelves = shelvesOf(
            listOf(
                lesson("Steuerkurs", path = "Grundlagen", title = "Lektion 1"),
                document("Steuerkurs", path = "Anhang", title = "Formulare"),
            ),
        )

        val course = assertIs<Entry.Collection>(shelves.single { it.title == "Tutorials" }.entries.single())
        assertEquals(
            listOf("Anhang", "Grundlagen"),
            course.divisions.map { it.title }.sorted(),
            "the folder that holds only a document is still a folder",
        )
    }

}

private fun film(title: String) = set(Kind.MOVIE, title)

private fun episode(
    show: String?,
    season: Int,
    episode: Int,
    title: String,
    poster: String? = null,
) = set(Kind.EPISODE, title, show = show, season = season, episodeFirst = episode, poster = poster)

private fun lesson(course: String, path: String, title: String) =
    set(Kind.TUTORIAL, title, show = course, path = path)

private fun document(course: String, path: String, title: String) =
    set(Kind.DOCUMENT, title, show = course, path = path)

private fun set(
    kind: Kind,
    title: String,
    show: String? = null,
    path: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    poster: String? = null,
) = MediaSet(
    setId = "$kind-$show-$season-$title",
    kind = kind,
    title = title,
    show = show,
    chapter = null,
    path = path,
    season = season,
    episodeFirst = episodeFirst,
    episodeLast = episodeFirst,
    year = null,
    durationSecs = null,
    posterPath = poster,
    totalBytes = 0,
)
