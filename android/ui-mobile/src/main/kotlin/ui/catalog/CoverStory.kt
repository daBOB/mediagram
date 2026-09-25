package ui.catalog

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import designsystem.Spacing
import kotlinx.coroutines.delay
import model.MediaSet
import model.ageLabelOf
import java.io.File

/** How long one cover story holds before advancing, the web's own `HOLD_MS`. */
private const val HOLD_MS = 9_000L

/**
 * The cover story: one film at a time across the full width of the page,
 * its backdrop behind a headline — a Compose port of `home-cover.js`.
 *
 * Rotates on its own while more than one film is on it, unless the system's
 * "Remove animations" setting is on (Android's rough equivalent of the
 * web's `prefers-reduced-motion: reduce`), or the viewer has paused it by
 * hand. There is no pointer to hover here, so the web's other reason to
 * pause — the pointer resting on it — has nothing to port.
 */
@Composable
internal fun CoverStory(
    films: List<MediaSet>,
    onPlay: (MediaSet) -> Unit,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (films.isEmpty()) return
    val context = LocalContext.current
    val reducedMotion =
        remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val pagerState = rememberPagerState(pageCount = { films.size })
    var paused by rememberSaveable { mutableStateOf(false) }
    val rotates = films.size > 1 && !reducedMotion

    if (rotates) {
        LaunchedEffect(pagerState.currentPage, paused) {
            if (paused) return@LaunchedEffect
            delay(HOLD_MS)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % films.size)
        }
    }

    // The aspect ratio sits here, on the pager's own container, rather than
    // only on each slide: a `HorizontalPager` with no declared size measures
    // its content with an unbounded height inside a scrolling grid item and
    // collapses to nothing, so the whole cover would silently not draw.
    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val set = films[page]
            CoverSlide(set = set, onPlay = { onPlay(set) }, onDetails = { onOpenTitle(set.setId) })
        }
        if (films.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (rotates) {
                    Text(
                        text = if (paused) "▶" else "‖",
                        color = Color.White,
                        modifier =
                            Modifier
                                .clickable(role = Role.Button) { paused = !paused }
                                .padding(Spacing.extraSmall),
                    )
                }
                films.indices.forEach { index ->
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverSlide(
    set: MediaSet,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val backdropPath = set.backdropPath
        if (backdropPath != null) {
            AsyncImage(
                model = File(backdropPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.large)) {
            val genre = set.genres.firstOrNull()
            Text(
                text = listOfNotNull("Cover story", genre).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = set.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small),
            )
            val tagline = set.tagline
            if (!tagline.isNullOrEmpty()) {
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.small),
                )
            }
            val facts = factsLine(set.year, set.durationSecs, ageLabelOf(set.fsk))
            val meta = listOfNotNull(facts, ratingLabel(set.rating)).joinToString(" · ").ifEmpty { null }
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = Spacing.extraSmall),
                )
            }
            Row(
                modifier = Modifier.padding(top = Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Button(onClick = onPlay) { Text("Watch now") }
                TextButton(onClick = onDetails) { Text("Details", color = Color.White) }
            }
        }
    }
}
