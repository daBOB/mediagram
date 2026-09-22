package designsystem

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
 * [Figures], [Ochre] and [Sage] clear 4.5:1 on each. [Imprint] is the web
 * player's `#8c3b2e` lifted until it did the same, because it lands on the
 * line saying where a viewer got to, which is small text and not
 * decoration.
 */
internal object Palette {
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

    /** One accent, for the thing this viewer is in the middle of. */
    val Imprint = Color(0xFFD26A55)

    /** The one thing the catalogue ever warns about. */
    val Ochre = Color(0xFFC99A3F)

    /** The only good news it has: something already held on the device. */
    val Sage = Color(0xFF8AA17A)
}
