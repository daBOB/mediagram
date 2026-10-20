package ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.FilmPreloadRow
import playback.FilmPreloadState
import kotlin.test.assertEquals

/**
 * An episode row's own Preload control over the real [LibraryFlowFixture]
 * route: the same [player.TitlePreloadViewModel] and engine a film page
 * drives, an episode queued under the name Continue gives it, and a row's
 * own collection kept across its progress ticks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class EpisodePreloadFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture()
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
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

    private fun openShow() {
        compose.onNode(hasText("Series") and hasClickAction()).performClick()
        compose.onNode(hasText("Example Show") and hasClickAction()).performClick()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertExists()
    }

    private fun preloadTheFirstEpisode() {
        openShow()
        compose.onNode(hasText("Preload · 10 B") and hasClickAction()).performClick()
    }

    @Test
    fun pressingPreloadQueuesTheEpisode() {
        preloadTheFirstEpisode()

        assertEquals(listOf(Triple("episode-1", "Example Show · S1E1", 10L)), fixture.filmPreloading.enqueueCalls)
    }

    @Test
    fun thePreloadsScreenNamesAnEpisodeLikeContinue() {
        preloadTheFirstEpisode()
        val (setId, title, total) = fixture.filmPreloading.enqueueCalls.single()
        fixture.filmPreloading.setQueueOverview(listOf(FilmPreloadRow.Waiting(setId, title, total)))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Preloads · 1").performClick()

        compose.onNodeWithText("Example Show · S1E1").assertExists()
    }

    /** A running episode's progress ticks recompose its row without asking the engine for a fresh state flow each time. */
    @Test
    fun progressTicksDoNotReCollectAnEpisodeRow() {
        openShow()
        compose.waitForIdle()
        val asked = fixture.filmPreloading.stateOfCalls

        for (held in 1L..9L) {
            fixture.filmPreloading.setState("episode-1", 10L, FilmPreloadState.Running(held, 10L))
            compose.waitForIdle()
        }

        compose.onNodeWithText("90%").assertExists()
        assertEquals(asked, fixture.filmPreloading.stateOfCalls)
    }
}
