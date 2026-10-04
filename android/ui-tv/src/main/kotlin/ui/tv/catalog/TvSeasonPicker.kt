package ui.tv.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.MaterialTheme
import catalog.Division
import catalog.spelledCountOf
import designsystem.Spacing
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
 */
@Composable
internal fun TvSeasonPicker(
    divisions: List<Division>,
    shown: Division?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(TvSeasonPickerTag).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        for (division in divisions) {
            key(division.title) {
                TvPill(
                    title = seasonOptionLabel(division),
                    count = null,
                    active = division == shown,
                    ink = MaterialTheme.colorScheme.onSurface,
                    onClick = { onSelect(division.title) },
                )
            }
        }
    }
}

/** `Season 2 · eight episodes` — the web's option text, counted as its `countOf` counts. */
internal fun seasonOptionLabel(division: Division): String = "${division.title} · ${spelledCountOf(division.items.size, "episode")}"

/** For a test to find the picker without matching on a season's own words. */
internal const val TvSeasonPickerTag = "tv-season-picker"
