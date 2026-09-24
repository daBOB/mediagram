package player

/**
 * What [UpNextController] publishes for the up-next card and the standing
 * "Play next" button to render — the two of them read the same state
 * because the button never withdraws itself on cancel while the card does.
 */
data class UpNextUiState(
    val phase: UpNextPhase = UpNextPhase.HIDDEN,
    /** The next title's own line (`titleLine`); blank until it resolves. */
    val titleLine: String = "",
    /** Seconds left in the countdown; `null` outside [UpNextPhase.COUNTING]. */
    val countdownSecondsLeft: Int? = null,
    /** Whether a next title exists at all — true even once [phase] is hidden by a cancel. */
    val hasNext: Boolean = false,
    /**
     * An unattended switch is under way: the countdown has run out and the
     * player is paused on the next title waiting on the autoplay gate.
     * Kept apart from [phase] — the switch itself changes which title
     * [phase] describes, but the screen must stay awake through both.
     */
    val awaitingStart: Boolean = false,
)

/** The next title to open, and the run it belongs to — [UpNextController] asks the UI layer to navigate there; see `PlayerScreen`'s own `pendingSwitch` effect. */
data class PendingPlayerSwitch(val setId: String, val run: List<String>)
