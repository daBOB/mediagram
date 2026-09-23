package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `playOrder`'s one job: agree with the order the course's own pages render.
 * A show is trivial — seasons only, no folder ever shares a number with an
 * episode — so the case worth pinning is a course, where a folder can sit
 * between two lesson numbers.
 */
class PlayOrderTest {

    @Test
    fun aShowsSeasonsPlayInSeasonOrder() {
        val season1 = division("Season 1", season = 1, items = listOf(lesson("S1E1", 1), lesson("S1E2", 2)))
        val season2 = division("Season 2", season = 2, items = listOf(lesson("S2E1", 1), lesson("S2E2", 2)))

        assertEquals(listOf("S1E1", "S1E2", "S2E1", "S2E2"), playOrder(listOf(season1, season2)).map { it.setId })
    }

    /**
     * "3. Signal" holds lessons 1 to 13 and 15 to 21, with 14 missing — the
     * folder standing in that gap, "14. Exkurs TWS", plays between 13 and
     * 15, which is where the course puts it and where a viewer working
     * through it looks for it. Ported from the example in library.js.
     */
    @Test
    fun aFolderNumberedIntoAGapPlaysInThatGap() {
        val detour = division("14. Exkurs TWS", season = null, items = listOf(lesson("Detour", 1)))
        val signal = division(
            "3. Signal",
            season = null,
            items = (1..13).map { lesson("Lesson $it", it) } + (15..21).map { lesson("Lesson $it", it) },
            children = listOf(detour),
        )

        val order = playOrder(listOf(signal)).map { it.setId }
        assertEquals((1..13).map { "Lesson $it" } + "Detour" + (15..21).map { "Lesson $it" }, order)
    }

    @Test
    fun aDocumentIsSkippedRatherThanPlayed() {
        val chapter = division(
            "Chapter",
            season = 1,
            items = listOf(lesson("Lesson 1", 1), document("Handout", 2), lesson("Lesson 2", 3)),
        )

        assertEquals(listOf("Lesson 1", "Lesson 2"), playOrder(listOf(chapter)).map { it.setId })
    }

    @Test
    fun anUnnumberedFolderSortsAfterNumberedLessons() {
        val extras = division("Extras", season = null, items = listOf(lesson("Extra", 1)))
        val chapter = division(
            "Chapter",
            season = null,
            items = listOf(lesson("Lesson 1", 1)),
            children = listOf(extras),
        )

        assertEquals(listOf("Lesson 1", "Extra"), playOrder(listOf(chapter)).map { it.setId })
    }
}

private fun division(title: String, season: Int?, items: List<MediaSet>, children: List<Division> = emptyList()) =
    Division(title = title, season = season, items = items, children = children)

private fun lesson(setId: String, episode: Int) = set(setId, Kind.TUTORIAL, episode)

private fun document(setId: String, episode: Int) = set(setId, Kind.DOCUMENT, episode)

private fun set(setId: String, kind: Kind, episode: Int) = MediaSet(
    setId = setId,
    kind = kind,
    title = setId,
    show = "Course",
    chapter = null,
    path = null,
    season = null,
    episodeFirst = episode,
    episodeLast = episode,
    year = null,
    durationSecs = null,
    posterPath = null,
    totalBytes = 0,
)
