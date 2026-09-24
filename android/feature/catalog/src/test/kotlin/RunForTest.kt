package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [runFor]'s own job: find the collection an episode or lesson belongs to by
 * its show name, the same lookup `openTitle`'s "else" branch runs in
 * `app.js`, and flatten it the one way [playOrder] already does — a season
 * boundary and a course folder boundary are the two places that flattening
 * has ever gone wrong, so both are pinned here alongside the film case,
 * which has no collection to find at all.
 */
class RunForTest {

    @Test
    fun aFilmHasNoRun() {
        val film = set("Film", Kind.MOVIE, show = null, episode = null)
        assertEquals(emptyList(), runFor(film, readyState(emptyList())))
    }

    @Test
    fun anEpisodesRunCrossesASeasonBoundary() {
        val season1 = division(
            "Season 1", 1,
            listOf(set("S1E1", Kind.EPISODE, "Show", 1), set("S1E2", Kind.EPISODE, "Show", 2)),
        )
        val season2 = division("Season 2", 2, listOf(set("S2E1", Kind.EPISODE, "Show", 1)))
        val state = readyState(listOf(collection("show", "Show", listOf(season1, season2))))

        assertEquals(listOf("S1E1", "S1E2", "S2E1"), runFor(set("S1E1", Kind.EPISODE, "Show", 1), state))
    }

    @Test
    fun aLessonsRunCrossesAFolderBoundary() {
        val detour = division("14. Exkurs TWS", null, listOf(set("Detour", Kind.TUTORIAL, "Course", 14)))
        val folder = division("Folder", null, listOf(set("L1", Kind.TUTORIAL, "Course", 1)), listOf(detour))
        val state = readyState(listOf(collection("course", "Course", listOf(folder))))

        assertEquals(listOf("L1", "Detour"), runFor(set("L1", Kind.TUTORIAL, "Course", 1), state))
    }

    @Test
    fun aTitleWithNoMatchingCollectionHasNoRun() {
        assertEquals(emptyList(), runFor(set("Orphan", Kind.EPISODE, "Nowhere", 1), readyState(emptyList())))
    }
}

private fun readyState(entries: List<Entry.Collection>) = CatalogUiState.Ready(shelves = listOf(Shelf("Series", entries)))

private fun collection(key: String, name: String, divisions: List<Division>) = Entry.Collection(
    key = key,
    kind = CollectionKind.SHOW,
    name = name,
    posterPath = null,
    posterKey = null,
    count = 0,
    chapters = 0,
    divisions = divisions,
)

private fun division(title: String, season: Int?, items: List<MediaSet>, children: List<Division> = emptyList()) =
    Division(title = title, season = season, items = items, children = children)

private fun set(setId: String, kind: Kind, show: String?, episode: Int?) = MediaSet(
    setId = setId,
    kind = kind,
    title = setId,
    show = show,
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
