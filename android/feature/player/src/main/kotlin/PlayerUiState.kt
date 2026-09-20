package player

/** What the player screen renders; the television surface renders the same states. */
sealed interface PlayerUiState {
    data object Preparing : PlayerUiState
    data class Playing(val positionMs: Long, val durationMs: Long) : PlayerUiState
    data class Paused(val positionMs: Long, val durationMs: Long) : PlayerUiState
    data class Failed(val message: String) : PlayerUiState
}
