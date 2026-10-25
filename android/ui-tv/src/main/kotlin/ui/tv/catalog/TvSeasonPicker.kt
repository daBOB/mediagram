package ui.tv.catalog

import ui.tv.ambient.rememberCardGlow
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Division
import catalog.seasonOptionOf
import catalog.walk
import designsystem.PageTitle
import designsystem.Spacing
import designsystem.TvType
import designsystem.inTvStyle
import designsystem.tvDp
import designsystem.tvSp
import java.io.File
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import ui.tv.TvFocus
import ui.tv.catalog.home.OnImage

/**
 * The season picker over a show's episode list — `series-page.js`'s own
 * `episodes()` select, as a row of season cards the user asked for in place
 * of the web's select and the pills that stood for it here: each card the
 * season's own cover where the library holds one (an episode's
 * `seasonPosterPath`), else the show's poster with the season's number set
 * large over it so the cards still tell apart, the watched share as the progress rule along its
 * foot, and under it what the web's option says ("Season 2 · eight episodes")
 * and how many are watched. The one showing is marked with the tab row's own
 * rule beneath it, and a press shows that season's episodes under the row.
 * A row rather than a drop-down: every season is in sight and one press
 * away, where a drop-down would hide them behind an extra press and a list
 * the remote then has to leave.
 *
 * Pressed to choose, not chosen on focus, for the tabs' own reason: walking
 * along the row must not swap the list under the remote at every step.
 * Keyed by title, so the pill just pressed keeps the remote as the list
 * below it changes.
 *
 * The row is the only place the shown season is named, so the shown pill is
 * kept in sight: the row scrolls to it whenever the shown season changes —
 * a show reopened on Season 5 of 6 starts there, not on Season 1 with 5 cut
 * off the edge — and the remote arriving from above or below lands on it,
 * as [TvSectionTabs] lands on the showing tab. Only a directional arrival is
 * redirected, so a pill asked for by name keeps the remote.
 */
@Composable
internal fun TvSeasonPicker(
    divisions: List<Division>,
    shown: Division?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    art: File? = null,
    watchedIds: Set<String> = emptySet(),
) {
    val scroll = rememberScrollState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    // Each pill's span along the row, in the row's own (unscrolled) coordinates.
    val spans = remember { mutableStateMapOf<String, IntRange>() }

    fun requesterOf(title: String) = requesters.getOrPut(title) { FocusRequester() }

    LaunchedEffect(shown?.title) {
        val title = shown?.title ?: return@LaunchedEffect
        // On arrival the row has not been placed yet; wait for the pill's own span.
        val span = snapshotFlow { spans[title] }.filterNotNull().first()
        val viewport = snapshotFlow { scroll.viewportSize }.first { it > 0 }
        when {
            span.first < scroll.value -> scroll.scrollTo(span.first)
            span.last > scroll.value + viewport -> scroll.scrollTo(span.last - viewport)
        }
    }

    Row(
        modifier =
            modifier
                .testTag(TvSeasonPickerTag)
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                            shown?.let { requesterOf(it.title).requestFocus() }
                        }
                    }
                }.focusGroup()
                .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(StripGap),
    ) {
        for (division in divisions) {
            key(division.title) {
                TvSeasonCard(
                    division = division,
                    art = art,
                    watchedIds = watchedIds,
                    showing = division == shown,
                    onClick = { onSelect(division.title) },
                    modifier =
                        Modifier
                            .focusRequester(requesterOf(division.title))
                            .onPlaced { at ->
                                val x = at.positionInParent().x.toInt()
                                spans[division.title] = x..(x + at.size.width)
                            },
                )
            }
        }
    }
}

/** One season's card: its cover (or [art], the show's, with the number over it) at a poster's proportions, the option's words and the watched count under it. */
@Composable
private fun TvSeasonCard(
    division: Division,
    art: File?,
    watchedIds: Set<String>,
    showing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes = remember(division) { division.walk().flatMap { it.items.asSequence() }.map { it.setId }.toList() }
    val watched = episodes.count { it in watchedIds }
    val cover = remember(division) { division.walk().flatMap { it.items.asSequence() }.firstNotNullOfOrNull { it.seasonPosterPath }?.let(::File) }
    val picture = cover ?: art
    val glow = rememberCardGlow(picture)
    Column(modifier = modifier.width(SeasonCardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.semantics(mergeDescendants = true) { selected = showing }.then(glow.modifier),
            shape = TvFocus.cardShape(),
            scale = TvFocus.cardScale(),
            border = TvFocus.cardBorder(color = glow.border ?: MaterialTheme.colorScheme.primary),
            glow = TvFocus.cardGlow(),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column {
                Box {
                    val number = division.season?.toString() ?: division.title
                    TvPlateArt(
                        posterPath = picture,
                        // With no art the initials stand in, and they are the number.
                        title = number,
                        progress = if (episodes.isEmpty() || watched == 0) null else watched.toFloat() / episodes.size,
                        watched = episodes.isNotEmpty() && watched == episodes.size,
                        ratio = PosterAspectRatio,
                    )
                    // The show's poster standing in for every season: the number over it tells them apart,
                    // its foot darkened under it. A season's own cover already says which it is.
                    if (cover == null && art != null) {
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                        Text(
                            text = number,
                            style = PageTitle.inTvStyle().copy(fontSize = 44.tvSp, lineHeight = 0.9.em),
                            color = OnImage,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.small, bottom = Spacing.small),
                        )
                    }
                }
                Text(
                    text = seasonOptionOf(division),
                    style = TvType.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.small, end = Spacing.small, top = Spacing.small),
                )
                Text(
                    text = if (watched == 0) " " else "$watched of ${episodes.size} watched",
                    style = TvType.labelSmall.copy(fontSize = 12.tvSp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.small, end = Spacing.small, bottom = Spacing.small),
                )
            }
        }
        // The season showing, marked as the showing tab is: a rule under it.
        Box(
            Modifier
                .padding(top = Spacing.small)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (showing) MaterialTheme.colorScheme.onBackground else Color.Transparent),
        )
    }
}

/** A poster plate's floor and a little more: six seasons across the row, a seventh runs on, the row scrolling to it. */
private val SeasonCardWidth = 150.tvDp

/** For a test to find the picker without matching on a season's own words. */
internal const val TvSeasonPickerTag = "tv-season-picker"
