package ui.tv.catalog

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import designsystem.PageTitle
import designsystem.PageTitleCompact
import designsystem.TvType
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The television's display titles in the cuts the web draws them in, read
 * from the style each was actually laid out in: a department's and a
 * spread's titles in Fraunces' display cuts, not the opsz-28 text cut that
 * made them read as the old TV; the quote in Newsreader italic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvDisplayFacesTest : TvScreenStateTest() {
    private val lead = set("lead", Kind.MOVIE, "Lead Film", addedAt = 0).copy(backdropPath = "/bd", tagline = "A tagline")

    @Test
    fun aDepartmentTitleIsTheWideDisplayCutAsTheWebsOpsz120() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead) }
        assertEquals(PageTitle.fontFamily, styleOf("MOVIES").fontFamily)
    }

    @Test
    fun aFranchiseTitleIsTheCompactDisplayCutAsTheWebsOpsz80() {
        show { TvDepartmentHero(title = "Saga", line = "two films", lead = lead, franchiseTitle = true) }
        assertEquals(PageTitleCompact.fontFamily, styleOf("SAGA").fontFamily)
    }

    @Test
    fun theDepartmentQuoteIsNewsreaderItalic() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead) }
        val quote = styleOf("“A tagline”")
        assertEquals(TvType.bodyLarge.fontFamily, quote.fontFamily)
        assertEquals(FontStyle.Italic, quote.fontStyle)
    }

    @Test
    fun aSpreadTitleIsTheWideCutAndALongOneTheCompact() {
        show { TvTitleSpread(backdropPath = null, title = "Heat", facts = null, overview = null, tagline = null) {} }
        assertEquals(PageTitle.fontFamily, styleOf("Heat").fontFamily)
        close()

        val long = "The Assassination of Jesse James"
        show { TvTitleSpread(backdropPath = null, title = long, facts = null, overview = null, tagline = null) {} }
        assertEquals(PageTitleCompact.fontFamily, styleOf(long).fontFamily)
    }

    @Test
    fun theSmallerDisplayLinesAreFraunces600() {
        // Home's feature titles and a resume card's initial: `.feature-title` and `.resume-initial` are 600.
        assertEquals(TvType.headlineSmall.fontFamily, DisplaySemiBold.fontFamily)
        assertEquals(FontWeight.SemiBold, DisplaySemiBold.fontWeight)
    }
}
