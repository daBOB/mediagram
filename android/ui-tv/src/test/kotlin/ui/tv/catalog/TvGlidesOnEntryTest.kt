package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import catalog.AnimeLibrary
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.animeDepartmentOf
import catalog.moviesDepartmentOf
import designsystem.Overscan
import model.Kind
import model.WatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.TvContentGutter
import ui.tv.chrome.TvDepartmentsBarHeight
import ui.tv.chrome.TvPagePadding

/**
 * Down off the bar onto a department page, the way the box does it: the
 * page under the bar's padding, the television's own scroll rule, and the
 * page not taking the remote itself, as after a pill press. The page glides
 * up over many frames to the stop the remote lands on, never jumping there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGlidesOnEntryTest : TvScreenStateTest() {
    private fun show(name: String) =
        Entry.Collection(
            key = "ANIME/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1,
            divisions = listOf(Division(name, 1, listOf(set("$name-e1", Kind.EPISODE, "E1", show = name, addedAt = 0, episode = 1)), emptyList())),
        )

    /** Anime with nothing underway: the hero alone over the wall, its first plates at the screen's foot. */
    private val anime = animeDepartmentOf(AnimeLibrary(shows = (1..14).map { show("Show $it") }, films = emptyList()), emptyMap(), WatchSnapshot.Empty)!!

    @Test
    fun downFromTheBarGlidesTheAnimeHeroOffOverFramesOntoTheFirstPlateAndUpBringsItBack() {
        val grid = LazyGridState()
        showUnderTheBar { TvAnimeDepartmentPage(anime, WatchSnapshot.Empty, {}, {}, {}, gridState = grid) }

        val gone = downFromTheBar(frames = 40) { -grid.layoutInfo.visibleItemsInfo.first { it.index == 0 }.offset.y }

        assertGlides(gone)
        compose.onNode(isFocused()).assert(hasText(anime.shows.first().name))
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithTag("bar").assertIsFocused()
        assertEquals(0 to 0, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset)
    }

    @Test
    fun downMidGlideHandsTheScrollToTheNextLine() {
        val grid = LazyGridState()
        showUnderTheBar { TvAnimeDepartmentPage(anime, WatchSnapshot.Empty, {}, {}, {}, gridState = grid) }

        downFromTheBar(frames = 5) { 0 }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        compose.onNode(isFocused()).assert(hasText(anime.shows[Columns].name))
        assertTrue(compose.onNode(isFocused()).getUnclippedBoundsInRoot().bottom <= 540.dp)
    }

    /** A row page: the first row's card already laid out under the hero, which Compose alone threw off in about five frames. */
    @Test
    fun downFromTheBarGlidesTheMoviesHeroOffAndRestsWithTheCardOnScreen() {
        val films = (1..40).map { set("film$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()) }
        val dept = moviesDepartmentOf(films) { false }!!
        val list = LazyListState()
        showUnderTheBar { TvMoviesDepartmentPage(dept, {}, {}, {}, {}, listState = list) }

        val gone = downFromTheBar(frames = 40) { -list.layoutInfo.visibleItemsInfo.first { it.index == 0 }.offset }

        assertGlides(gone)
        compose.onNode(isFocused()).assert(hasText(dept.featured.first().title))
        assertTrue(compose.onNode(isFocused()).getUnclippedBoundsInRoot().bottom <= 540.dp - Overscan.vertical + 1.dp)
    }

    /** Spread over the web's 420ms: little in the first frames, still short at 190ms, settled by 30 frames — and never back. */
    private fun assertGlides(gone: List<Int>) {
        val end = gone.last()
        assertTrue("never moved: $gone", end > 0)
        assertTrue("jumped: $gone", gone.take(3).all { it < end / 2 })
        assertTrue("over too soon: $gone", gone[11] < end)
        assertTrue("never settled: $gone", gone.drop(30).all { it == end })
        assertTrue("went back: $gone", gone.zipWithNext().all { (a, b) -> b >= a })
    }

    /** Down on the bar, then [read] after each of [frames] frames. */
    private fun downFromTheBar(
        frames: Int,
        read: () -> Int,
    ): List<Int> {
        compose.mainClock.autoAdvance = false
        compose.onNode(isFocused()).performKeyInput { keyDown(Key.DirectionDown) }
        val seen = List(frames) { compose.mainClock.advanceTimeByFrame().let { read() } }
        compose.onNode(isFocused()).performKeyInput { keyUp(Key.DirectionDown) }
        compose.mainClock.autoAdvance = true
        return seen
    }

    private fun showUnderTheBar(page: @Composable () -> Unit) {
        val padding = TvPagePadding(TvContentGutter, TvDepartmentsBarHeight + Overscan.vertical, TvContentGutter, Overscan.vertical)
        show {
            val bar = remember { FocusRequester() }
            val content = remember { FocusRequester() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides LeanbackPivot, LocalTvPagePadding provides padding) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().focusRequester(content)) {
                        CompositionLocalProvider(LocalTakesArrivalFocus provides false, content = page)
                    }
                    Box(
                        Modifier.align(Alignment.TopStart).testTag("bar").focusRequester(bar).focusProperties { down = content }
                            .focusable().height(TvDepartmentsBarHeight).fillMaxWidth(),
                    )
                }
            }
            LaunchedEffect(Unit) { bar.requestFocus() }
        }
    }
}
