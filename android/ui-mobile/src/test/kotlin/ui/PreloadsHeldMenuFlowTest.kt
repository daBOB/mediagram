package ui

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** Preloads stays in the overflow menu while a title is held on this device, queue or no queue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class PreloadsHeldMenuFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    private fun open(heldIds: Set<String>) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture(heldIds = heldIds)
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Menu").performClick()
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
    fun preloadsShowsWhenSomethingIsHeldButNothingQueued() {
        open(setOf("film-1"))
        // No queue, so no number beside it.
        compose.onNodeWithText("Preloads").performClick()

        compose.onNodeWithText("Example Film").assertExists()
    }

    @Test
    fun preloadsHiddenWhenNothingQueuedOrHeld() {
        open(emptySet())
        assertTrue(compose.onAllNodesWithText("Preloads", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
