package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import designsystem.Spacing
import java.io.File

/**
 * A card is named underneath rather than across its face.
 *
 * Every episode of a show carries the same artwork, and the index pinned in
 * a channel carries no artwork at all, so the face is the least reliable
 * place to say what something is. Initials stand in for a missing poster —
 * enough to tell two cards apart at a glance, and the name is right below
 * them either way.
 */
@Composable
internal fun PosterCard(
    posterPath: String?,
    title: String,
    caption: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Card(modifier = Modifier.aspectRatio(2f / 3f).clickable(onClick = onClick)) {
            if (posterPath != null) {
                AsyncImage(
                    model = File(posterPath),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = initialsOf(title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.small),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.extraSmall),
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two letters to stand in for artwork that is not there. */
internal fun initialsOf(title: String): String = title
    .split(WHITESPACE)
    .take(2)
    .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
    .joinToString("")
    .uppercase()
    .ifEmpty { "?" }

private val WHITESPACE = Regex("\\s+")
