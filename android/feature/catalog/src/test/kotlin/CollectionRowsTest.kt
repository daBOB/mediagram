package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/** `rowsOf`: each set is called by its own number, as `course-view.js` calls it with `episodeLabel`. */
class CollectionRowsTest {
    /** A season missing E02 calls the next episode 3, not 2. */
    @Test
    fun aMissingEpisodeLeavesAGapInTheNumbers() {
        val season = listOf(Division("Season 1", 1, listOf(set("a", 1), set("c", 3), set("d", 4, 5)), emptyList()))

        assertEquals(listOf("1", "3", "4-5"), rowsOf(season).filterIsInstance<CollectionRow.Item>().map { it.number })
    }

    /** Nothing to call it by: its place in the list, as before. */
    @Test
    fun anUnnumberedSetIsCountedByItsPlace() {
        val course = listOf(Division("Basics", null, listOf(set("a", null), set("b", null)), emptyList()))

        assertEquals(listOf("1", "2"), rowsOf(course).filterIsInstance<CollectionRow.Item>().map { it.number })
    }

    private fun set(
        id: String,
        first: Int?,
        last: Int? = null,
    ) = MediaSet(
        setId = id,
        kind = Kind.EPISODE,
        title = id,
        show = "Show",
        chapter = null,
        path = null,
        season = 1,
        episodeFirst = first,
        episodeLast = last,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
    )
}
