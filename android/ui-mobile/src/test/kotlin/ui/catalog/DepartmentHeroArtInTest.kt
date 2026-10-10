package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
import org.robolectric.annotation.GraphicsMode
import ui.common.catalog.HERO_ARTWORK_TEST_TAG
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The department hero's art-in plays once a page visit, Back included, as the web's does,
 * and never on a scroll back, a redraw or a turn of the tablet. A picture
 * still settling from 1.04 has its top-left further left than at rest, and a
 * node's unclipped bounds in root follow the layer, so that is what is read.
 * Alpha cannot be read here (see PageGroundColorTest); the fade is the device
 * walk's. Native graphics give text its real width, so a quote's scale shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class DepartmentHeroArtInTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        frames(2)
    }

    private fun frames(count: Int) {
        compose.waitForIdle()
        repeat(count) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun artLeft() =
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun leftOf(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private val lead =
        MediaSet(
            setId = "lead", kind = Kind.MOVIE, title = "a lowercase title", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
            totalBytes = 0, backdropPath = "backdrop.jpg", tagline = "a quotable line",
        )

    @Composable
    private fun DepartmentPage(reveals: Reveals, state: LazyListState = LazyListState()) =
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            item { DepartmentHero(title = "Movies", line = "four films", lead = lead, onOpenTitle = null, reveals = reveals) }
            // Enough rows of the same content type that the hero's slot is reused, not kept.
            items(12) { Spacer(Modifier.fillMaxWidth().height(400.dp)) }
        }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun aDepartmentHeroScrolledAwayAndBackStaysAtRest() {
        val list = LazyListState()
        show {
            DepartmentPage(rememberReveals(), list)
        }
        val art = artLeft()
        val quote = leftOf("“a quotable line”")
        compose.mainClock.advanceTimeBy(150)
        assertEquals(quote, leftOf("“a quotable line”"), 0.05f, "the quote waits behind its picture")
        compose.mainClock.advanceTimeBy(1_700)
        val artRest = artLeft()
        val quoteRest = leftOf("“a quotable line”")
        assertTrue(art < artRest - 5f && quote < quoteRest - 1f, "both settle from 1.04")
        compose.runOnUiThread { list.requestScrollToItem(12) }
        frames(3)
        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        assertEquals(artRest, artLeft(), 0.5f, "scrolled back, the picture is already at rest")
        assertEquals(quoteRest, leftOf("“a quotable line”"), 0.5f, "and so is its quote")
    }

    /** The shelves' saved slot outlives a title pushed over them; the department's art-in must not. */
    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun aDepartmentComeBackToWithBackPlaysItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("shelves") { DepartmentPage(rememberReveals()) }
        }
        compose.mainClock.advanceTimeBy(1_800)
        val rest = artLeft()
        compose.runOnUiThread { open = false }
        frames(2)
        compose.runOnUiThread { open = true }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "Back is a new visit, and plays it, as on the web")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aPhonesDepartmentHeroSettlesToo() {
        show { DepartmentPage(rememberReveals()) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertTrue(start < artLeft() - 3f, "the compact hero's strip starts at 1.04 too")
    }
}
