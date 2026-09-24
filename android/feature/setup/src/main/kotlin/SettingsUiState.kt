package setup

/**
 * What the Settings screen's Telegram section shows.
 *
 * Each row is `null` while it is still being asked and a sentence once it
 * is known — including "—" for an answer that could not be had, so a row
 * never sits empty with no way to tell waiting from failing.
 */
data class SettingsUiState(
    /** "Name (@username)", or "—" when Telegram did not answer. */
    val account: String? = null,
    /** The chosen library's title, as the account lists it. */
    val library: String? = null,
    /** The datacentre this login lives on, from the stored key. */
    val datacenter: String? = null,
    /** Whether the last question to Telegram was answered. */
    val connection: String? = null,
    /** The application id in use, to prefill its form; the hash is never shown. */
    val apiId: Int? = null,
    /** The account's libraries, once asked for to change the choice. */
    val choices: List<LibraryOption>? = null,
    /** Something is being done; actions wait for it. */
    val busy: Boolean = false,
    /** What the last action came to, when there is something to say. */
    val notice: String? = null,
    /** A replacement retired the previous core; its watch-state owner still needs a successful read. */
    val profileReloadNeeded: Boolean = false,
    /** Monotonic success marker: a form opened before this action can close. */
    val completedActionId: Long = 0,
)

/** Retained until the library screen has acted on it, including across collector gaps. */
data class SettingsCompletion(
    val id: Long,
    val event: SettingsEvent,
)

/** What happened that a screen outside this one must act on. */
enum class SettingsEvent {
    /** A different library was installed: the shelves read it again. */
    LibraryChanged,

    /** A new application identity was accepted: its form can close. */
    ApplicationChanged,

    /** This device signed out: setup takes over from sign-in. */
    SignedOut,
}
