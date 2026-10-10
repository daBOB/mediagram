package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import designsystem.LocalReducedMotion
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

/**
 * One Featured slide's motion (`playback.css:499-510,585`): the poster closes
 * in once and holds where it is while the reel is paused, and rests at its
 * own size under reduced motion; the copy rises line by line.
 *
 * The poster's frame clips it, as the web's does, so its clipped left edge
 * stays at the frame's while the unclipped one is mapped left by the scale
 * and pan: the gap between the two is how far it has drifted, 0 at rest.
 * Alpha is drawn and cannot be captured here (see PageGroundColorTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h600dp")
class FeaturedSlideMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val film = MediaSet(
        setId = "f", kind = Kind.MOVIE, title = "A Film", show = null, chapter = null, path = null, season = null,
        episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 6_000, posterPath = null, totalBytes = 0,
    )

    private fun show(paused: MutableState<Boolean> = mutableStateOf(false), reducedMotion: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                        FeaturedSlide(
                            set = film, count = "Featured · 1 / 1", info = null, paused = paused.value,
                            onTogglePause = {}, onPlay = {}, onDetails = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun drifted(): Float {
        val poster = compose.onNodeWithTag(FEATURED_POSTER_TEST_TAG, useUnmergedTree = true)
        return poster.getBoundsInRoot().left.value - poster.getUnclippedBoundsInRoot().left.value
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    /** With "Remove animations" on, the poster is at its own size and stays there. */
    @Test
    fun underReducedMotionThePosterRestsAtItsOwnSize() {
        show(reducedMotion = true)
        assertEquals(0f, drifted(), 0.5f)
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(0f, drifted(), 0.5f)
    }

    @Test
    fun thePosterClosesInOnceAndHoldsWhereItIsWhilePaused() {
        val paused = mutableStateOf(false)
        show(paused)
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { paused.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        val held = drifted()
        assertTrue(held > 1f, "the poster should have closed in by now, drift was $held")
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(held, drifted(), 0.1f)
        compose.runOnUiThread { paused.value = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(9_000)
        assertTrue(drifted() > held, "resumed from where it held")
    }

    @Test
    fun theCopyRisesLineByLine() {
        show()
        compose.mainClock.advanceTimeBy(300)
        val count = top("Featured · 1 / 1")
        val play = top("Play")
        compose.mainClock.advanceTimeBy(1_500)
        // The buttons wait four beats (450ms), so at 300ms they have not moved.
        assertEquals(14f, play - top("Play"), 0.5f)
        val countToGo = count - top("Featured · 1 / 1")
        assertTrue(countToGo in 1f..10f, "the count line should be well on its way, $countToGo dp to go")
    }
}
