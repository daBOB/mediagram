package designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The catalogue's ink, for a surface that is read in the dark.
 *
 * These are the web player's own dark-theme values, `styles/theme.css`
 * `:root`, verbatim — not approximated, not re-lit for Android. Before this
 * palette a phone or tablet ran a warmer near-black of its own, reasoned as
 * paper inverted rather than reproduced; that reasoning is gone, and so is
 * the divergence it caused. A viewer moving between the web player and this
 * app now meets one ground, not two.
 *
 * Every value that carries text was measured against Ground, Page, Sunk and
 * [Sidebar] — the Measured Colour Rule, held by `PaletteContrastTest`.
 *
 * Public so every surface reads it, not just the phone's M3 theme. A
 * television theme can't build a `ColorScheme` from this at all — it never
 * has material3 on its compile classpath — but it still needs these exact
 * values to build its own `darkColorScheme`, and a hex retyped into a
 * second file is a hex that can drift from this one unnoticed.
 */
object Palette {
    /** The deepest ground: what the window is cleared to. The web's `--paper`. */
    val Ground = Color(0xFF0D0D0E)

    /** The page the plates sit on, one step up from the ground. The web's `--surface`. */
    val Page = Color(0xFF151517)

    /** Where artwork is missing and the page itself shows through. The web's `--paper-sunk`. */
    val Sunk = Color(0xFF1B1B1D)

    /** Paper, at the weight a dark page carries without glare. The web's `--ink`. */
    val Text = Color(0xFFF3EFE7)

    /** Runtimes, sizes and counts: present, and quieter than a title. The web's `--ink-2`. */
    val Figures = Color(0xFFCBC5BA)

    /** A hairline. Structure, never a boundary anyone has to look at. The web's `--rule` (18% ink over the ground). */
    val Rule = Color(0x2EF3EFE7)

    /** Visible enough to read as an edge where one is doing work. The web's `--ink-3`. */
    val RuleStrong = Color(0xFF9C968B)

    /**
     * The rail's own ground, one step darker than [Ground] rather than a
     * step up from it — the web's `--sidebar`. A settings index sits on
     * this rather than on the page, the one surface this palette makes
     * darker than the window it opens in.
     */
    val Sidebar = Color(0xFF09090A)

    /**
     * A quieter hairline than [Rule], for structure that separates without
     * asking to be seen — the web's `--rule-soft` (8% ink over the ground).
     */
    val RuleSoft = Color(0x14F3EFE7)

    /**
     * One accent, for the thing this viewer is in the middle of — a
     * focused card's border on a television, a text cursor, the line
     * saying where a viewer got to. Settings › Appearance's [Accent]
     * replaced the fixed red this used to be with a choice, so this is now
     * that choice's current colour rather than a constant: [MediagramTheme]
     * and [ui.tv.TvTheme] (through `androidx.compose.runtime.SideEffect`,
     * the only place either is allowed to write it) set it to the resolved
     * accent on every composition, and everything below — a television's
     * focus border, a cursor — reads the same property rather than a value
     * retyped for each of them. A snapshot state, not a plain `var`,
     * because most of its readers are themselves inside a composition and
     * need to recompose the moment a viewer picks a different accent, not
     * on whatever this object's next unrelated read happens to be.
     * Defaults to coral's dark value, the constant this always was before
     * Appearance existed, so a device that has never opened Settings looks
     * exactly as it always did.
     */
    var Imprint: Color by mutableStateOf(Color(0xFFE57A61))

    /** The one thing the catalogue ever warns about. The web's `--warn`. */
    val Ochre = Color(0xFFE6C47F)

    /** The only good news it has: something already held on the device. The web's `--held`. */
    val Sage = Color(0xFFA3D3A4)

    /**
     * The light "paper" variant, chosen in Settings › Appearance as Light,
     * or Auto on a device whose system theme is light. Values are the
     * web's own light-theme roles (`styles/theme.css`'s `data-theme="light"`
     * block) verbatim: nothing here had an Android light theme to diverge
     * from before this setting existed, so the reference is exactly it.
     */
    val LightGround = Color(0xFFF4F0E8)

    /** The page the plates sit on, light theme — the web's `--surface`. */
    val LightPage = Color(0xFFFBF8F2)

    /** Where artwork is missing, light theme — the web's `--paper-sunk`. */
    val LightSunk = Color(0xFFE5DFD3)

    /** Ink on paper — the web's `--ink`. */
    val LightText = Color(0xFF1B1916)

    /** Runtimes, sizes and counts on paper — the web's `--ink-2`. */
    val LightFigures = Color(0xFF48433B)

    /** A hairline on paper: 20% ink, the same opacity the web's `--rule` uses. */
    val LightRule = Color(0x331B1916)

    /** Visible enough to read as an edge on paper — the web's `--ink-3`. */
    val LightRuleStrong = Color(0xFF676157)

    /** The one thing the catalogue ever warns about, on paper — the web's light `--warn`. */
    val LightOchre = Color(0xFF7A5510)

    /** Something already held on the device, on paper — the web's light `--held`. */
    val LightSage = Color(0xFF2F6A35)

    /** [Sidebar], on paper — the web's light `--sidebar`, one step *lighter* than [LightGround] rather than darker. */
    val LightSidebar = Color(0xFFEBE5D9)

    /** [RuleSoft], on paper — the web's light `--rule-soft` (9% ink over the paper). */
    val LightRuleSoft = Color(0x171B1916)
}
