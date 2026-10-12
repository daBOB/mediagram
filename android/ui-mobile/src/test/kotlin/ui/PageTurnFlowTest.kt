package ui

import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.chrome.PAGE_TURN_TEST_TAG

/**
 * The page under the chrome rises 10dp into place when the viewer goes
 * somewhere (`shell.css:162-163`), and only then: not while a search is
 * typed, not for the player, not a second time when a page's catalog
 * arrives after it opened. Read from the page's layer-mapped bounds on a
 * paused clock; its fade is drawn, and nothing here captures pixels (see
 * PageGroundColorTest), so the fade is the device walk's to check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class PageTurnFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    @Before fun open() {
        // The ViewModels are held in the fixture's store; only Hilt's
        // generated-Activity factory lookup needs replacing in Robolectric.
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture()
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    private fun pageTop() = compose.onNodeWithTag(PAGE_TURN_TEST_TAG).getBoundsInRoot().top.value

    /** One frame composes and places the new page; the next starts its entrance. */
    private fun twoFrames() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun choosingADepartmentTurnsThePage() {
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        twoFrames()
        val rising = pageTop()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(rising - pageTop() > 5f, "the tab should start below its place, was ${rising - pageTop()}dp")
    }

    @Test fun openingSearchTurnsThePageButTypingInItDoesNot() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search").performClick()
        twoFrames()
        val rising = pageTop()
        compose.mainClock.advanceTimeBy(500)
        val rest = pageTop()
        assertTrue(rising - rest > 5f, "search should rise in, was ${rising - rest}dp")
        compose.onNode(hasSetTextAction()).performTextInput("Exa")
        twoFrames()
        assertEquals(rest, pageTop(), 0.5f)
    }

    @Test fun thePlayerOpensWithNoPageTurn() {
        title()
        compose.onNodeWithText("▶ Play").performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        compose.onNodeWithTag(PAGE_TURN_TEST_TAG).assertDoesNotExist()
    }

    /** A restored title shows "Loading…" until the catalog arrives; the page it then shows is the same page, already turned. */
    @Test fun aTitleWhoseCatalogArrivesAfterItOpenedTurnsOnlyOnce() {
        title()
        restoreWhileLoading()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        val rest = pageTop()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { fixture.catalogReady.complete(Unit) }
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("▶ Play").assertExists()
        assertEquals(rest, pageTop(), 0.5f)
    }

    /** As LibraryFlowTest's own: a poster tap on the Movies tab opens the film's page. */
    private fun title() {
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        compose.onAllNodes(hasText("Example Film") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    /** As LibraryFlowTest's own: the process dies with the stack saved, and comes back before the catalog has loaded. */
    private fun restoreWhileLoading() {
        compose.runOnUiThread {
            val saved = Bundle()
            controller.saveInstanceState(saved).pause().stop().destroy()
            fixture.close()
            fixture = LibraryFlowFixture(loading = true)
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).create(saved).start().resume().visible()
        }
        compose.waitForIdle()
    }
}
