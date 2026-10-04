package ui.tv.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.MaterialTheme
import catalog.Division
import catalog.seasonOptionOf
import designsystem.Spacing
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import ui.tv.chrome.TvPill

/**
 * The season picker over a show's episode list — `series-page.js`'s own
 * `episodes()` select, as a row of pills: each says what its option says
 * ("Season 2 · eight episodes"), the one showing is filled the way a chosen
 * department pill is, and a press shows that season's episodes under it.
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        for (division in divisions) {
            key(division.title) {
                TvPill(
                    title = seasonOptionOf(division),
                    count = null,
                    active = division == shown,
                    ink = MaterialTheme.colorScheme.onSurface,
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

/** For a test to find the picker without matching on a season's own words. */
internal const val TvSeasonPickerTag = "tv-season-picker"
