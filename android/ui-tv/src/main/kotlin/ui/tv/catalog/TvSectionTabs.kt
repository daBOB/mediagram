package ui.tv.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale

/**
 * A title page's own tabs — Overview/Cast/Similar/Details on a film, Episodes/
 * About/Cast/Similar on a show — pressed to switch, for [TvMasthead]'s own
 * reason: tv-material's habit of selecting on focus would swap the body
 * under the remote on every step it takes along the row, before it ever
 * reaches the tab a viewer meant to press.
 *
 * [selected] survives the page's own state updates by living in the
 * caller's `rememberSaveable`, not here — a body that refetches (credits
 * arriving after the page opens) must not reset which tab is showing.
 */
@Composable
internal fun TvSectionTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    TabRow(
        selectedTabIndex = selected,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onFocus = {},
                onClick = { onSelect(index) },
                colors =
                    TabDefaults.underlinedIndicatorTabColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = Palette.Imprint,
                        focusedSelectedContentColor = Palette.Imprint,
                    ),
            ) {
                Text(
                    text = title,
                    style = TvTypeScale.body,
                    modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.small),
                )
            }
        }
    }
}
