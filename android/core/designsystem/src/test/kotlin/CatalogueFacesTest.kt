package designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.sp
import com.mediagram.android.core.designsystem.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The faces the web player sets and the roles that reach them: a quote in
 * Newsreader's own italic file, a television's figures at Geist 400, and
 * every role at the web's `letter-spacing: normal` rather than M3's.
 */
@OptIn(ExperimentalTextApi::class)
class CatalogueFacesTest {
    private val readFonts = (Read as FontListFontFamily).map { it as ResourceFont }

    @Test
    fun anItalicQuoteIsTheWebsItalicFileNotTheRomanSlanted() {
        val italic = readFonts.single { it.style == FontStyle.Italic }
        assertEquals(R.font.newsreader_italic, italic.resId)
        assertEquals(FontWeight.Normal, italic.weight, "every quote is set at 400")
        assertTrue(italic.variationSettings.settings.isNotEmpty(), "it takes the same opsz as the roman")
    }

    @Test
    fun theRomanWeightsStayOnTheRomanFile() {
        val roman = readFonts.filter { it.style == FontStyle.Normal }
        assertEquals(listOf(400, 500, 600), roman.map { it.weight.weight })
        assertTrue(roman.all { it.resId == R.font.newsreader })
    }

    @Test
    fun theTelevisionsFiguresAreGeist400AsTheWebsMetaLinesAre() {
        for (role in listOf(TvType.Base.labelSmall, TvType.Base.labelMedium)) {
            assertEquals(Interface, role.fontFamily)
            assertEquals(FontWeight.Normal, role.fontWeight)
        }
    }

    @Test
    fun theRolesKeepTheWebsNormalTrackingNotM3s() {
        with(CatalogueType) {
            for (role in listOf(titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall)) {
                assertEquals(0.sp, role.letterSpacing)
            }
        }
    }
}
