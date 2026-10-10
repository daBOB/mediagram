package ui.catalog

import catalog.ratingLabel
import android.os.Build
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
import ui.chrome.CatalogueButton
import androidx.compose.material3.MaterialTheme
import ui.chrome.CatalogueOutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import designsystem.Motion
import designsystem.Spacing
import designsystem.drift
import designsystem.entrance
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
    paused: Boolean,
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
        val art: @Composable (Modifier) -> Unit = { modifier -> DriftingPoster(poster, paused, onTogglePause, modifier) }
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

/** The reel's poster, for a test to read its drift from. */
internal const val FEATURED_POSTER_TEST_TAG = "featured-poster"

/** `featured-drift`: once per slide, from its own size to 1.09 and up-left by 1.5% and 1%, over 8.5s (`playback.css:499-503`). */
private const val POSTER_DRIFT_MS = 8_500
private const val POSTER_DRIFT_TO = 1.09f
private val POSTER_DRIFT_PAN = Offset(-0.015f, -0.01f)

/**
 * The poster, closing in once while its slide shows; a tap pauses the reel.
 * Paused, it holds where it is and carries on from there, as the web's
 * `animation-play-state: paused` does (`playback.css:585`). Under reduced
 * motion it rests at its own size, where the web's `animation: none` leaves
 * it. Clipped to its frame, as `.featured-poster` is (`playback.css:495`).
 */
@Composable
private fun DriftingPoster(
    poster: File?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.aspectRatio(2f / 3f).clipToBounds().clickable(role = Role.Button, onClick = onTogglePause)) {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .drift(1f, POSTER_DRIFT_TO, POSTER_DRIFT_MS, Motion.CssEaseOut, running = !paused, translate = POSTER_DRIFT_PAN, restScale = 1f)
                    .testTag(FEATURED_POSTER_TEST_TAG),
        )
    }
}

/** `featured-rise`: each line of the copy up 14dp and in over 900ms, a beat after the one above (`playback.css:505-510`). */
private const val RISE_MS = 900
private const val RISE_BEAT_MS = 150
private val RISE_FROM = 14.dp

/** A line of the copy rising [beat] beats after the first; the web stops counting at the fourth line (`:nth-child(n + 4)`). */
private fun Modifier.rise(beat: Int): Modifier = entrance(RISE_MS, delayMs = RISE_BEAT_MS * beat, fromY = RISE_FROM, easing = Motion.CssEaseOut)

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
    // A fixed beat per line, as on the web, where the facts line is always
    // there (`featured-reel.js:161`) and the tagline goes in before the
    // buttons when it arrives, rising from then (`featured-reel.js:211`).
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(count, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.rise(0))
        Text(set.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.rise(1))
        if (facts.isNotEmpty()) {
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.rise(2))
        }
        info?.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = Color.White, modifier = Modifier.rise(3))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), modifier = Modifier.padding(top = Spacing.small).rise(3)) {
            CatalogueButton(onClick = onPlay) { Text("Play") }
            CatalogueOutlinedButton(onClick = onDetails) { Text("Details", color = Color.White) }
        }
    }
}

/** Room at the foot of a slide for the reel's ‹ dots › row. */
private val NAV_CLEARANCE = 72.dp
