package ui.catalog

import catalog.ratingLabel
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.MediaSet
import uniffi.mediagram_core.TitleInfo
import java.io.File

/**
 * One film in the Featured reel — `slideFor` in `featured-reel.js`: the
 * poster drifting slowly over a blurred copy of itself, and beside it (below
 * it, on a window taller than wide) the count, title, year, genres, score,
 * tagline, and Play and Details. The score and tagline arrive a moment after
 * the slide, from the same title lookup the details screen makes.
 */
@Composable
internal fun FeaturedSlide(
    set: MediaSet,
    count: String,
    info: TitleInfo?,
    drifting: Boolean,
    onTogglePause: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    val poster = set.posterPath?.let(::File)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Blur needs API 31; below it the copy is simply dimmer.
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier)
                .alpha(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.45f else 0.25f),
        )
        val wide = maxWidth > maxHeight
        val art: @Composable (Modifier) -> Unit = { modifier -> DriftingPoster(poster, drifting, onTogglePause, modifier) }
        val copy: @Composable (Modifier) -> Unit = { modifier -> FeaturedCopy(set, count, info, onPlay, onDetails, modifier) }
        if (wide) {
            Row(
                // The foot is left clear for the dots, which sit over every slide.
                modifier = Modifier.fillMaxSize().padding(start = Spacing.extraLarge, end = Spacing.extraLarge, top = Spacing.extraLarge, bottom = NAV_CLEARANCE),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
            ) {
                art(Modifier.fillMaxHeight(0.85f))
                copy(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = Spacing.large, end = Spacing.large, top = Spacing.large, bottom = NAV_CLEARANCE),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.large, Alignment.CenterVertically),
            ) {
                art(Modifier.fillMaxWidth(0.7f))
                copy(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The poster, easing in and out a little while the slide holds; a tap pauses the reel. */
@Composable
private fun DriftingPoster(
    poster: File?,
    drifting: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier,
) {
    val drift = rememberInfiniteTransition(label = "drift")
    val scale by drift.animateFloat(1f, 1.05f, infiniteRepeatable(tween(7_000), RepeatMode.Reverse), label = "scale")
    Box(modifier = modifier.aspectRatio(2f / 3f).clickable(role = Role.Button, onClick = onTogglePause)) {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val shown = if (drifting) scale else 1f
                scaleX = shown
                scaleY = shown
            },
        )
    }
}

@Composable
private fun FeaturedCopy(
    set: MediaSet,
    count: String,
    info: TitleInfo?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier,
) {
    val facts = listOfNotNull(set.year?.toString()) + set.genres.take(3) + listOfNotNull(ratingLabel(info?.rating?.takeIf { it > 0 }))
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(count, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Text(set.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        info?.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), modifier = Modifier.padding(top = Spacing.small)) {
            Button(onClick = onPlay) { Text("Play") }
            OutlinedButton(onClick = onDetails) { Text("Details", color = Color.White) }
        }
    }
}

/** Room at the foot of a slide for the reel's ‹ dots › row. */
private val NAV_CLEARANCE = 72.dp
