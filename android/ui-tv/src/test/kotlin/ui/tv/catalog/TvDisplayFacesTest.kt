package ui.tv.catalog

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import catalog.Feature
import catalog.FeatureKind
import catalog.SearchRow
import designsystem.PageTitle
import designsystem.PageTitleCompact
import designsystem.TvType
import model.Kind
import stats.AchievementsUi
import stats.NextLine
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState
import ui.tv.catalog.home.TvContinueBand
import ui.tv.catalog.home.TvHomeFeatures
import ui.tv.settings.TvSettingsPanes
import ui.common.settings.SettingsSection
import kotlin.test.assertNotEquals
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
        assertDisplayCut(styleOf("MOVIES"), PageTitle.fontFamily)
    }

    @Test
    fun aFranchiseTitleIsTheCompactDisplayCutAsTheWebsOpsz80() {
        show { TvDepartmentHero(title = "Saga", line = "two films", lead = lead, franchiseTitle = true) }
        assertDisplayCut(styleOf("SAGA"), PageTitleCompact.fontFamily)
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
        assertDisplayCut(styleOf("Heat"), PageTitle.fontFamily)
        close()

        val long = "The Assassination of Jesse James"
        show { TvTitleSpread(backdropPath = null, title = long, facts = null, overview = null, tagline = null) {} }
        assertDisplayCut(styleOf(long), PageTitleCompact.fontFamily)
    }

    @Test
    fun theSmallerDisplayLinesAreFraunces600() {
        // Home's feature titles and a resume card's initial: `.feature-title` and `.resume-initial` are 600.
        assertEquals(TvType.headlineSmall.fontFamily, DisplaySemiBold.fontFamily)
        assertEquals(FontWeight.SemiBold, DisplaySemiBold.fontWeight)
    }

    @Test
    fun aHomeFeatureTitleIsFraunces600() {
        show { TvHomeFeatures(listOf(Feature(FeatureKind.EDITOR, lead)), onOpenTitle = {}) }
        val title = styleOf("LEAD FILM")
        assertEquals(DisplaySemiBold.fontFamily, title.fontFamily)
        assertEquals(FontWeight.SemiBold, title.fontWeight)
    }

    @Test
    fun theSettingsTitleIsTheWideDisplayCut() {
        show { TvSettingsPanes(SettingsSection.SYSTEM, emptyMap(), false, {}, {}, content = { _, _, _ -> }) }
        assertDisplayCut(styleOf("SYSTEM"), PageTitle.fontFamily)
    }

    @Test
    fun aSearchMatchReasonIsUpright() {
        show { TvSearchRow(SearchRow(set = lead, matched = "path", excerpt = null), progress = null, watched = false, onPlay = {}, focus = null) }
        assertNotEquals(FontStyle.Italic, styleOf("matched the folder").fontStyle, "Geist has no italic, and the web's badge is upright")
    }

    @Test
    fun thePullQuoteCreditIsGeist500() {
        show { TvContinueBand(cards = emptyList(), quote = lead, onPlay = {}, onOpenTitle = {}, onSeeAllContinue = {}) }
        assertEquals(FontWeight.Medium, styleOf("LEAD FILM").fontWeight)
    }

    @Test
    fun theStatsNextHeadingIsGeist500() {
        val ready =
            StatsUiState.Ready(
                totals = listOf("This week" to "42 min", "This month" to "42 min", "All time" to "42 min"),
                bars = List(30) { StatsBar(fraction = 0f, initial = "M", description = "day $it") },
                history = listOf(StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min")),
                achievements = AchievementsUi(earned = emptyList(), next = listOf(NextLine("films-10", "10 films", "1 of 10 films", 0.1f))),
            )
        show { TvStatsPage(ready) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Next"))
        assertEquals(FontWeight.Medium, styleOf("Next").fontWeight)
    }

    /** A display title in the cut the web draws it in, upright at the display weight. */
    private fun assertDisplayCut(
        style: androidx.compose.ui.text.TextStyle,
        family: androidx.compose.ui.text.font.FontFamily?,
    ) {
        assertEquals(family, style.fontFamily)
        assertEquals(FontWeight.Medium, style.fontWeight)
        assertNotEquals(FontStyle.Italic, style.fontStyle)
    }
}
