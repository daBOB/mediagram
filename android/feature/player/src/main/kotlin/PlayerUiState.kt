package player

/**
 * What the player screen renders; the television surface renders the same
 * states.
 *
 * No position here. Where the playhead is, is a fact the player keeps and the
 * surfaces read from it directly through media3's state holders — a second
 * copy carried through this state could only ever be the same number, later,
 * or a different one, wrongly.
 */
sealed interface PlayerUiState {
    data object Preparing : PlayerUiState

    data object Playing : PlayerUiState

    data object Paused : PlayerUiState

    data class Failed(
        val message: String,
    ) : PlayerUiState
}
