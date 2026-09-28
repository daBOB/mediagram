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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.FilmPreloadRow
import kotlin.test.assertTrue

/**
 * The overflow menu's own "Preloads · n" row, over the real
 * [LibraryFlowFixture]/[LibraryFlowTestActivity] route [LibraryFlowTest]
 * already walks for every other menu item: absent while the engine's own
 * queue is empty, present with a live count once it is not, and opening it
 * reaches the real [PreloadsFrame]/[ui.catalog.PreloadsScreen].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class PreloadsMenuFlowTest {
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

    private fun openMenu() = compose.onNodeWithContentDescription("Menu").performClick()

    @Test
    fun theEntryIsAbsentWhileNothingIsRunningOrQueued() {
        openMenu()
        assertTrue(compose.onAllNodesWithText("Preloads", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theEntryNamesTheCountAndOpensTheRealPreloadsPage() {
        fixture.filmPreloading.setQueueOverview(
            listOf(FilmPreloadRow.Running("film-1", "Example Film", TOTAL, heldBytes = HELD_40_PERCENT, pauseReason = null)),
        )
        compose.waitForIdle()
        openMenu()
        compose.onNodeWithText("Preloads · 1").performClick()

        compose.onNodeWithText("Example Film").assertExists()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertExists()

        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Menu").assertExists()
    }

    /** A film this profile's own catalogue cannot resolve must not surface in the count or the page — the same rule a kids profile relies on to never learn a grown-up's own preload. */
    @Test
    fun aFilmNotInThisCatalogueDoesNotCountOrShow() {
        fixture.filmPreloading.setQueueOverview(listOf(FilmPreloadRow.Waiting("not-in-catalogue", "Somewhere Else", TOTAL)))
        compose.waitForIdle()
        openMenu()
        assertTrue(compose.onAllNodesWithText("Preloads", substring = true).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TOTAL = 5 * 1_073_741_824L
        const val HELD_40_PERCENT = TOTAL * 40 / 100
    }
}
