package ui.tv.catalog

import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.factsLine
import catalog.ratingLabel
import coil3.compose.AsyncImage
import designsystem.Spacing
import designsystem.TvTypeScale
import kotlinx.coroutines.delay
import model.MediaSet
import model.ageLabelOf
import java.io.File
import ui.tv.TvTextRow

/** The web's own `HOLD_MS` — see the phone's `CoverStory`, this page's own reference. */
private const val HOLD_MS = 9_000L

/**
 * The magazine home page's cover story, the television twin of the phone's
 * `CoverStory` and the web's `home-cover.js` — one film at a time across the
 * full width of the page, its backdrop behind a headline, rotating on its
 * own.
 *
 * A television has no pointer to rest and pause it the way the phone's
 * hover does; [Watch now] and [Details] taking the remote is what pauses it
 * here instead — a viewer who has stepped onto the cover to press one of
 * them is exactly the viewer the rotation must stop under, the same reason
 * hovering pauses it on the web.
 *
 * [kicker] is the small caps line above the title — "Cover story" for
 * Home's own magazine header, "Only in your library" when a department page
 * reuses this same slide for its single-film hero (`department-hero.js`'s
 * own kicker; that hero has no rotation of its own either, which one film
 * already guarantees here). [arrivalFocus], attached to the first slide's
 * Watch now, is how a caller makes this cover the page's own arrival stop.
 */
@Composable
internal fun TvCoverStory(
    films: List<MediaSet>,
    onPlay: (setId: String) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    kicker: String = "Cover story",
    arrivalFocus: FocusRequester? = null,
) {
    if (films.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { films.size })
    var held by rememberSaveable { mutableStateOf(false) }
    val rotates = films.size > 1

    if (rotates) {
        LaunchedEffect(pagerState.currentPage, held) {
            if (held) return@LaunchedEffect
            delay(HOLD_MS)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % films.size)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(21f / 9f).padding(bottom = Spacing.medium)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val set = films[page]
            TvCoverSlide(
                set = set,
                kicker = kicker,
                onPlay = { onPlay(set.setId) },
                onDetails = { onOpenTitle(set.setId) },
                onHeld = { held = it },
                // Only the first page is ever the arrival stop: a fresh
                // composition always opens on page 0, the same page every
                // arrival-focus caller means.
                arrivalFocus = arrivalFocus.takeIf { page == 0 },
            )
        }
        if (films.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
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
private fun TvCoverSlide(
    set: MediaSet,
    kicker: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onHeld: (Boolean) -> Unit,
    arrivalFocus: FocusRequester?,
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
                text = listOfNotNull(kicker, genre).joinToString(" · "),
                style = TvTypeScale.body,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = set.title,
                style = TvTypeScale.title,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small),
            )
            set.tagline?.takeIf(String::isNotEmpty)?.let { tagline ->
                Text(
                    text = "“$tagline”",
                    style = TvTypeScale.body,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.small),
                )
            }
            val facts = factsLine(set.year, set.durationSecs, ageLabelOf(set.fsk))
            val meta = listOfNotNull(facts, ratingLabel(set.rating)).joinToString(" · ").ifEmpty { null }
            if (meta != null) {
                Text(text = meta, style = TvTypeScale.body, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = Spacing.extraSmall))
            }
            Row(modifier = Modifier.padding(top = Spacing.medium), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                TvTextRow(
                    text = "▶ Watch now",
                    onClick = onPlay,
                    modifier =
                        Modifier
                            .onFocusChanged { onHeld(it.isFocused) }
                            .let { if (arrivalFocus != null) it.focusRequester(arrivalFocus) else it },
                )
                TvTextRow(
                    text = "Details",
                    onClick = onDetails,
                    modifier = Modifier.onFocusChanged { onHeld(it.isFocused) },
                )
            }
        }
    }
}
