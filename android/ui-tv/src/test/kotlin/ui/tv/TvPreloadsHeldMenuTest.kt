package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.films

/** Preloads stays in the TV menu while a title is held on this device, queue or no queue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPreloadsHeldMenuTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    private fun openMenu(heldIds: Set<String>) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2), heldIds = heldIds)
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Menu, watching as Ada").performSemanticsAction(SemanticsActions.OnClick)
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

    @Test
    fun preloadsShowsWhenSomethingIsHeldButNothingQueued() {
        openMenu(setOf("film-0"))
        // No queue, so no number beside it.
        compose.onNodeWithText("Preloads").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText("Film 0").assertExists()
    }

    @Test
    fun preloadsHiddenWhenNothingQueuedOrHeld() {
        openMenu(emptySet())
        compose.onNode(hasText("Preloads", substring = true)).assertDoesNotExist()
    }
}
