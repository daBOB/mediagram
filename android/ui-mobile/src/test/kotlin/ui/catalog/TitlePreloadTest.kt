package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import playback.FilmPreloadState
import playback.PauseReason
import ui.common.catalog.TitlePreloadUi
import kotlin.test.assertTrue

/**
 * A film's own Preload control, exercised through [TitleDetailScreen]:
 * every [FilmPreloadState] reads back as its own label, a tap reaches the
 * right callback, the bar stays up through a pause, and the ⋯ menu's
 * Remove/needs-space link only appear when the state calls for them.
 * Android-only by decision — the web player has no film preload.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TitlePreloadTest {
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

    private val film =
        MediaSet(
            setId = "film-1", kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = TOTAL,
        )

    private fun ui(
        state: FilmPreloadState,
        serverLine: String? = null,
        onToggle: () -> Unit = {},
        onRemove: () -> Unit = {},
        onOpenStorage: () -> Unit = {},
        queuedAheadLabel: String? = null,
        needsSpaceBudgetBytes: Long? = null,
    ) = TitlePreloadUi(state, serverLine, onToggle, onRemove, onOpenStorage, queuedAheadLabel, needsSpaceBudgetBytes)

    @Test
    fun idleWithNothingHeldNamesTheFilmsSize() {
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Idle(0L, TOTAL))) }
        compose.onNodeWithText("Preload · 5.0 GB").assertIsDisplayed()
    }

    @Test
    fun idleWithSomeHeldNamesThePercentage() {
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Idle(HELD_35_PERCENT, TOTAL))) }
        compose.onNodeWithText("Preload · 35% held").assertIsDisplayed()
    }

    @Test
    fun tappingIdleStartsThePreload() {
        var toggled = false
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Idle(0L, TOTAL), onToggle = { toggled = true })) }
        compose.onNodeWithText("Preload · 5.0 GB").performClick()
        assertTrue(toggled)
    }

    @Test
    fun queuedTapCancels() {
        var toggled = false
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Queued, onToggle = { toggled = true })) }
        compose.onNodeWithText("Queued").performClick()
        assertTrue(toggled)
    }

    @Test
    fun runningShowsTheBarAndTapCancels() {
        var toggled = false
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Running(HELD_40_PERCENT, TOTAL), onToggle = { toggled = true }),
            )
        }
        compose.onNodeWithText("Preloading").assertIsDisplayed()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertIsDisplayed()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").performClick()
        assertTrue(toggled)
    }

    @Test
    fun pausedWhilePlayingKeepsTheBarUpUnderItsOwnPillLabel() {
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Paused(HELD_40_PERCENT, TOTAL, PauseReason.Playing)),
            )
        }
        compose.onNodeWithText("Paused while playing").assertIsDisplayed()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertIsDisplayed()
    }

    @Test
    fun waitingForWifiIsTheMeteredPausesOwnLabel() {
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Paused(HELD_40_PERCENT, TOTAL, PauseReason.Metered)),
            )
        }
        compose.onNodeWithText("Waiting for Wi-Fi").assertIsDisplayed()
    }

    @Test
    fun aTimeLimitPauseTogglesEnqueueRatherThanCancel() {
        var toggled = false
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Paused(HELD_40_PERCENT, TOTAL, PauseReason.TimeLimit), onToggle = { toggled = true }),
            )
        }
        compose.onNodeWithText("Paused (background limit reached)").performClick()
        assertTrue(toggled)
    }

    @Test
    fun doneOffersRemoveInTheOverflowMenu() {
        var removed = false
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Done, onRemove = { removed = true })) }
        compose.onNodeWithText("Preloaded ✓").assertIsDisplayed()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Remove preload").performClick()
        assertTrue(removed)
    }

    /** No preload at all (no menu item, no editor's-choice callback) leaves the ⋯ menu unoffered, as it always has. */
    @Test
    fun noMenuAtAllWithNeitherEditorsChoiceNorARemovablePreload() {
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}) }
        assertTrue(compose.onAllNodesWithContentDescription("More").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun queuedNamesWhatItIsWaitingOnWhenTheViewModelSaysSo() {
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Queued, queuedAheadLabel = "Queued · after Der Pate, 36%"),
            )
        }
        compose.onNodeWithText("Queued · after Der Pate, 36%").assertIsDisplayed()
    }

    @Test
    fun needsSpaceNamesTheLiveBudgetWhenTheViewModelSaysSo() {
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.NeedsSpace(TOTAL), needsSpaceBudgetBytes = 8L shl 30),
            )
        }
        compose.onNodeWithText("Needs 5.0 GB · budget is 8.0 GB · Try again").assertIsDisplayed()
    }

    @Test
    fun needsSpaceOffersTheStorageLinkAndItsOwnPillRetries() {
        var openedStorage = false
        var toggled = false
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.NeedsSpace(TOTAL), onOpenStorage = { openedStorage = true }, onToggle = { toggled = true }),
            )
        }
        // Retryable, not a dead end once the viewer raises the budget elsewhere.
        compose.onNodeWithText("Needs 5.0 GB · Try again").performClick()
        assertTrue(toggled)
        compose.onNodeWithText("Raise the cache budget").performClick()
        assertTrue(openedStorage)
    }

    @Test
    fun failedShowsAShortReasonWithRetryAndTapRetries() {
        var toggled = false
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Failed("Could not preload this film"), onToggle = { toggled = true }),
            )
        }
        compose.onNodeWithText("Could not preload this film · Retry").performClick()
        assertTrue(toggled)
    }

    @Test
    fun theServerLineIsHiddenWhenNull() {
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Idle(0L, TOTAL), serverLine = null)) }
        assertTrue(compose.onAllNodesWithText("Home server", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theServerLineShowsUnderTheButtonsWhenIdle() {
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                preload = ui(FilmPreloadState.Idle(0L, TOTAL), serverLine = "Home server: 5.0 of 5.0 GB"),
            )
        }
        compose.onNodeWithText("Home server: 5.0 of 5.0 GB").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun onATabletThePillAndBarStillRenderBesidePlay() {
        show { TitleDetailScreen(film, null, {}, onOpenGenre = {}, preload = ui(FilmPreloadState.Running(HELD_40_PERCENT, TOTAL))) }
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        compose.onNodeWithText("Preloading").assertIsDisplayed()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertIsDisplayed()
    }

    private companion object {
        /** 5 * 1024^3 — prints as "5.0 GB" through [model.humanSize]. */
        const val TOTAL = 5 * 1_073_741_824L
        const val HELD_35_PERCENT = TOTAL * 35 / 100
        const val HELD_40_PERCENT = TOTAL * 40 / 100
    }
}
