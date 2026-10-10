package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import catalog.SetCard
import kotlin.math.abs
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
 * The Continue strip comes to rest with a card at its start, as the web's
 * `scroll-snap-type: x mandatory` strip does (`home.css:220,234`), rather
 * than wherever a fling runs out. Unclipped bounds, so a card half scrolled
 * off reads where it really is rather than clipped to the strip's edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h500dp")
class ResumeRowSnapTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film $id", show = null, chapter = null, path = null, season = null,
        episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 3_600, posterPath = null, totalBytes = 0,
    )

    /** Where card [i] starts, or null once the strip has let go of it. */
    private fun cardLeft(i: Int): Float? {
        val card = hasClickAction() and hasText("Film $i")
        if (compose.onAllNodes(card).fetchSemanticsNodes().isEmpty()) return null
        return compose.onNode(card).getUnclippedBoundsInRoot().left.value
    }

    @Test
    fun aSwipeComesToRestWithACardAtTheStripsStart() {
        val cards = (1..8).map { SetCard(film("$it"), "1h left", 0.4f, false) }
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { ResumeRow(cards = cards, onPlay = {}, modifier = Modifier.testTag("strip")) } }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(startX = centerX + 150f, endX = centerX - 150f, durationMillis = 400) }
        compose.waitForIdle()
        val first = cardLeft(1)
        assertTrue(first == null || first < -1f, "the strip should have moved, the first card starts at $first")
        val lefts = (1..8).mapNotNull(::cardLeft)
        assertTrue(lefts.any { abs(it) < 1f }, "a card should rest at the strip's start, cards start at $lefts")
    }
}
