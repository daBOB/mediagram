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
 * Pins each Fraunces role to the font instance it actually resolves through,
 * so a regression like the one this guards never ships silently again:
 * [PageTitle]/[PageTitleCompact] draw through `TextAutoSize.StepBased`
 * (`PageHead`'s own doc comment), which on at least one device left a
 * `FontVariation.Settings` request behind mid-measurement and fell back to
 * `fraunces.ttf`'s own registered default instance — `wght 900`/`opsz 9`,
 * its heaviest, most decorative cut. The fix ships [PageTitle] and
 * [PageTitleCompact] as static, pre-instanced fonts with no axis left to
 * drop; this test is what would have caught the regression before a device
 * did — a `variationSettings` request on a font `TextAutoSize` re-measures.
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
    fun displayStillCarriesItsOwnVariationAxisRequest() {
        // Display draws through plain Text, never TextAutoSize — it never
        // showed the bug, so it stays on the variable font, weight per role.
        val fonts = (Display as FontListFontFamily).map { it as ResourceFont }
        assertEquals(2, fonts.size)
        for (font in fonts) {
            assertEquals(R.font.fraunces, font.resId)
            assertTrue(font.variationSettings.settings.isNotEmpty())
        }
    }

    @Test
    fun readAndInterfaceAlsoStillCarryTheirOwnVariationAxisRequest() {
        // Neither is ever drawn through TextAutoSize either (PageHead is the
        // only caller in the whole app) — see the grep this phase's own
        // report cites as evidence neither needed the same static-font fix.
        for (font in (Read as FontListFontFamily).map { it as ResourceFont }) {
            assertTrue(font.variationSettings.settings.isNotEmpty())
        }
        for (font in (Interface as FontListFontFamily).map { it as ResourceFont }) {
            assertTrue(font.variationSettings.settings.isNotEmpty())
        }
    }
}
