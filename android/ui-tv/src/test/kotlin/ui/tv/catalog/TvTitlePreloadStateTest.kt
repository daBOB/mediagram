package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.FilmPreloadState
import playback.PauseReason
import kotlin.test.assertTrue

/**
 * [TvTitlePage]'s own Preload control: a plate beside Play carrying the
 * same [FilmPreloadState] labels the phone shows, D-pad right from Play
 * reaches it, OK toggles it, and Done offers a second Remove plate.
 * Android-only by decision — the web player has no film preload.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTitlePreloadStateTest : TvScreenStateTest() {
    private val film = set("f", Kind.MOVIE, "A Film", addedAt = 0, year = 2004, durationSecs = 6780).copy(totalBytes = TOTAL)

    private fun ui(
        state: FilmPreloadState,
        serverLine: String? = null,
        onToggle: () -> Unit = {},
        onRemove: () -> Unit = {},
        onOpenStorage: () -> Unit = {},
    ) = TvTitlePreloadUi(state, serverLine, onToggle, onRemove, onOpenStorage)

    @Test
    fun withNoPreloadWiredPlayStillTakesFocusAlone() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}) }
        compose.onNodeWithText("▶ Play").assertIsFocused()
    }

    @Test
    fun anIdlePlateSitsBesidePlayAndRightMovesTheRemoteToIt() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, preload = ui(FilmPreloadState.Idle(0L, TOTAL))) }
        compose.onNodeWithText("▶ Play").assertIsFocused()
        compose.onNodeWithText("▶ Play").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Preload · 5.0 GB").assertIsFocused()
    }

    @Test
    fun okOnTheIdlePlateStartsThePreload() {
        var toggled = false
        show {
            TvTitlePage(set = film, info = null, progress = null, onPlay = {}, preload = ui(FilmPreloadState.Idle(0L, TOTAL), onToggle = { toggled = true }))
        }
        compose.onNodeWithText("Preload · 5.0 GB").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(toggled)
    }

    @Test
    fun runningShowsThePlateAndTheNumericLineAndOkCancels() {
        var toggled = false
        show {
            TvTitlePage(
                set = film, info = null, progress = null, onPlay = {},
                preload = ui(FilmPreloadState.Running(HELD_40_PERCENT, TOTAL), onToggle = { toggled = true }),
            )
        }
        compose.onNodeWithText("Preloading").assertExists()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertExists()
        compose.onNodeWithText("Preloading").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(toggled)
    }

    @Test
    fun runningShowsTheBarAtItsOwnFraction() {
        show {
            TvTitlePage(
                set = film, info = null, progress = null, onPlay = {},
                preload = ui(FilmPreloadState.Running(HELD_40_PERCENT, TOTAL)),
            )
        }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f)))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))
    }

    @Test
    fun pausedWhilePlayingKeepsTheNumericLineUnderItsOwnPlateLabel() {
        show {
            TvTitlePage(
                set = film, info = null, progress = null, onPlay = {},
                preload = ui(FilmPreloadState.Paused(HELD_40_PERCENT, TOTAL, PauseReason.Playing)),
            )
        }
        compose.onNodeWithText("Paused while playing").assertExists()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertExists()
    }

    @Test
    fun doneOffersARemovePlateRightOfTheMainOneAndOkRemoves() {
        var removed = false
        show {
            TvTitlePage(set = film, info = null, progress = null, onPlay = {}, preload = ui(FilmPreloadState.Done, onRemove = { removed = true }))
        }
        compose.onNodeWithText("Preloaded ✓").assertExists()
        compose.onNodeWithText("▶ Play").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Preloaded ✓").assertIsFocused()
        compose.onNodeWithText("Preloaded ✓").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Remove preload").assertIsFocused()
        compose.onNodeWithText("Remove preload").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(removed)
        // Left takes back to the main plate, not the Overview tab a plain
        // focus-search would have landed on once Remove tore itself down.
        compose.onNodeWithText("Preloaded ✓").assertIsFocused()
    }

    @Test
    fun needsSpaceOffersTheStorageRowAndItsOwnPlateRetries() {
        var openedStorage = false
        var toggled = false
        show {
            TvTitlePage(
                set = film, info = null, progress = null, onPlay = {},
                preload = ui(FilmPreloadState.NeedsSpace(TOTAL), onOpenStorage = { openedStorage = true }, onToggle = { toggled = true }),
            )
        }
        compose.onNodeWithText("Needs 5.0 GB · Try again").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(toggled)
        compose.onNodeWithText("Raise the cache budget").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(openedStorage)
    }

    @Test
    fun theServerLineShowsWhenPresent() {
        show {
            TvTitlePage(
                set = film, info = null, progress = null, onPlay = {},
                preload = ui(FilmPreloadState.Idle(0L, TOTAL), serverLine = "Home server: 5.0 of 5.0 GB"),
            )
        }
        compose.onNodeWithText("Home server: 5.0 of 5.0 GB").assertExists()
    }

    private companion object {
        /** 5 * 1024^3 — prints as "5.0 GB" through [model.humanSize]. */
        const val TOTAL = 5 * 1_073_741_824L
        const val HELD_40_PERCENT = TOTAL * 40 / 100
    }
}
