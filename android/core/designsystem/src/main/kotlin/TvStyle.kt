package designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Settings › Appearance's television question: which of three looks the TV
 * draws in — mediagram's own magazine, or a look after Apple TV's or Google
 * TV's home screens. A property of this screen like the rest of
 * [Appearance]; the phone and tablet store it and ignore it until they get
 * themes of their own.
 */
enum class TvStyle(val storageKey: String, val label: String, val note: String) {
    MEDIAGRAM("mediagram", "mediagram", "Serif covers, posters, the rail"),
    APPLE("apple", "Apple TV style", "Black, bold titles, wide cards"),
    GOOGLE("google", "Google TV style", "Grey, rounded cards, a ring on focus"),
    ;

    companion object {
        val Default = MEDIAGRAM

        /** Anything unrecognised — a later version's value, a cleared preference — is Default, as [Backdrop] reads. */
        fun fromStorageKey(key: String?): TvStyle = entries.find { it.storageKey == key } ?: Default
    }
}

/**
 * The style every TV screen draws in, set once by `ui.tv.TvTheme` from
 * [Appearance.style] — static for [LocalBackdrop]'s reason: Settings is its
 * only writer, so the full recomposition a change triggers is correct.
 */
val LocalTvStyle = staticCompositionLocalOf { TvStyle.Default }
