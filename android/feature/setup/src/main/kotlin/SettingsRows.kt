package setup

/** The Telegram block's rows, pure so a test can pin them. "…" is a row still being asked. */
fun telegramRows(state: SettingsUiState): List<Pair<String, String?>> =
    listOf(
        "Account" to (state.account ?: "…"),
        "Library" to (state.library ?: "…"),
        "Datacenter" to (state.datacenter ?: "…"),
        "Session" to (state.connection ?: "…"),
    )
