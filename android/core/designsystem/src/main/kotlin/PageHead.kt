package designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit

/**
 * A page's own head: the huge uppercase [title] in Fraunces, then a small
 * tracked-caps [eyebrow] in Geist beneath it — the web's own order,
 * `.dept-title` then `.eyebrow` in one `<header>` (`departments.css:31-36`,
 * `theme.css:196-203`, and the approved mockups' own `.head`), reused
 * everywhere a screen on this catalogue opens the way a magazine
 * department does.
 *
 * Built on `BasicText` rather than Material's `Text`, so `:ui-tv` — which
 * never has material3 on its compile classpath — can compose this too;
 * colour is taken as a parameter for the same reason, since a plain
 * `TextStyle` here has no `MaterialTheme.colorScheme` to reach for.
 *
 * [maxTitleSize] is the size the title starts at and steps down from —
 * [TextAutoSize.StepBased] shrinks it to fit rather than wrapping or
 * clipping, the way the web's `clamp()` lets the title answer to the
 * viewport instead of a screen answering to one fixed size. It also picks
 * which of [PageTitle]/[PageTitleCompact] draws it — the wide cut at 112sp
 * and up, the narrow one below — so a compact caller gets the optical size
 * its own static font was actually instanced at, not the wide cut scaled down.
 *
 * The eyebrow and the title are merged into one semantics node marked as a
 * heading, so a screen reader meets one page head once rather than two
 * unrelated pieces of text — the same merge `DESIGN.md`'s plate uses for
 * artwork and caption together.
 */
@Composable
fun PageHead(
    title: String,
    eyebrow: String,
    titleColor: Color,
    eyebrowColor: Color,
    maxTitleSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val titleStyle = if (maxTitleSize.value >= WideTitleFloor) PageTitle else PageTitleCompact
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        // The mockup's own `.title{margin:0 0 16px}` — the gap between the
        // title and the eyebrow beneath it, not the wider one the page
        // itself adds below the whole head.
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        BasicText(
            text = title.uppercase(),
            style = titleStyle.copy(color = titleColor),
            autoSize = TextAutoSize.StepBased(maxFontSize = maxTitleSize),
            maxLines = 1,
        )
        BasicText(text = eyebrow, style = Eyebrow.copy(color = eyebrowColor))
    }
}

/** Halfway between [PageTitleCompact]'s own 72sp ceiling and [PageTitle]'s 112sp one — every caller passes one or the other, never in between. */
private const val WideTitleFloor = 90f
