package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.ratingLabel
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import ui.tv.TvFocus
import uniffi.mediagram_core.TitleInfo

/**
 * The page's name, the art beside the facts, then the tagline and the
 * overview — the television twin of the phone's `TitleHeader`, with the
 * same blocks in the same order: [facts] (a show's age rating, a film's
 * year and runtime), the provider's rating, the genres. Each block is left
 * out when there is nothing for it, so a title with no provider entry is
 * its art and its facts rather than a row of empty labels.
 *
 * [genres] are the catalogue's own, the same field the phone's and the
 * web's genre links are matched against, rather than the provider's genre
 * sentence the header printed before those links existed. They are a line
 * to read here, not links: a television has no genre page to open yet.
 *
 * [beside] goes at the foot of the facts, still beside the art — where a
 * title page puts its Play, as the web's film page does, so the one thing
 * to press is on screen from the start however long the overview under it
 * runs. The name, the art and the facts are one block to scroll by
 * ([revealsFromTop]): the remote coming back up to Play brings the name
 * above it back too.
 *
 * [readableOverview] lets the remote rest on the overview — see
 * [TvReadableParagraph] — for a page where nothing else sits below it.
 * [readableTitle] does the same for the name, on a page with no Play: the
 * remote needs a stop up here to bring the top of the page back to.
 *
 * The tagline is quoted and the overview is not, for the phone's reason:
 * one is a line of marketing and the other a paragraph of description.
 */
@Composable
internal fun TvTitleHeader(
    posterPath: String?,
    title: String,
    facts: String?,
    info: TitleInfo?,
    modifier: Modifier = Modifier,
    genres: List<String> = emptyList(),
    readableOverview: Boolean = false,
    readableTitle: Boolean = false,
    beside: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Column(modifier = Modifier.revealsFromTop(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            if (readableTitle) {
                TvReadableParagraph(title, style = TvTypeScale.title)
            } else {
                Text(text = title, style = TvTypeScale.title)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.large)) {
                TvPlateArt(
                    posterPath = posterPath?.let(::File),
                    title = title,
                    progress = null,
                    watched = false,
                    modifier = Modifier.width(PosterWidth),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    facts?.let { Text(text = it, style = TvTypeScale.body) }
                    ratingLabel(info?.rating)?.let { Text(text = it, style = TvTypeScale.body) }
                    if (genres.isNotEmpty()) {
                        Text(
                            text = genres.joinToString(" · "),
                            style = TvTypeScale.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    beside()
                }
            }
        }
        info?.tagline?.takeIf(String::isNotBlank)?.let { tagline ->
            Text(
                text = "“$tagline”",
                style = TvTypeScale.body,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info?.overview?.takeIf(String::isNotBlank)?.let { overview ->
            if (readableOverview) {
                TvReadableParagraph(overview)
            } else {
                Text(text = overview, style = TvTypeScale.body)
            }
        }
    }
}

/** Wider than the phone's 120dp: the same art read from across a room. */
private val PosterWidth = 180.dp

/**
 * A paragraph the remote can rest on, for a page whose only other stop is
 * above it: a remote cannot scroll a page it has no stop in, so an
 * overview longer than the screen would otherwise run off the bottom out
 * of reach. Down lands here and the page scrolls it into view; Up goes back.
 *
 * Its focus is quieter than a row's — the accent as a rule down its
 * leading edge rather than a whole paragraph turned red and underlined — so
 * reading is not mistaken for something to press, while it still shows
 * from across a room where the remote is.
 */
@Composable
internal fun TvReadableParagraph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TvTypeScale.body,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = style,
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .drawBehind {
                    if (focused) {
                        val x = -ReadingRuleGap.toPx()
                        drawLine(Palette.Imprint, Offset(x, 0f), Offset(x, size.height), TvFocus.BorderWidth.toPx())
                    }
                },
    )
}

/** How far outside the paragraph its focus rule sits, so the rule never touches a letter. */
private val ReadingRuleGap = 12.dp
