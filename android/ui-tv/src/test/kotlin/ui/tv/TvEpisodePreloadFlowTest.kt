package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.Profile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.FilmPreloadState
import ui.tv.catalog.TvOfflineBadgeTag
import ui.tv.catalog.set
import ui.tv.player.TvPlayerScreenTag

/**
 * An episode row's own Preload control on a show's page, walked with the
 * remote over the real [TvLibrary] and the real `TitlePreloadViewModel`
 * [TvAppFixture] builds: the row plays on OK, Right steps onto its control,
 * and the row says how much of the episode this device holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvEpisodePreloadFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    private val pilot = set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 10, episode = 1).copy(season = 1, totalBytes = TOTAL)
    private val second = set("second", Kind.EPISODE, "Second", show = "A Show", addedAt = 11, episode = 2).copy(season = 1, totalBytes = 2 * TOTAL)

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", listOf(pilot, second))
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("A Show") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun anEpisodeRowsPreloadIsReachableWithTheRemote() {
        walkToPilot()

        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("Preload · 100 MB").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        // The film's engine and queue, under the name Continue gives an episode.
        assertEquals(listOf(Triple("pilot", "A Show · S1E1", TOTAL)), fixture.filmPreloading.enqueueCalls)

        // The row itself still plays on OK.
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithText("1. Pilot").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()
    }

    @Test
    fun aPartlyHeldEpisodeShowsItsPercentageOnTv() {
        state(FilmPreloadState.Idle(TOTAL * 42 / 100, TOTAL))
        openShow()

        // Said once, beside a control that reads as for nothing held; nothing on the row holding nothing.
        compose.onAllNodesWithText("42%").assertCountEquals(1)
        compose.onNodeWithText("Preload · 100 MB").assertExists()
        compose.onNodeWithText("Preload · 200 MB").assertExists()
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(1)

        state(FilmPreloadState.Running(TOTAL * 60 / 100, TOTAL))
        compose.onNodeWithText("60%").assertExists()
        compose.onNodeWithText("Preloading").assertExists()

        // In full: the held badge, not a figure.
        state(FilmPreloadState.Done)
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
        compose.onNodeWithTag(TvOfflineBadgeTag).assertExists()
        compose.onNodeWithText("Preloaded ✓").assertExists()
    }

    @Test
    fun focusStaysOnTheRowWhenItsPreloadStateChanges() {
        walkToPilot()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("Preload · 100 MB").assertIsFocused()

        state(FilmPreloadState.Queued)
        compose.onNodeWithText("Queued").assertIsFocused()
        state(FilmPreloadState.Running(TOTAL / 10, TOTAL))
        compose.onNodeWithText("Preloading").assertIsFocused()
        state(FilmPreloadState.Done)
        compose.onNodeWithText("Preloaded ✓").assertIsFocused()

        // Remove sits beside it once Done, and takes the remote back to the control as it goes.
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("Remove preload").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("pilot"), fixture.filmPreloading.removeCalls)
        state(FilmPreloadState.Idle(0, TOTAL))
        compose.onNodeWithText("Remove preload").assertDoesNotExist()
        compose.onNodeWithText("Preload · 100 MB").assertIsFocused()

        // On the title, the control changing beside it leaves the remote where it is.
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithText("1. Pilot").assertIsFocused()
        state(FilmPreloadState.Running(TOTAL / 2, TOTAL))
        compose.onNodeWithText("1. Pilot").assertIsFocused()
        state(FilmPreloadState.Done)
        compose.onNodeWithText("1. Pilot").assertIsFocused()
    }

    private fun openShow() {
        compose.onNode(hasText("A Show") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** The show's own Play takes the remote on arrival; Down walks it over the tabs onto the first episode. */
    private fun walkToPilot() {
        openShow()
        val row = compose.onNodeWithText("1. Pilot")
        repeat(4) { if (!row.isFocused()) key(KeyEvent.KEYCODE_DPAD_DOWN) }
        row.assertIsFocused()
    }

    private fun state(state: FilmPreloadState) {
        compose.runOnUiThread { fixture.filmPreloading.setState(pilot.setId, TOTAL, state) }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.isFocused() = fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    private companion object {
        const val TOTAL = 100 * 1_048_576L
    }
}
