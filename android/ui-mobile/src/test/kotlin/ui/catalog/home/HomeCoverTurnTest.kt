package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.LocalReducedMotion
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
import ui.common.catalog.HERO_ARTWORK_TEST_TAG

/**
 * The cover turns the web's way (`home.css:15-28`, `home-cover.js:56-63`):
 * the old slide is gone at once, the new one comes up out of the black
 * ground, and its picture settles from 1.06 to its own size over 14s. A tap
 * while it comes up lands on nothing; a film a refresh moves into view is
 * simply there.
 *
 * The settle is read from the picture's unclipped bounds: the cover clips its
 * picture, as the web's `overflow: hidden` does, so the clipped bounds never
 * grow, while the mapped left edge sits left of the cover's own (0) while the
 * picture is scaled up. The fade is drawn, and nothing here captures pixels
 * (see PageGroundColorTest), so "out of black" is the device walk's; the old
 * slide being gone at once is what is checked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class HomeCoverTurnTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String, tagline: String? = null) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film ${id.uppercase()}", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
        totalBytes = 0, backdropPath = "b-$id.jpg", tagline = tagline,
    )

    private fun show(
        films: MutableState<List<MediaSet>>,
        backdrop: Backdrop = Backdrop.DEFAULT,
        reducedMotion: Boolean = false,
        onPlay: (MediaSet) -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalBackdrop provides backdrop, LocalReducedMotion provides reducedMotion) {
                        HomeCover(
                            films = films.value, watchlist = emptySet(), width = 1164.dp,
                            onPlay = onPlay, onOpenTitle = {}, onToggleWatchlist = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun artLeft() = compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    @Test
    fun aTurnRemovesTheOldSlideAtOnce() {
        show(mutableStateOf(listOf(film("a"), film("b"), film("c"))))
        compose.onNodeWithContentDescription("Cover story 2 of 3").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("FILM A").assertDoesNotExist()
        compose.onNodeWithText("FILM B").assertExists()
    }

    /** Mid-fade, a press lands on nothing rather than on a control still coming up out of the dark. */
    @Test
    fun aTapWhileTheNewFilmComesUpLandsOnNothing() {
        var played: String? = null
        show(mutableStateOf(listOf(film("a"), film("b"))), onPlay = { played = it.setId })
        compose.onNodeWithContentDescription("Cover story 2 of 2").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertNull(played)
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertEquals("b", played)
    }

    @Test
    fun theFirstSlideSettlesFromCloseUpAndASyncLeavesItSettled() {
        val films = mutableStateOf(listOf(film("a", tagline = "First line.")))
        show(films)
        assertTrue(artLeft() < -20f, "the picture should start scaled up past the cover's edge, left was ${artLeft()}")
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(0f, artLeft(), 0.5f)
        // The same film with new details redraws in place,
        // neither fading up nor settling again.
        compose.runOnUiThread { films.value = listOf(film("a", tagline = "A new line.")) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("A new line.").assertExists()
        assertEquals(0f, artLeft(), 0.5f)
    }

    @Test
    fun underBlurredThePictureStillSettles() {
        show(mutableStateOf(listOf(film("a"))), backdrop = Backdrop.BLURRED)
        assertTrue(artLeft() < -20f, "Blurred's own 1.12 must not replace the settle, left was ${artLeft()}")
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(0f, artLeft(), 0.5f)
    }

    /** A cover film watched on another device leaves the lineup: the one moved into view is there at once, with no fade and no shield. */
    @Test
    fun aFilmARefreshMovesIntoViewShowsAtOnce() {
        var played: String? = null
        val films = mutableStateOf(listOf(film("a"), film("b"), film("c")))
        show(films, onPlay = { played = it.setId })
        compose.onNodeWithContentDescription("Cover story 3 of 3").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { films.value = films.value.take(2) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("FILM B").assertExists()
        assertEquals(0f, artLeft(), 0.5f)
        compose.onNodeWithContentDescription("Watch now: Film B").performClick()
        assertEquals("b", played)
    }

    @Test
    fun underReducedMotionTheCoverNeverTurns() {
        show(mutableStateOf(listOf(film("a"), film("b"))), reducedMotion = true)
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("FILM A").assertExists()
        compose.onNodeWithText("FILM B").assertDoesNotExist()
    }
}
