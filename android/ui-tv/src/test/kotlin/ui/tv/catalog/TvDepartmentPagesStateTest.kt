package ui.tv.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.Department
import catalog.Entry
import catalog.Shelf
import catalog.allSetsById
import catalog.moviesDepartmentOf
import catalog.shelvesOf
import catalog.showsDepartmentOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.TvMoviesPageEntryKey
import kotlin.test.assertEquals

/**
 * [TvShowsDepartmentPage] and [TvMoviesDepartmentPage] over real
 * [showsDepartmentOf]/[moviesDepartmentOf] output — a wide screen and a
 * generous lazy-row cache window so every plate this checks is actually
 * composed, not just scrolled past.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1920dp-h1080dp")
class TvDepartmentPagesStateTest : TvScreenStateTest() {
    /** A header row past the old six-plate Home limit shows every one of its own up-to-a-dozen stops, not just six. */
    @Test
    fun aPopularRowOfMoreThanSixShowsShowsAllOfThem() {
        val shows = (0 until 13).map { i -> set("s$i", Kind.EPISODE, "Ep", show = "Show %02d".format(i), addedAt = i.toLong(), episode = 1) }
        val dept = showsDepartmentOf(Kind.EPISODE, seriesEntriesOf(shows), allSetsById(shelvesOf(shows)), WatchSnapshot.Empty)!!

        show { TvShowsDepartmentPage(Department.SERIES, dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}) }

        for (i in 0..7) {
            compose.onAllNodesWithText("Show %02d".format(i)).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Show %02d missing".format(i) } }
        }
    }

    /** With no header row to win arrival (too few shows for Popular/New, no categories on a Series shelf), the wall's own first plate takes it — the hero itself is never a focus stop any more. */
    @Test
    fun withNoHeaderRowsArrivalLandsOnTheWallsFirstPlate() {
        val lead = set("s0-e1", Kind.EPISODE, "Ep", show = "Lead Show", addedAt = 0, episode = 1).copy(backdropPath = "/bd0")
        val other = set("s1-e1", Kind.EPISODE, "Ep", show = "Other Show", addedAt = 1, episode = 1)
        val shows = listOf(lead, other)
        val dept = showsDepartmentOf(Kind.EPISODE, seriesEntriesOf(shows), allSetsById(shelvesOf(shows)), WatchSnapshot.Empty)!!

        show { TvShowsDepartmentPage(Department.SERIES, dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}) }

        compose.onNodeWithText("Lead Show").assertIsFocused()
    }

    /**
     * [DeptRow]/[GenreTileRow]'s own lazy rewrite still wires arrival focus
     * correctly: every film watched empties Featured and Acclaimed, so
     * Genres — the row over a [LazyRow][androidx.compose.foundation.lazy.LazyRow] —
     * is the first non-empty row left, and its own tile takes the remote.
     */
    @Test
    fun theMoviesFrontPagesGenreRowTakesArrivalFocusWhenEarlierRowsAreEmpty() {
        val films = (0 until 3).map { i -> set("f$i", Kind.MOVIE, "Film $i", addedAt = i.toLong()).copy(genres = listOf("Action")) }
        val dept = moviesDepartmentOf(films) { true }!!

        show { TvMoviesDepartmentPage(dept = dept, onOpenTitle = {}, onPlay = {}, onOpenGenre = {}, onOpenAllFilms = {}) }

        compose.onNodeWithText("Action").assertIsFocused()
        // The tile names its count across the art, spelled as the web's `countOf` does.
        compose.onNodeWithText("three titles").assertExists()
    }

    /**
     * Featured, Genres and Acclaimed all empty (every film watched, none
     * carries a genre) — three rows in a row skipped. Recently added ignores
     * watched status, so it alone is left to take arrival; this pins the
     * scroll-to-item math staying correct across more than one skipped row,
     * not just the one row the other test above already covers. The list
     * holds no item for a skipped row, or the arrival's scroll index, which
     * counts only the rows drawn, would land short of its row.
     */
    @Test
    fun recentlyAddedTakesArrivalFocusWhenEveryEarlierRowIsEmpty() {
        val films = (0 until 3).map { i -> set("f$i", Kind.MOVIE, "Film $i", addedAt = i.toLong()) }
        val dept = moviesDepartmentOf(films) { true }!!
        val listState = LazyListState()

        show { TvMoviesDepartmentPage(dept = dept, onOpenTitle = {}, onPlay = {}, onOpenGenre = {}, onOpenAllFilms = {}, listState = listState) }

        compose.onNodeWithText("Film 2").assertIsFocused()
        // The hero, Recently added and the "All N films" link.
        assertEquals(3, listState.layoutInfo.totalItemsCount)
    }

    /**
     * Unlike Movies/Series/Tutorials — omitted from the shelf list while
     * empty, so [DepartmentOrShelfWall] never meets one with nothing in it —
     * Documentaries is always present, so an empty library, or a kids
     * profile with nothing rated for it, reaches this wall with zero
     * entries.
     */
    @Test
    fun anEmptyDocumentariesWallShowsTheUploadHintRatherThanNothing() {
        show {
            DepartmentOrShelfWall(
                shelf = Shelf(Department.DOCUMENTARIES, emptyList()),
                watch = WatchSnapshot.Empty,
                heldIds = emptySet(),
                byId = emptyMap(),
                deptScroll = rememberTvDepartmentScrollStates(),
                onOpenTitle = {},
                onPlay = {},
                onOpenCollection = {},
                onOpenGenre = {},
                onOpenMoviesPage = {},
                restoreKey = null,
            )
        }

        compose.onNodeWithText("No documentaries yet. Upload one with mediagram add-docu <file|folder>.").assertIsDisplayed()
    }

    /**
     * The page's own outer list carries a [androidx.compose.ui.focus.focusRestorer]
     * (`TvMoviesDepartmentPage`'s own doc on why) so the rail's Right returns
     * to the plate it left; an explicit restore key naming a row further
     * down still has to win over it, not the restorer's own fallback.
     */
    @Test
    fun aRestoreKeyNamingARowPastTheHeroWinsOverThePagesOwnRestorer() {
        val films = (0 until 3).map { i -> set("f$i", Kind.MOVIE, "Film $i", addedAt = (2 - i).toLong()) }
        val dept = moviesDepartmentOf(films) { true }!!

        show { TvMoviesDepartmentPage(dept = dept, onOpenTitle = {}, onPlay = {}, onOpenGenre = {}, onOpenAllFilms = {}, restoreKey = "f1") }

        compose.onNodeWithText("Film 1").assertIsFocused()
    }

    /**
     * [TvWall] carries no restorer of its own (tried and reverted: it
     * restored into whichever plate a *different* page's own header last
     * remembered, ahead of an explicit restore key naming a show further
     * down — `TvSearchAndGenreTest`'s own regression). This still proves
     * the explicit key wins over the wall's own plain plate-0 default.
     */
    @Test
    fun aRestoreKeyNamingAShowFurtherDownTheWallStillWinsOverTheFirstPlate() {
        val shows = (0 until 3).map { i -> set("s$i", Kind.EPISODE, "Ep", show = "Show $i", addedAt = i.toLong(), episode = 1) }
        val dept = showsDepartmentOf(Kind.EPISODE, seriesEntriesOf(shows), allSetsById(shelvesOf(shows)), WatchSnapshot.Empty)!!

        show {
            TvShowsDepartmentPage(
                Department.SERIES, dept, WatchSnapshot.Empty,
                onOpenTitle = {}, onOpenCollection = {}, onPlay = {}, restoreKey = "SHOW/Show 2",
            )
        }

        compose.onNodeWithText("Show 2").assertIsFocused()
    }

    /**
     * "All N films →" is the web's `.dept-all` pill at the page's foot: coming
     * back from the wall it opened lands on it, Up reaches the last row of
     * films above it, Down comes back, and a press opens the wall.
     */
    @Test
    fun theAllFilmsPillIsWhereTheWallReturnsToAndOpensIt() {
        val films = (0 until 3).map { i -> set("f$i", Kind.MOVIE, "Film $i", addedAt = i.toLong()) }
        val dept = moviesDepartmentOf(films) { true }!!
        var opened = 0

        show {
            TvMoviesDepartmentPage(
                dept = dept, onOpenTitle = {}, onPlay = {}, onOpenGenre = {}, onOpenAllFilms = { opened++ },
                restoreKey = TvMoviesPageEntryKey,
            )
        }

        compose.onNodeWithText("All 3 films →").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("Film 2").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("All 3 films →").assertIsFocused()
        compose.onNodeWithText("All 3 films →").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, opened)
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun seriesEntriesOf(sets: List<MediaSet>): List<Entry.Collection> =
        shelvesOf(sets).first { it.title == "Series" }.entries.filterIsInstance<Entry.Collection>()
}
