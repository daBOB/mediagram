package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.ratingLabel
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import uniffi.mediagram_core.TitleInfo

/**
 * The art beside the facts, then the tagline and the overview — the
 * television twin of the phone's `TitleHeader`, with the same blocks in the
 * same order: [facts] (a show's age rating, a film's year and runtime), the
 * genres, the provider's rating. Each block is left out when there is
 * nothing for it, so a title with no provider entry is its art and its
 * facts rather than a row of empty labels.
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
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
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
                info?.genres?.takeIf(String::isNotBlank)?.let { genres ->
                    Text(text = genres, style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ratingLabel(info?.rating)?.let { Text(text = it, style = TvTypeScale.body) }
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
        info?.overview?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body) }
    }
}

/** Wider than the phone's 120dp: the same art read from across a room. */
private val PosterWidth = 180.dp
