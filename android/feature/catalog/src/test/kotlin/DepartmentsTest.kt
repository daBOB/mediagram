package catalog

import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepartmentsTest {
    private fun film(
        setId: String,
        popularity: Double? = null,
        rating: Double? = null,
        backdropPath: String? = null,
        addedAt: Long = 0,
        durationSecs: Int? = null,
        kind: Kind = Kind.MOVIE,
        category: String? = null,
    ): MediaSet =
        MediaSet(
            setId = setId, kind = kind, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = durationSecs,
            posterPath = null, totalBytes = 0, popularity = popularity, rating = rating, backdropPath = backdropPath,
            addedAt = addedAt, category = category,
        )

    @Test
    fun anEmptyShelfHasNoDepartment() {
        assertNull(moviesDepartmentOf(emptyList(), watched = { false }))
    }

    @Test
    fun theLeadIsTheMostPopularUnwatchedFilmWithABackdropAndItIsExcludedFromFeatured() {
        val dept = moviesDepartmentOf(
            listOf(
                film("watched-favorite", popularity = 99.0, backdropPath = "wf-bg"),
                film("no-art", popularity = 90.0),
                film("lead", popularity = 50.0, backdropPath = "lead-bg"),
                film("second", popularity = 10.0, backdropPath = "second-bg"),
            ),
            watched = { it == "watched-favorite" },
        )!!
        assertEquals("lead", dept.lead?.setId)
        assertEquals(listOf("no-art", "second"), dept.featured.map(MediaSet::setId))
    }

    @Test
    fun acclaimedIsUnwatchedAndAtOrAboveSevenPointFive() {
        val dept = moviesDepartmentOf(
            listOf(film("great", rating = 8.0), film("fine", rating = 7.0), film("seen", rating = 9.0)),
            watched = { it == "seen" },
        )!!
        assertEquals(listOf("great"), dept.acclaimed.map(MediaSet::setId))
    }

    @Test
    fun recentlyAddedIsNewestFirstAcrossEveryFilmRegardlessOfWatched() {
        val dept = moviesDepartmentOf(
            listOf(film("old", addedAt = 1), film("new", addedAt = 2)),
            watched = { it == "new" },
        )!!
        assertEquals(listOf("new", "old"), dept.recentlyAdded.map(MediaSet::setId))
    }

    @Test
    fun hoursSumsEveryFilmsDurationRegardlessOfWatched() {
        val dept = moviesDepartmentOf(listOf(film("a", durationSecs = 3600), film("b", durationSecs = 7200)), watched = { false })!!
        assertEquals(3, dept.hours)
    }

    /** Rounded like the web player's department hero, not truncated: 2h 40m of film is "3 hours". */
    @Test
    fun hoursRoundToTheNearestHourAsTheWebPlayerDoes() {
        val dept = moviesDepartmentOf(listOf(film("a", durationSecs = 9_600)), watched = { false })!!
        assertEquals(3, dept.hours)
    }

    private fun show(
        name: String,
        popularity: Double? = null,
        backdropPath: String? = null,
        addedAt: Long = 0,
        category: String? = null,
    ): Entry.Collection {
        val first = MediaSet(
            setId = "$name-e1", kind = Kind.EPISODE, title = "E1", show = name, chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, popularity = popularity, backdropPath = backdropPath, addedAt = addedAt,
            category = category,
        )
        return Entry.Collection(
            key = "series/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division(name, 1, listOf(first), emptyList())),
        )
    }

    @Test
    fun anEmptyShowShelfHasNoDepartment() {
        assertNull(showsDepartmentOf(Kind.EPISODE, emptyList(), emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun belowTheRowSizePopularAndNewEpisodesAreEmptyButAllListsEveryShow() {
        val shows = listOf(show("Show A"), show("Show B"))
        val dept = showsDepartmentOf(Kind.EPISODE, shows, emptyMap(), WatchSnapshot.Empty)!!
        assertTrue(dept.popular.isEmpty())
        assertTrue(dept.newEpisodes.isEmpty())
        assertEquals(2, dept.all.size)
    }

    @Test
    fun aboveTheRowSizePopularAndNewEpisodesRank() {
        val shows = (1..13).map { show("Show $it", popularity = it.toDouble(), addedAt = it.toLong()) }
        val dept = showsDepartmentOf(Kind.EPISODE, shows, emptyMap(), WatchSnapshot.Empty)!!
        assertEquals("Show 13", dept.popular.first().name)
        assertEquals("Show 13", dept.newEpisodes.first().name)
    }

    /** `renderShowsDept`'s gate is `series && shows.length > ROW`: courses never get rows, however many there are. */
    @Test
    fun aCourseLibraryAboveTheRowSizeStillGetsNoPopularOrNewRows() {
        val courses = (1..13).map { show("Course $it", popularity = it.toDouble(), addedAt = it.toLong()) }
        val dept = showsDepartmentOf(Kind.TUTORIAL, courses, emptyMap(), WatchSnapshot.Empty)!!
        assertTrue(dept.popular.isEmpty())
        assertTrue(dept.newEpisodes.isEmpty())
        assertEquals(13, dept.all.size)
    }

    @Test
    fun theLeadIsTheMostPopularShowWithABackdrop() {
        val shows = listOf(show("No Art", popularity = 99.0), show("Lead", popularity = 10.0, backdropPath = "lead-bg"))
        val dept = showsDepartmentOf(Kind.EPISODE, shows, emptyMap(), WatchSnapshot.Empty)!!
        assertEquals("Lead", dept.lead?.name)
    }

    /**
     * The general Continue list resolves any started title by [Kind] alone
     * — an anime episode is still `Kind.EPISODE` — so the Series department
     * has to drop `anime: true` sets itself rather than leave them to leak
     * in from a viewer's own watch snapshot. Anime left this shelf entirely
     * at `shelvesOf`, and offers the same title under its own Continue
     * watching instead.
     */
    @Test
    fun theSeriesDepartmentsContinueRowDropsAnAnimeEpisodeEvenThoughItSharesTheKind() {
        val plainEpisode = MediaSet(
            setId = "plain-ep", kind = Kind.EPISODE, title = "Plain", show = "Plain Show", chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = 1200,
            posterPath = null, totalBytes = 0, anime = false,
        )
        val animeEpisode = MediaSet(
            setId = "anime-ep", kind = Kind.EPISODE, title = "Anime", show = "Anime Show", chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = 1200,
            posterPath = null, totalBytes = 0, anime = true,
        )
        val byId = mapOf(plainEpisode.setId to plainEpisode, animeEpisode.setId to animeEpisode)
        val watch = WatchSnapshot.Empty.copy(
            progress = listOf(
                Progress(plainEpisode.setId, at = 60.0, duration = 1200.0, updatedAt = 1),
                Progress(animeEpisode.setId, at = 60.0, duration = 1200.0, updatedAt = 2),
            ),
        )

        val dept = showsDepartmentOf(Kind.EPISODE, listOf(show("Plain Show")), byId, watch)!!

        assertEquals(listOf(plainEpisode.setId), dept.underway.continues.map { it.setId })
    }

    @Test
    fun aFiledCourseGetsItsOwnRowAndAnUnfiledOneFallsToOther() {
        val courses = listOf(show("Forex", category = "Trading"), show("Geld"))
        val dept = showsDepartmentOf(Kind.TUTORIAL, courses, emptyMap(), WatchSnapshot.Empty)!!

        assertEquals(listOf("Trading" to listOf("Forex"), "Other" to listOf("Geld")), dept.categories.map { it.title to it.units.map(Entry.Collection::name) })
    }

    @Test
    fun noCourseFiledMeansNoCategoryRowsAtAll() {
        val dept = showsDepartmentOf(Kind.TUTORIAL, listOf(show("Geld")), emptyMap(), WatchSnapshot.Empty)!!
        assertTrue(dept.categories.isEmpty())
    }

    /** A category is only ever set on a course or a documentary unit — a show never carries one. */
    @Test
    fun theSeriesDepartmentNeverGetsCategoryRows() {
        val dept = showsDepartmentOf(Kind.EPISODE, listOf(show("Breaking Bad")), emptyMap(), WatchSnapshot.Empty)!!
        assertTrue(dept.categories.isEmpty())
    }

    private fun collectionOf(name: String, category: String?, key: String = "DOCUMENTARY/$name"): Entry.Collection {
        val lead = film("$name-ep1", category = category, kind = Kind.DOCUMENTARY)
        return Entry.Collection(
            key = key, kind = CollectionKind.COURSE, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division(name, null, listOf(lead), emptyList())),
        )
    }

    @Test
    fun documentariesCategoryRowsHoldCollectionsBeforeSinglesInDepartmentOrder() {
        val collection = collectionOf("Terra X", category = "Science")
        val single = film("solo", category = "Science", kind = Kind.DOCUMENTARY)
        val library = DocumentaryLibrary(collections = listOf(collection), singles = listOf(single))

        val dept = documentariesDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!

        val row = dept.categories.single { it.title == "Science" }
        assertEquals(listOf(collection.key, single.setId), row.units.map(::keyOf))
    }

    @Test
    fun documentariesWithNothingCategorisedHasNoRows() {
        val library = DocumentaryLibrary(collections = listOf(collectionOf("Terra X", category = null)), singles = listOf(film("solo", kind = Kind.DOCUMENTARY)))
        val dept = documentariesDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!
        assertTrue(dept.categories.isEmpty())
    }
}
