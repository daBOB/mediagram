package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvSafeArea

/**
 * A reference page's own head — the web's `heading()` as `.shelf-head`
 * sets it (`catalog.css`), the phone's `ShelfHead`: the title in the display
 * face at the web's 4.4vw (42sp at television's fixed 960dp), how much is
 * there in small tracked capitals flush right, and a rule under both.
 * Genres, one genre, Latest, a person and a list open with this, as on the web;
 * [leading] goes before the title — a person's round portrait, which the
 * web prepends to the same header.
 */
@Composable
internal fun TvShelfHead(
    title: String,
    sub: String?,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            leading?.let {
                it()
                Spacer(Modifier.width(Spacing.medium))
            }
            Text(
                text = title,
                style = TvTypeScale.title.copy(fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 1.em, letterSpacing = (-0.03).em),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 24.dp).semantics { heading() },
            )
            sub?.let {
                Text(
                    text = it.uppercase(),
                    style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, letterSpacing = 0.24.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .padding(top = 20.dp, bottom = Spacing.medium)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.borderVariant),
        )
    }
}

/**
 * The same "Title · n" the web's and the phone's row and kept-wall headings
 * carry, over a hairline — one heading for Home's rows and the kept walls
 * alike, so a viewer reading Continue's row on Home and its own tab sees
 * one convention, not two. [trailing] goes at the line's far end, where a
 * Home row puts its "See all".
 */
@Composable
internal fun TvCountedHeading(
    title: String,
    total: Int,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        append(title)
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · $total") }
                    },
                style = TvTypeScale.title,
            )
            trailing()
        }
        Box(
            modifier =
                Modifier
                    .padding(top = Spacing.small, bottom = Spacing.medium)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.borderVariant),
        )
    }
}

/**
 * A heading one step down from [TvCountedHeading] — a course's folder —
 * where the phone sets a smaller title. The television scale has only a
 * title and a body size, so this is the body face made heavier rather than
 * a third size invented here.
 */
@Composable
internal fun TvSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = TvTypeScale.body.copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier.padding(top = Spacing.small),
    )
}

/**
 * A line that says something rather than leading anywhere — an empty
 * list's text where a button still follows it, so the phone's centred
 * message cannot fill the page.
 */
@Composable
internal fun TvQuietLine(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = TvTypeScale.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/**
 * What a screen says when it has nothing to show, in the phone's exact
 * words — a viewer who uses both surfaces reads the same sentence on each.
 */
@Composable
internal fun TvCenteredMessage(message: String) {
    TvSafeArea {
        Text(
            text = message,
            style = TvTypeScale.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
