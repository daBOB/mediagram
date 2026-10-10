package ui.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.PageTitle
import designsystem.PageTitleCompact
import ui.catalog.home.fluid

/** The title's own bounds, for a test to measure its real layout — text laid out is what a `text` match cannot tell apart from text wrapped or clipped. */
internal const val DEPT_HERO_TITLE_TEST_TAG = "department-hero-title"

/**
 * The huge title and facts line every [DepartmentHero] layout draws, wide
 * or compact — always in the page's own ink, never on-image: unlike the
 * quote (top-right, over the picture itself), this copy sits where the
 * art's own left fade has already blended it back to the page
 * (`departments.css` — neither `.dept-title` nor `.dept-line` names
 * `--on-image` at all, only `.dept-quote` does).
 *
 * [franchiseTitle] draws the title as `.franchise-hero .dept-title` does
 * (`departments.css:108`) rather than the plain department rule: up to
 * three lines at a smaller ceiling, instead of one line shrunk to fit —
 * a franchise's own name is never chosen the way "Movies" or "Series" are.
 */
@Composable
internal fun DeptHeroWords(
    title: String,
    line: String,
    compact: Boolean,
    width: Dp,
    franchiseTitle: Boolean = false,
    overview: String? = null,
) {
    if (franchiseTitle) {
        Text(
            text = title.uppercase(),
            style =
                PageTitle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = fluid(41.6f, 0.055f, 80f, width.value).sp,
                    lineHeight = 0.92.em,
                ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }.testTag(DEPT_HERO_TITLE_TEST_TAG),
        )
    } else {
        val titleSize = if (compact) fluid(48f, 0.16f, 72f, width.value) else fluid(56f, 0.085f, 120f, width.value)
        // A title never breaks inside a word — the web's own `.dept-title` does
        // not either, it just never has to: its `clamp()` is a plain CSS
        // formula with nothing measuring the actual string against it. Compose
        // has no such free lunch, so the size steps down to fit one line
        // instead, the same way `designsystem.PageHead` already sizes a page
        // title — [titleSize] (the web's own clamp number) is the *ceiling* this
        // starts from and shrinks below only if this catalogue's own longest
        // department name ("Documentaries") would not otherwise fit.
        BasicText(
            text = title.uppercase(),
            style = (if (compact) PageTitleCompact else PageTitle).copy(color = MaterialTheme.colorScheme.onSurface),
            autoSize = TextAutoSize.StepBased(maxFontSize = titleSize.sp),
            maxLines = 1,
            modifier = Modifier.semantics { heading() }.testTag(DEPT_HERO_TITLE_TEST_TAG),
        )
    }
    Text(
        text = line,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 1.4.em, fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
    // `.franchise-overview` (`departments.css`): the reading face at 17px/1.6,
    // in figures grey, no wider than 36rem.
    overview?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.6.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp).widthIn(max = 576.dp),
        )
    }
}
