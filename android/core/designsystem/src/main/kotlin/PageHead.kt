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
 * A page's own head: the huge uppercase [title] in Fraunces over a small
 * tracked-caps [eyebrow] in Geist — the web's `.dept-title`/`.eyebrow` pair
 * (`departments.css:31-36`, `theme.css:196-203`), reused everywhere a
 * screen on this catalogue opens the way a magazine department does.
 *
 * Built on `BasicText` rather than Material's `Text`, so `:ui-tv` — which
 * never has material3 on its compile classpath — can compose this too;
 * colour is taken as a parameter for the same reason, since a plain
 * `TextStyle` here has no `MaterialTheme.colorScheme` to reach for.
 *
 * [maxTitleSize] is the size the title starts at and steps down from —
 * [TextAutoSize.StepBased] shrinks it to fit rather than wrapping or
 * clipping, the way the web's `clamp()` lets the title answer to the
 * viewport instead of a screen answering to one fixed size.
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
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        BasicText(text = eyebrow, style = Eyebrow.copy(color = eyebrowColor))
        BasicText(
            text = title.uppercase(),
            style = PageTitle.copy(color = titleColor),
            autoSize = TextAutoSize.StepBased(maxFontSize = maxTitleSize),
            maxLines = 1,
        )
    }
}
