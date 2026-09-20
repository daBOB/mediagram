package setup

/**
 * Which of the three first-run questions is still outstanding.
 *
 * Derived every time from what is actually stored and what the core
 * actually reports — never from a remembered position in the flow. A
 * counter would be a second source of truth, and the two drift the moment
 * Telegram invalidates a session from somewhere else: the counter would
 * still say "done" while the core says "not signed in". Recomputing has no
 * such failure, and it is also what makes the flow resumable, because a
 * process killed halfway through leaves nothing to remember.
 */
sealed interface SetupUiState {

    /** Storage has not answered yet — distinct from having answered "nothing stored". */
    data object Checking : SetupUiState

    /** Step one: the Telegram application identity, from my.telegram.org. */
    data class NeedsApplication(val error: String? = null) : SetupUiState

    /** Step two: phone, code and any two-factor password, through the login flow. */
    data object NeedsSignIn : SetupUiState

    /** Step three: the package address and the key that decrypts it. */
    data class NeedsLibrary(val error: String? = null) : SetupUiState

    /** Everything is stored and the session is live; the catalog can open. */
    data object Ready : SetupUiState

    /**
     * Storage itself would not answer.
     *
     * Its own state rather than a variant of a step, because there is no
     * step to be on: the keystore this app's secrets live behind throws
     * after a backup restore onto another device or when the key behind it
     * has been invalidated, and every question the flow asks goes through
     * it. Without this the app shows a spinner for ever, or crashes on
     * every launch. The way out is starting over, which is why that offer
     * travels with the message.
     */
    data class Failed(val message: String) : SetupUiState
}
