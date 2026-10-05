package ui.tv.player

import playback.PlaybackTotals
import player.PlayerChoices
import player.PlayerMarksState
import player.UpNextUiState

/** What the controls show beyond the player's own state, and what pressing them does. */
internal class TvPlayerExtras(
    val marks: PlayerMarksState?,
    val markActions: TvMarksActions,
    val statsShown: Boolean,
    val onToggleStats: () -> Unit,
    val totals: () -> PlaybackTotals,
    /** Whether this device holds the title in full, which the statistics' buffer row reports as "cached". */
    val held: Boolean = false,
    /** What the tools read and change: subtitles, speed, audio and framing. */
    val choices: PlayerChoices = PlayerChoices.Default,
    val onToggleSubtitles: () -> Unit = {},
    val onOpenMenu: (TvCardMenu) -> Unit = {},
    /** The run as ⏮ and ⏭ walk it, and whether the up-next card floats above the card. */
    val upNext: UpNextUiState = UpNextUiState(),
    val onRestart: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
    /** Opens the episode list; null with no list to open, which leaves ☰ out. */
    val onOpenEpisodes: (() -> Unit)? = null,
    /** Opens and closes the notes column; null while the title has none, which leaves the Notes button out. */
    val onToggleNotes: (() -> Unit)? = null,
    /** Whether the episode list is open down the right, which the card then stands clear of. */
    val sidebarOpen: Boolean = false,
)
