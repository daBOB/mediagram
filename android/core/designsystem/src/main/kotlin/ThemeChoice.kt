package designsystem

/**
 * Settings › Appearance's theme choice: Dark, Light, or Auto (follow the
 * device's own system theme) — the same three
 * `lib/catalog/settings-page.js` offers on the web.
 */
enum class ThemeChoice(val storageKey: String) {
    DARK("dark"),
    LIGHT("light"),
    AUTO("auto"),
    ;

    companion object {
        val Default = AUTO

        /** Anything unrecognised — a later version's value, a cleared preference — is Auto, as on the web. */
        fun fromStorageKey(key: String?): ThemeChoice = entries.find { it.storageKey == key } ?: Default
    }
}
