package designsystem

import androidx.compose.ui.graphics.Color

/**
 * The seven accent choices Settings › Appearance offers, at the web's own
 * per-theme hex values (`styles/appearance.css`) rather than approximated
 * for Android: an accent is a colour a viewer picked by eye, and this
 * catalogue re-deriving it would be a second colour behind the same name.
 * [CORAL] is the accent every surface drew before this setting existed —
 * [Palette.Imprint]'s own default — so a device that never opens Settings
 * looks unchanged.
 */
enum class Accent(
    val storageKey: String,
    val dark: Color,
    val light: Color,
) {
    CORAL("coral", Color(0xFFE57A61), Color(0xFFA3392A)),
    BLUE("blue", Color(0xFF7CB4F0), Color(0xFF2C5C9A)),
    VIOLET("violet", Color(0xFFB9A1F7), Color(0xFF6243A6)),
    TEAL("teal", Color(0xFF5EC9C0), Color(0xFF1D6A65)),
    GREEN("green", Color(0xFF93CF80), Color(0xFF3A6A2A)),
    AMBER("amber", Color(0xFFE8B058), Color(0xFF80530C)),
    ROSE("rose", Color(0xFFF08AA2), Color(0xFFA1304D)),
    ;

    /** This accent's value in the resolved theme: [dark] or [light]. */
    fun resolve(dark: Boolean): Color = if (dark) this.dark else light

    companion object {
        val Default = CORAL

        /** Anything unrecognised — a later version's value, a cleared preference — is coral, as on the web. */
        fun fromStorageKey(key: String?): Accent = entries.find { it.storageKey == key } ?: Default
    }
}
