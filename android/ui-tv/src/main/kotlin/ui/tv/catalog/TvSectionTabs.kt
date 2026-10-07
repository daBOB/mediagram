package ui.tv.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale

/**
 * A title page's own tabs — Overview/Cast/Similar/Details on a film, Episodes/
 * About/Cast/Similar on a show — pressed to switch, for [TvMasthead]'s own
 * reason: tv-material's habit of selecting on focus would swap the body
 * under the remote on every step it takes along the row, before it ever
 * reaches the tab a viewer meant to press.
 *
 * The remote arriving from above or below lands on the tab that is showing,
 * not on whichever tab happens to sit nearest: Up out of a panel goes back
 * to the panel's own tab, as a reader's eye does. Only a directional
 * arrival is redirected — a tab asked for focus by name keeps it, so the
 * redirect can never answer itself.
 *
 * [selected] survives the page's own state updates by living in the
 * caller's `rememberSaveable`, not here — a body that refetches (credits
 * arriving after the page opens) must not reset which tab is showing. Each
 * tab is keyed by its title and keeps one requester for its life, so Cast
 * arriving between two tabs moves nothing that already holds the remote.
 */
@Composable
internal fun TvSectionTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { HashMap<String, FocusRequester>() }

    fun requesterOf(title: String) = requesters.getOrPut(title) { FocusRequester() }

    TabRow(
        selectedTabIndex = selected,
        // Held to the start like the masthead's tabs, so they line up over the title below.
        // The group comes after the width is wrapped: a focus search weighs a candidate by
        // how far its centre sits off the remote's line, and a group as wide as the page
        // has its centre mid-screen, where a pill above a left-hand plate beats it.
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.Start)
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                            titles.getOrNull(selected)?.let { requesterOf(it).requestFocus() }
                        }
                    }
                }.focusGroup(),
        containerColor = Color.Transparent,
        // An underline, as the colours below assume: the default pill fills with the same light
        // colour as the selected tab's label once the row has focus, and the label vanishes.
        indicator = { positions, focused ->
            positions.getOrNull(selected)?.let { position ->
                TabRowDefaults.UnderlinedIndicator(currentTabPosition = position, doesTabRowHaveFocus = focused)
            }
        },
    ) {
        titles.forEachIndexed { index, title ->
            key(title) {
                Tab(
                    selected = index == selected,
                    onFocus = {},
                    onClick = { onSelect(index) },
                    modifier = Modifier.focusRequester(requesterOf(title)),
                    colors =
                        TabDefaults.underlinedIndicatorTabColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.primary,
                            focusedSelectedContentColor = MaterialTheme.colorScheme.primary,
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
}

/**
 * [TvTitlePage]'s own scrollable page — its tab row carries a horizontal
 * scroll capability of its own on a television-wide tab strip, so a test
 * asking for "the" scrollable node by `hasScrollAction()` alone finds two;
 * this is the one that is actually the page.
 */
internal const val TvTitlePageBodyTag = "tv-title-page-body"
