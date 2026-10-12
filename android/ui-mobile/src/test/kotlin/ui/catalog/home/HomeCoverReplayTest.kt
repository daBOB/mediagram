package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.catalog.HERO_ARTWORK_TEST_TAG

/** What a refresh, a recycled list slot or a stale turn must never replay: the cover's fade and settle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class HomeCoverReplayTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film ${id.uppercase()}", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
        totalBytes = 0, backdropPath = "b-$id.jpg", tagline = null,
    )

    private fun host(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun cover(films: MutableState<List<MediaSet>>, onPlay: (MediaSet) -> Unit = {}, arrived: MutableState<Boolean>? = null) = host {
        val state = arrived ?: remember { mutableStateOf(false) }
        HomeCover(
            films = films.value, watchlist = emptySet(), width = 1164.dp, onPlay = onPlay,
            onOpenTitle = {}, onToggleWatchlist = { _, _ -> }, arrived = state,
        )
    }

    private fun artLeft() = compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun sync(films: MutableState<List<MediaSet>>, next: List<MediaSet>) {
        compose.runOnUiThread { films.value = next }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theArrivalFilmDoesNotSettleAgainWhenATurnedFilmLeavesTheLineup() {
        val films = mutableStateOf(listOf(film("a"), film("b")))
        cover(films)
        compose.onNodeWithContentDescription("Cover story 2 of 2").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        sync(films, listOf(film("a")))
        assertEquals(0f, artLeft(), 0.5f)
    }

    @Test
    fun aFilmThatLeftAndComesBackFromARefreshIsNotTurnedTo() {
        val films = mutableStateOf(listOf(film("a"), film("b"), film("c")))
        cover(films)
        compose.onNodeWithContentDescription("Cover story 3 of 3").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        sync(films, listOf(film("a"), film("b")))
        sync(films, listOf(film("a"), film("b"), film("c")))
        assertEquals(0f, artLeft(), 0.5f)
    }

    @Test
    fun theTapShieldDoesNotOutliveAFilmARefreshDropped() {
        var played: String? = null
        val films = mutableStateOf(listOf(film("a"), film("b"), film("c")))
        cover(films, onPlay = { played = it.setId })
        compose.onNodeWithContentDescription("Cover story 3 of 3").performClick()
        compose.mainClock.advanceTimeBy(500)
        sync(films, listOf(film("a"), film("b")))
        assertNull(played)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertEquals("b", played)
    }

    @Test
    fun aCoverRecycledOutOfAListAndBackIsAtRest() {
        val films = mutableStateOf(listOf(film("a")))
        lateinit var scrollTo: suspend (Int) -> Unit
        host {
            val state = rememberLazyListState()
            val arrived = remember { mutableStateOf(false) }
            scrollTo = { state.scrollToItem(it) }
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(777.dp)) {
                item(key = "cover") {
                    HomeCover(
                        films = films.value, watchlist = emptySet(), width = 1164.dp, onPlay = {},
                        onOpenTitle = {}, onToggleWatchlist = { _, _ -> }, arrived = arrived,
                    )
                }
                items(20) { Box(Modifier.fillMaxWidth().height(600.dp)) }
            }
        }
        assertTrue(artLeft() < -20f, "the arrival settles first, left was ${artLeft()}")
        compose.mainClock.advanceTimeBy(15_000)
        val scroll = { to: Int -> compose.runOnUiThread { runBlocking { scrollTo(to) } } }
        for (to in listOf(5, 10, 15, 20)) {
            scroll(to)
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        scroll(0)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, artLeft(), 0.5f)
    }
}
