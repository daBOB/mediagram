package player

import model.Kind
import model.Progress
import model.WatchSnapshot
import model.Watched
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [episodeListOf]: the run as the episode sidebar draws it, on the phone and the television alike. */
class EpisodeListTest {

    private fun episode(id: String, season: Int?, number: Int?, durationSecs: Int? = 1_500) =
        fakeMediaSet(id, kind = Kind.EPISODE, title = "Title $id", show = "A Show", season = season, episodeFirst = number, durationSecs = durationSecs)

    private fun lesson(id: String, number: Int?, chapter: String? = null, path: String? = null) =
        fakeMediaSet(id, kind = Kind.TUTORIAL, title = "Lesson $id", show = "A Course", episodeFirst = number, chapter = chapter, path = path)

    private fun watch(watched: List<String> = emptyList(), progress: List<Progress> = emptyList()) =
        WatchSnapshot.Empty.copy(watched = watched.map { Watched(it, 1L) }, progress = progress)

    private val show = listOf(episode("a1", 1, 1), episode("a2", 1, 2), episode("b1", 2, 1), episode("b2", 2, 2))
    private val sets = show.associateBy { it.setId }
    private val run = show.map { it.setId }

    @Test
    fun aShowGroupsByItsSeasonsInRunOrderAndOpensOnTheCurrentOne() {
        val list = assertNotNull(episodeListOf("b1", run, sets, watch()))

        assertEquals(listOf("Season 1", "Season 2"), list.sections.map { it.title })
        assertEquals(listOf("a1", "a2"), list.sections[0].rows.map { it.setId })
        assertEquals(1, list.currentSection)
    }

    @Test
    fun aRowCarriesItsNumberTitleAndRuntime() {
        val row = assertNotNull(episodeListOf("b1", run, sets, watch())).sections[0].rows[1]

        assertEquals(EpisodeRow("a2", "S1E2", "Title a2", 1_500, watched = false, progress = null, current = false), row)
    }

    @Test
    fun watchedProgressAndCurrentAreEachTheirOwnRows() {
        val list = assertNotNull(
            episodeListOf(
                "b1", run, sets,
                watch(watched = listOf("a1"), progress = listOf(Progress("a2", at = 750.0, duration = 1_500.0, updatedAt = 1L))),
            ),
        )
        val rows = list.sections.flatMap { it.rows }.associateBy { it.setId }

        assertTrue(rows.getValue("a1").watched)
        assertNull(rows.getValue("a1").progress)
        assertEquals(0.5f, rows.getValue("a2").progress)
        assertFalse(rows.getValue("a2").watched)
        assertTrue(rows.getValue("b1").current)
        assertEquals(1, rows.values.count { it.current })
    }

    /** A finished title is ticked, not ruled: a position left at the credits would draw a full bar beside the tick. */
    @Test
    fun aWatchedTitleDrawsNoProgressLineEvenWithAPositionLeft() {
        val list = assertNotNull(episodeListOf("b1", run, sets, watch(watched = listOf("a2"), progress = listOf(Progress("a2", 1_490.0, 1_500.0, 1L)))))

        assertNull(list.sections[0].rows[1].progress)
    }

    @Test
    fun aRuntimeNobodyKnowsDrawsNoProgressLineAndNoRuntime() {
        val unmeasured = sets + ("a2" to episode("a2", 1, 2, durationSecs = null))
        val row = assertNotNull(episodeListOf("b1", run, unmeasured, watch(progress = listOf(Progress("a2", 300.0, null, 1L))))).sections[0].rows[1]

        assertNull(row.progress)
        assertNull(row.runtimeSecs)
    }

    @Test
    fun aFilmHasNoList() {
        val film = fakeMediaSet("f1", kind = Kind.MOVIE)

        assertNull(episodeListOf("f1", emptyList(), mapOf("f1" to film), watch()))
        // A film opened from a hand-picked list walks the list with ⏮/⏭, but it is no series to list.
        assertNull(episodeListOf("f1", listOf("f1", "f2"), mapOf("f1" to film), watch()))
    }

    @Test
    fun oneSeasonIsOneSection() {
        val list = assertNotNull(episodeListOf("a1", listOf("a1", "a2"), sets, watch()))

        assertEquals(listOf("Season 1"), list.sections.map { it.title })
        assertEquals(0, list.currentSection)
    }

    /** A course reads in the folders it was uploaded in — the catalogue's own sections. */
    @Test
    fun aCourseGroupsByItsFolders() {
        val lessons = listOf(
            lesson("l1", 1, path = "Basics/1. Start"),
            lesson("l2", 2, path = "Basics/1. Start"),
            lesson("l3", 1, chapter = "2. Broker"),
        )
        val list = assertNotNull(episodeListOf("l3", lessons.map { it.setId }, lessons.associateBy { it.setId }, watch()))

        assertEquals(listOf("Basics › 1. Start", "2. Broker"), list.sections.map { it.title })
        assertEquals("1", list.sections[0].rows[0].number)
        assertEquals(1, list.currentSection)
    }

    @Test
    fun aCourseWithNoFoldersIsOneList() {
        val lessons = listOf(lesson("l1", 1), lesson("l2", 2))
        val list = assertNotNull(episodeListOf("l1", listOf("l1", "l2"), lessons.associateBy { it.setId }, watch()))

        assertEquals(1, list.sections.size)
        assertEquals(listOf("l1", "l2"), list.sections.single().rows.map { it.setId })
    }

    /** What the catalogue cannot place still lists — last, so the seasons it can place keep their order. */
    @Test
    fun anUnknownIdAndASeasonlessEpisodeGoLast() {
        val extra = episode("x1", season = null, number = null)
        val messy = listOf("ghost", "a1", "x1", "a2")
        val list = assertNotNull(episodeListOf("a1", messy, sets + ("x1" to extra), watch()))

        assertEquals(listOf("Season 1", OTHER_SECTION), list.sections.map { it.title })
        val other = list.sections.last().rows
        assertEquals(listOf("ghost", "x1"), other.map { it.setId })
        assertEquals(UNKNOWN_TITLE, other[0].title)
        assertEquals("", other[0].number)
        assertNull(other[0].runtimeSecs)
        assertEquals("", other[1].number)
        assertEquals(0, list.currentSection)
    }

    @Test
    fun aRunOfNothingPlaceableIsOneEpisodesSection() {
        val list = assertNotNull(episodeListOf("ghost-1", listOf("ghost-1", "ghost-2"), emptyMap(), watch()))

        assertEquals(listOf(EPISODES_SECTION), list.sections.map { it.title })
        assertTrue(list.sections.single().rows[0].current)
    }

    @Test
    fun anOpenTitleTheRunDoesNotHoldOpensOnTheFirstSection() {
        val list = assertNotNull(episodeListOf("elsewhere", run, sets, watch()))

        assertEquals(0, list.currentSection)
        assertTrue(list.sections.flatMap { it.rows }.none { it.current })
    }
}
