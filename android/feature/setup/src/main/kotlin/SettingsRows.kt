package setup

/** The Telegram block's rows, pure so a test can pin them. "…" is a row still being asked. */
fun telegramRows(state: SettingsUiState): List<Pair<String, String?>> =
    listOf(
        "Account" to (state.account ?: "…"),
        "Library" to (state.library ?: "…"),
        "Datacenter" to (state.datacenter ?: "…"),
        "Session" to (state.connection ?: "…"),
    )

/**
 * The Telegram index row's one-line status: who is signed in, or that
 * nobody is — read off the same round trip [telegramRows]' own "Account" row
 * reads, not a second question.
 */
fun telegramStatus(state: SettingsUiState): String {
    val account = state.account
    return when {
        state.connection == null -> "Reading…"
        // "—" is the same placeholder readRows() falls back to when
        // Telegram did not answer — see SettingsViewModel's own UNKNOWN.
        account == null || account == "—" -> "Not signed in"
        else -> "$account · signed in"
    }
}
