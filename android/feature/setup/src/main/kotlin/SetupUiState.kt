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

    /**
     * Step three: which of this account's libraries the device reads.
     *
     * The two fields say between them where the step has got to, because
     * this is the one question that cannot be answered without asking
     * Telegram first:
     *
     * - no choices and no error — the list is being fetched, or a chosen
     *   library is being installed; either way there is nothing to do but
     *   wait.
     * - no choices and an error — the list could not be fetched, and the
     *   only way on is to ask again.
     * - choices — pick one. An error beside them explains why the last
     *   pick did not take, and the list stays on screen because picking
     *   again is exactly what the person should do next.
     */
    data class NeedsLibrary(
        val choices: List<LibraryOption>? = null,
        val error: String? = null,
    ) : SetupUiState

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

/**
 * Returns [previous] unchanged when it is the same step with something to
 * say — the error a person has not read yet, or the fetched list the
 * library step would otherwise have to ask for again.
 *
 * Beside the states it reads rather than inside the ViewModel that calls
 * it: it is a rule about which of two states wins, and nothing about it
 * needs the ViewModel's coroutines, its storage or its core.
 */
internal fun SetupUiState.keepingWhatIsOnScreenFrom(previous: SetupUiState): SetupUiState = when {
    this is SetupUiState.NeedsApplication && previous is SetupUiState.NeedsApplication -> previous
    this is SetupUiState.NeedsLibrary && previous is SetupUiState.NeedsLibrary -> previous
    else -> this
}
