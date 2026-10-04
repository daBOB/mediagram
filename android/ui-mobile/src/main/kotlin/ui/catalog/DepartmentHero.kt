package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.MediaSet
import ui.catalog.home.fluid
import ui.chrome.LocalTopChrome

/** The art strip's own share of the hero's width — `.dept-art{inset:0 0 0 30%}` (`departments.css:16`), unless Artwork mode asks for the full width (`appearance.css:23`). */
internal const val ART_WIDTH_FRACTION = 0.7f

/** `.dept-copy{max-width:40rem}` (`departments.css:29`). */
internal val DEPT_COPY_MAX_WIDTH = 640.dp

/** `.dept-quote{max-width:17rem}` (`departments.css:41`), measured after the quote's own inner padding rather than before it — see [DeptQuote]. */
internal val DEPT_QUOTE_MAX_WIDTH = 272.dp

/** The hero's own outer bounds, for a test to check its real measured height against [fluid]'s own floor. */
internal const val DEPARTMENT_HERO_TEST_TAG = "department-hero"

/**
 * A department's opening page, the way a magazine opens its cinema or its
 * television section — a Compose port of `department-hero.js`'s own
 * `departmentHero`: the department's name set very large, one line of
 * real figures, over art that fades into the page from the left the way
 * the home cover's own art does — with the lead title's own tagline as a
 * pull-quote inside the hero itself, top-right on a wide window. Shared by
 * Movies, Series, Tutorials, Documentaries, Collections and one franchise's
 * own page.
 *
 * The hero itself is never a tap target — the web only ever links the
 * quote's own credit (`department-hero.js`), never the picture or the words
 * beside it. [onOpenTitle] reaches only that credit, and only once [lead]
 * and a tagline both exist to draw one at all; `null` (Documentaries' own
 * case, `leadHref: null` on the web) leaves nothing clickable in the hero
 * at all.
 *
 * [leadName] credits the quote — defaults to [lead]'s own title, but a show
 * or a course credits its own name instead of whichever episode happened to
 * lead (the web's own `lead?.show`, `department-pages.js`).
 *
 * [franchiseTitle] draws the title the way `.franchise-hero .dept-title`
 * does (`departments.css:108`): up to three lines rather than shrinking a
 * long collection's name onto one, since a franchise name — unlike a fixed
 * department name — is never chosen to fit.
 *
 * [overview] is a franchise's own introduction, set under the line inside
 * the hero's copy, where `renderFranchise` appends `.franchise-overview`.
 */
@Composable
internal fun DepartmentHero(
    title: String,
    line: String,
    lead: MediaSet?,
    onOpenTitle: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    leadName: String? = lead?.title,
    franchiseTitle: Boolean = false,
    overview: String? = null,
) {
    // Solid hides a department's own art (`appearance.css:24`, unlike the
    // cover — see `ui.catalog.home.CoverSlide`'s own note on that split):
    // a lead with no backdrop already draws title and line over the plain
    // page, which is exactly what "plain pages, no artwork" asks for here.
    val art = lead?.backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
    // Whether [lead] carries a backdrop at all, regardless of Solid — the
    // no-art and Solid cases sit at different top offsets on the web
    // (`departments.css:86`) even though both leave [art] null.
    val hasBackdropData = lead?.backdropPath != null
    val artFraction = if (LocalBackdrop.current == Backdrop.ARTWORK) 1f else ART_WIDTH_FRACTION
    // The same width signal `ui.chrome.LibraryHome` decides its own bar
    // bleed from — a hero reading a different one (the raw `screenWidthDp`
    // against a 900dp breakpoint, once) went compact in a band where the
    // bar had already decided it was wide, and bled `LocalTopChrome` into a
    // layout that never asked for it.
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val compact = widthClass != WindowWidthSizeClass.EXPANDED
    val width = LocalConfiguration.current.screenWidthDp.dp
    val topChrome = LocalTopChrome.current
    val heroMinHeight = fluid(420f, 0.58f, 600f, LocalConfiguration.current.screenHeightDp.toFloat()).dp

    Box(
        modifier =
            modifier
                .testTag(DEPARTMENT_HERO_TEST_TAG)
                .fillMaxWidth()
                .let { if (!compact && art != null) it.heightIn(min = heroMinHeight) else it },
    ) {
        if (compact) {
            CompactDeptHero(title, line, art, width, franchiseTitle, overview)
        } else {
            // The web hides `.dept-quote` below 900px (`departments.css:91`)
            // — the quote is a wide-only concern, so only this branch ever
            // needs to know [lead]'s own tagline at all.
            val quote = art?.let { lead.tagline?.takeIf { it.isNotBlank() }?.let { tagline -> leadName?.let { name -> tagline to name } } }
            val onOpenLead = if (quote != null) onOpenTitle?.let { open -> { open(lead.setId) } } else null
            WideDeptHero(title, line, art, hasBackdropData, quote, onOpenLead, width, topChrome, heroMinHeight, artFraction, franchiseTitle, overview)
        }
    }
}
