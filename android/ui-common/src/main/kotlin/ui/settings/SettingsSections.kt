package ui.settings

/**
 * Settings' five sections, one per index row and one per page: a title for
 * the row and the page head alike, and the small sentence under the page's
 * own huge title — the same per-section line the web's Settings/System pages
 * carry (`settings-page.js`, `status-view.js:85-113`).
 *
 * Shared between `:ui-mobile` and the television's own two-pane Settings,
 * which is why this lives in `:ui-common` rather than beside either surface's
 * own screens.
 */
enum class SettingsSection(val title: String, val pageEyebrow: String) {
    TELEGRAM("Telegram", "Your account and where it's signed in"),
    STORAGE("Storage", "What this device keeps and where it comes from"),
    APPEARANCE("Appearance", "Make it yours"),
    PROFILE("Profile", "Who is watching and how"),
    SYSTEM("System", "What this player is doing, refreshed as it happens"),
}

/**
 * One index row's own one-line status — [text] under the section's name, and
 * whether to lead it with the held-colour dot a live, good-news connection
 * earns (Storage's "home cache connected", the ledger's own "held" mark).
 */
data class IndexStatus(val text: String, val held: Boolean = false)
