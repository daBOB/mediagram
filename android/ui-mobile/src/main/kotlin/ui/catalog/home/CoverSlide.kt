package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.coverFactsLine
import designsystem.CoverTitle
import designsystem.Eyebrow
import designsystem.Motion
import designsystem.Spacing
import designsystem.drift
import designsystem.entrance
import model.MediaSet
import ui.chrome.LocalTopChrome
import ui.common.catalog.HeroArtwork

/** Past this many characters a title steps down a size, so it still fits in three lines (`home-cover.js:22`). */
private const val LONG_TITLE = 18

/** Each slide's picture settles once from 1.06 to its own size — `cover-drift 14s var(--ease-out)` (`home.css:27,128`). */
private const val DRIFT_FROM = 1.06f
private const val DRIFT_MS = 14_000

/**
 * One cover story's own picture, headline and actions, over its own
 * backdrop — one slide of [HomeCover].
 *
 * [turning]: the page arrived on this film or a turn brought it in, so the
 * slide comes up out of the cover's ground and its picture settles. A film a
 * refresh swapped in is drawn at rest, as on the web's settled page.
 * [arrived], when given, is the page's note that the arrival has played.
 *
 * [minHeight] is a floor, not an exact height: this root `Box`'s own
 * `heightIn(min = ...)` is what actually reserves the cover's usual size —
 * [HeroArtwork] and [CoverScrim] below read `matchParentSize()` rather than
 * `fillMaxSize()` so neither drives that size itself, the same reason
 * `HomeFeatures.kt`'s own cards do — the root `Box` is then `max(min, its
 * own text column)` tall, tall enough to fit a headline a short window
 * (split screen) would otherwise clip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoverSlide(
    set: MediaSet,
    width: Dp,
    columnWidth: Dp,
    compact: Boolean,
    minHeight: Dp,
    watchlisted: Boolean,
    turning: Boolean,
    arrived: MutableState<Boolean>?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onToggleWatchlist: (Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).then(if (turning) Modifier.entrance(COVER_FADE_MS, seen = arrived) else Modifier)) {
        val backdropPath = set.backdropPath
        // Blurred is the only Artwork mode that changes the cover's own
        // picture: `appearance.css:22` lists `.cover-stage` on its blur rule
        // only — the Solid rule two lines below names just `.spread-art`/
        // `.dept-art`, so the web leaves the cover's own picture showing
        // under Solid too, unlike the title spread or a department hero.
        // `HeroArtwork` already carries exactly that split.
        if (backdropPath != null) {
            // Clipped here, so the settling picture never spills past the
            // cover (`.cover { overflow: hidden }`, `home.css:13`). Outside
            // HeroArtwork, so Blurred's own 1.12 overscan multiplies with the
            // settle instead of being overridden by it as the web's is.
            HeroArtwork(
                path = backdropPath,
                modifier =
                    Modifier
                        .matchParentSize()
                        .clipToBounds()
                        .then(if (turning) Modifier.drift(DRIFT_FROM, 1f, DRIFT_MS, Motion.EaseOut) else Modifier),
            )
        } else {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        CoverScrim(compact = compact, modifier = Modifier.matchParentSize())

        val wide = width > WideBreakpoint
        val textMaxWidth = if (compact) columnWidth else (columnWidth * (if (wide) 0.60f else 0.72f)).coerceAtMost(736.dp)
        val topChrome = LocalTopChrome.current
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = textMaxWidth)
                    .padding(
                        start = gutterFor(width),
                        end = gutterFor(width),
                        top = if (topChrome > 0.dp) topChrome + 12.dp else Spacing.large,
                        // The web's own compact copy sits higher off the
                        // bottom than the pager does under it —
                        // `padding-bottom: 104px`, against the pager's own
                        // `bottom: 40px` (`home.css:350,355`) — so the pager
                        // never sits over the button row. Above compact
                        // there is no pager collision to guard, so both
                        // keep their older, tighter numbers.
                        bottom = if (compact) 104.dp else 40.dp,
                    ),
        ) {
            val genre = set.genres.firstOrNull()
            Text(
                text = listOfNotNull("Featured today", genre).joinToString(" · ").uppercase(),
                style = Eyebrow,
                color = OnImage2,
                modifier = Modifier.padding(bottom = 22.dp),
            )
            val long = set.title.length > LONG_TITLE
            val titleSize = if (long) fluid(38.4f, 0.044f, 73.6f, width.value) else fluid(48f, 0.064f, 112f, width.value)
            Text(
                text = set.title.uppercase(),
                style = CoverTitle.copy(fontSize = titleSize.sp, lineHeight = if (long) 0.9.em else CoverTitle.lineHeight),
                color = OnImage,
            )
            val tagline = set.tagline
            if (!tagline.isNullOrEmpty()) {
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = fluid(17.6f, 0.014f, 20.8f, width.value).sp, lineHeight = 1.4.em),
                    color = OnImage2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
            val meta = coverFactsLine(set)
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, letterSpacing = 0.06.em),
                    color = OnImage2,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            // A `FlowRow`, not a `Row`: the web's own `.cover-actions` wraps
            // (`flex-wrap: wrap`, `home.css:70`) rather than squeezing
            // "Details" until it breaks mid-word — the three actions need
            // about 346dp at font scale 1.0, more than a 360dp phone has
            // left after its own gutters, and more still at a larger font
            // scale.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 34.dp),
            ) {
                Box(Modifier.align(Alignment.CenterVertically)) {
                    SolidPill(text = "▶  Watch now", description = "Watch now: ${set.title}", onClick = onPlay)
                }
                Box(Modifier.align(Alignment.CenterVertically)) {
                    LinePill(
                        text = if (watchlisted) "✓ My List" else "+ My List",
                        description = if (watchlisted) "Remove from My List" else "Add to My List",
                        onClick = { onToggleWatchlist(!watchlisted) },
                    )
                }
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                    color = OnImage2,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 16.dp)
                            .clickable(role = Role.Button, onClick = onDetails)
                            .semantics { contentDescription = "Details: ${set.title}" }
                            .padding(vertical = 12.dp),
                )
            }
        }
    }
}
