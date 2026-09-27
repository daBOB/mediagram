package designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import com.mediagram.android.core.designsystem.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins each Fraunces role — [PageTitle]/[PageTitleCompact], [Display] and
 * [CoverTitle] — to the static font instance it actually resolves through,
 * so a regression like the one this guards never ships silently again: on
 * at least one device, `FontVariation.Settings` was ignored outright,
 * falling back to `fraunces.ttf`'s own registered default instance — `wght
 * 900`/`opsz 9`, its heaviest, most decorative cut — for every one of these
 * roles, not only [PageTitle]'s own `TextAutoSize.StepBased` re-measurement
 * pass (`PageHead`'s own doc comment) that first surfaced it. The fix ships
 * all four as static, pre-instanced files with no axis left to drop; this
 * test is what would have caught the regression before a device did.
 */
@OptIn(ExperimentalTextApi::class)
class PageTitleFontTest {
    private fun onlyFont(style: TextStyle): ResourceFont = (style.fontFamily as FontListFontFamily).single() as ResourceFont

    @Test
    fun pageTitleIsAStaticFontAtTheWideOpticalSize() {
        val font = onlyFont(PageTitle)
        assertEquals(R.font.fraunces_page_title, font.resId)
        assertEquals(FontWeight.Medium, font.weight)
        assertTrue(font.variationSettings.settings.isEmpty(), "a static font has no variation axis to lose mid-measurement")
    }

    @Test
    fun pageTitleCompactIsAlsoAStaticFontAtItsOwnNarrowerOpticalSize() {
        val font = onlyFont(PageTitleCompact)
        assertEquals(R.font.fraunces_page_title_compact, font.resId)
        assertEquals(FontWeight.Medium, font.weight)
        assertTrue(font.variationSettings.settings.isEmpty())
    }

    @Test
    fun displayIsAStaticPairAtItsTwoWeights() {
        // An on-device A/B (same string, same size, variable vs a static
        // cut at the same wght/opsz) showed Display dropped its own
        // FontVariation.Settings request too, through a plain Text with no
        // TextAutoSize involved — not only PageTitle's own re-measurement
        // pass. The fix is the same one: static, pre-instanced files.
        val fonts = (Display as FontListFontFamily).map { it as ResourceFont }
        assertEquals(2, fonts.size)
        val byWeight = fonts.associateBy { it.weight }
        assertEquals(R.font.fraunces_display_500, byWeight.getValue(FontWeight.Medium).resId)
        assertEquals(R.font.fraunces_display_600, byWeight.getValue(FontWeight.SemiBold).resId)
        for (font in fonts) {
            assertTrue(font.variationSettings.settings.isEmpty(), "a static font has no variation axis to lose mid-measurement")
        }
    }

    @Test
    fun coverTitleIsAStaticCutAtTheWidestOpticalSize() {
        val font = onlyFont(CoverTitle)
        assertEquals(R.font.fraunces_cover_600, font.resId)
        assertEquals(FontWeight.SemiBold, font.weight)
        assertTrue(font.variationSettings.settings.isEmpty())
    }

    @Test
    fun readAndInterfaceStillCarryTheirOwnVariationAxisRequest() {
        // Neither showed the failure Fraunces did: both stay variable, and
        // PageHead is the only TextAutoSize caller in the app, so neither
        // is ever drawn through the re-measurement pass that surfaced it.
        for (font in (Read as FontListFontFamily).map { it as ResourceFont }) {
            assertTrue(font.variationSettings.settings.isNotEmpty())
        }
        for (font in (Interface as FontListFontFamily).map { it as ResourceFont }) {
            assertTrue(font.variationSettings.settings.isNotEmpty())
        }
    }
}
