package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import playback.FilmPreloadRow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PreloadsScreen]: what is preloading, queued, or already fully on this
 * device. Android-only — the web player has no film preload. Exercised
 * directly with plain data, the same shape [TitlePreloadTest] already
 * builds [ui.catalog.TitleDetailScreen] with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreloadsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun held(id: String, title: String) =
        MediaSet(
            setId = id, kind = Kind.MOVIE, title = title, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = TOTAL,
        )

    @Test
    fun withNothingAtAllTheEmptyLineShowsAndNoSectionsAreOffered() {
        show { PreloadsScreen(rows = emptyList(), heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("Nothing is preloading right now.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Preloading").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Queued").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("On this device").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theRunningFilmShowsItsBarAndOpensOnATapAndCancelsFromItsOwnButton() {
        var opened: String? = null
        var cancelled: String? = null
        val row = FilmPreloadRow.Running("f1", "Der Pate", TOTAL, heldBytes = HELD_40_PERCENT, pauseReason = null)
        show {
            PreloadsScreen(
                rows = listOf(row), heldFilms = emptyList(),
                onOpenTitle = { opened = it }, onCancel = { cancelled = it }, onRemove = {}, onResume = {},
            )
        }
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertIsDisplayed()
        compose.onNodeWithText("Der Pate").performClick()
        assertEquals("f1", opened)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("f1", cancelled)
    }

    @Test
    fun aQueuedFilmShowsInItsOwnSectionAndCancelsFromItsOwnButton() {
        var cancelled: String? = null
        val rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL))
        show { PreloadsScreen(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = { cancelled = it }, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("Queued").assertIsDisplayed()
        compose.onNodeWithText("The Green Mile").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("f2", cancelled)
    }

    @Test
    fun aHeldFilmShowsUnderOnThisDeviceAndOpensAndRemovesFromItsOwnButton() {
        var opened: String? = null
        var removed: String? = null
        show {
            PreloadsScreen(
                rows = emptyList(), heldFilms = listOf(held("held-1", "Chihiros Reise ins Zauberland")),
                onOpenTitle = { opened = it }, onCancel = {}, onRemove = { removed = it }, onResume = {},
            )
        }
        compose.onNodeWithText("On this device").assertIsDisplayed()
        compose.onNodeWithText("Chihiros Reise ins Zauberland").performClick()
        assertEquals("held-1", opened)
        compose.onNodeWithText("Remove").performClick()
        assertEquals("held-1", removed)
    }

    @Test
    fun onlyNonEmptySectionsAreOffered() {
        val rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL))
        show { PreloadsScreen(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("Queued").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Preloading").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("On this device").fetchSemanticsNodes().isEmpty())
    }

    /** The user decision: a film the background time limit paused stays under Preloading (it was the one writing), named plainly, with one Resume action rather than Cancel. */
    @Test
    fun aTimeLimitPausedFilmThatWasActiveStaysUnderPreloadingWithResume() {
        var resumed: FilmPreloadRow.TimeLimitPaused? = null
        val row = FilmPreloadRow.TimeLimitPaused("f1", "Der Pate", TOTAL, heldBytes = HELD_40_PERCENT, wasActive = true)
        show { PreloadsScreen(rows = listOf(row), heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = { resumed = it }) }
        compose.onNodeWithText("Preloading").assertIsDisplayed()
        compose.onNodeWithText("Paused — background limit").assertIsDisplayed()
        compose.onNodeWithText("Resume").performClick()
        assertEquals(row, resumed)
    }

    /** As above, for a film that was merely queued when the pause hit — it stays under Queued. */
    @Test
    fun aTimeLimitPausedFilmThatWasQueuedStaysUnderQueuedWithResume() {
        var resumed: FilmPreloadRow.TimeLimitPaused? = null
        val row = FilmPreloadRow.TimeLimitPaused("f2", "The Green Mile", TOTAL, heldBytes = 0L, wasActive = false)
        show { PreloadsScreen(rows = listOf(row), heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = { resumed = it }) }
        compose.onNodeWithText("Queued").assertIsDisplayed()
        compose.onNodeWithText("Resume").performClick()
        assertEquals(row, resumed)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun onATabletEverySectionStillRenders() {
        val rows =
            listOf(
                FilmPreloadRow.Running("f1", "Der Pate", TOTAL, heldBytes = HELD_40_PERCENT, pauseReason = null),
                FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL),
            )
        show {
            PreloadsScreen(
                rows = rows, heldFilms = listOf(held("held-1", "Chihiros Reise ins Zauberland")),
                onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {},
            )
        }
        compose.onNodeWithText("Der Pate").assertIsDisplayed()
        compose.onNodeWithText("The Green Mile").assertIsDisplayed()
        compose.onNodeWithText("Chihiros Reise ins Zauberland").assertIsDisplayed()
    }

    private companion object {
        /** 5 * 1024^3 — prints as "5.0 GB" through [model.humanSize]. */
        const val TOTAL = 5 * 1_073_741_824L
        const val HELD_40_PERCENT = TOTAL * 40 / 100
    }
}
