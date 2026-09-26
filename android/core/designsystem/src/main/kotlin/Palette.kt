package designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The catalogue's ink, for a surface that is read in the dark.
 *
 * The web player sets the same catalogue on uncoated paper and keeps only
 * its player black, because a poster reads best against a page and a
 * picture reads best against nothing. A phone or a tablet is held in the
 * room the film is about to play in, and a paper-white slab there is a
 * lamp. So the stock is inverted rather than reproduced: the same hues,
 * the same restraint, the page in ink.
 *
 * Every value that carries text was measured against both grounds.
 * [Figures], [Ochre] and [Sage] clear 4.5:1 on each.
 *
 * Public so every surface reads it, not just the phone's M3 theme. A
 * television theme can't build a `ColorScheme` from this at all — it never
 * has material3 on its compile classpath — but it still needs these exact
 * values to build its own `darkColorScheme`, and a hex retyped into a
 * second file is a hex that can drift from this one unnoticed.
 */
object Palette {
    /** The deepest ground: what the window is cleared to. */
    val Ground = Color(0xFF16130F)

    /** The page the plates sit on, one step up from the ground. */
    val Page = Color(0xFF1E1B16)

    /** Where artwork is missing and the page itself shows through. */
    val Sunk = Color(0xFF272319)

    /** Paper, at the weight a dark page carries without glare. */
    val Text = Color(0xFFE8E2D4)

    /** Runtimes, sizes and counts: present, and quieter than a title. */
    val Figures = Color(0xFFA89B84)

    /** A hairline. Structure, never a boundary anyone has to look at. */
    val Rule = Color(0xFF3A342A)

    /** Visible enough to read as an edge where one is doing work. */
    val RuleStrong = Color(0xFF574E3E)

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

    /** The one thing the catalogue ever warns about. */
    val Ochre = Color(0xFFC99A3F)

    /** The only good news it has: something already held on the device. */
    val Sage = Color(0xFF8AA17A)

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
}
