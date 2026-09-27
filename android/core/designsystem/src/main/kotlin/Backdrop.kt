package designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Settings › Appearance's fourth question: how a hero (a cover, a title
 * spread, a department page) treats its own picture — the same four
 * options, at the same ids, labels and notes, as `lib/catalog/settings-page.js`'s
 * `BACKDROPS` on the web.
 */
enum class Backdrop(val storageKey: String, val label: String, val note: String) {
    DEFAULT("default", "Default", "Artwork fades into the page"),
    BLURRED("blurred", "Blurred", "Colour and light, softened"),
    ARTWORK("artwork", "Artwork", "The picture behind the words"),
    SOLID("solid", "Solid", "Plain pages, no artwork"),
    ;

    companion object {
        val Default = DEFAULT

        /** Anything unrecognised — a later version's value, a cleared preference — is Default, as on the web. */
        fun fromStorageKey(key: String?): Backdrop = entries.find { it.storageKey == key } ?: Default
    }
}

/**
 * The backdrop every hero on this device draws under, set once by
 * [MediagramTheme]/`ui.tv.TvTheme` from [Appearance.backdrop] rather than
 * each hero collecting the settings `StateFlow` on its own. Settings is the
 * only writer of this value, so the full recomposition a
 * `staticCompositionLocalOf` triggers on change is correct and cheap here.
 */
val LocalBackdrop = staticCompositionLocalOf { Backdrop.Default }
