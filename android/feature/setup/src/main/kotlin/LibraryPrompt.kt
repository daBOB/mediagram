package setup

internal const val NOTHING_TO_CHOOSE =
    "This account cannot see any channels. " +
        "Join the channel you upload to, then look again."

/** What the picker has to put on screen, given how far the step has got. */
sealed interface LibraryPrompt {
    /** Fetching the list, or installing a chosen one. Nothing to do but wait. */
    data object Waiting : LibraryPrompt

    /** Pick one of these. */
    data class Choose(
        val choices: List<LibraryOption>,
    ) : LibraryPrompt

    /**
     * Nothing to pick — the list never arrived, or the account is in no
     * channels yet. Both are answered by asking once more, not by starting
     * the whole setup over, so this is the state that carries that offer.
     */
    data class LookAgain(
        val explanation: String,
    ) : LibraryPrompt
}

/**
 * A core error is shown as it was written: those sentences name what is
 * wrong with the channel and what fixes it, which is more than this screen
 * knows. Only the "no channels at all" case has no such sentence, because
 * nothing failed.
 */
fun libraryPromptFor(
    choices: List<LibraryOption>?,
    error: String?,
): LibraryPrompt =
    when {
        choices == null && error == null -> LibraryPrompt.Waiting
        choices.isNullOrEmpty() -> LibraryPrompt.LookAgain(error ?: NOTHING_TO_CHOOSE)
        else -> LibraryPrompt.Choose(choices)
    }
