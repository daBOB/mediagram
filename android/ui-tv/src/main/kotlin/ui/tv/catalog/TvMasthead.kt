package ui.tv.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus
import ui.tv.TvTextRow
import ui.tv.profile.TvChosenProfile

/**
 * The masthead the web player and the phone both have, across the top of
 * the screen rather than down a side drawer: [titles] in `catalogTabsOf`'s
 * order — Home, the catalog shelves, then the four kept from watch state —
 * and at the far end, set apart as the web's `#who` is, the name of whoever
 * is watching. Choosing that name reopens the picker, the way the phone's bar
 * button does.
 *
 * A tab is selected by pressing it, not by landing on it. tv-material's
 * habit of selecting on focus would swap the wall below on every step the
 * remote takes along the masthead, and a wall takes focus the moment it
 * appears — so walking from Home to Kids would be pulled down into Movies
 * on the first step. Pressing is also what the web and the phone ask for.
 *
 * [focusRequester] is how a caller sends the remote back up here. It lands
 * on the tab last focused, or the selected one the first time, rather than
 * on whichever tab happens to be leftmost. [selectedFocus] lands on the
 * selected tab itself, wherever on the masthead the remote already is.
 */
@Composable
internal fun TvMasthead(
    titles: List<String>,
    selected: Int,
    firstKeptIndex: Int,
    profile: TvChosenProfile,
    onSelect: (Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    selectedFocus: FocusRequester = remember { FocusRequester() },
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = Overscan.horizontal, end = Overscan.horizontal, top = Overscan.vertical)
                .focusRequester(focusRequester)
                .focusRestorer(selectedFocus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (titles.isNotEmpty()) {
            TabRow(
                selectedTabIndex = selected,
                // Held to the start rather than left to centre itself in the
                // space the name leaves it, so Home lines up over the rows.
                modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start),
                // Type on the ground rather than a band laid over it — the
                // phone's masthead makes the same choice for the same reason.
                containerColor = Color.Transparent,
                indicator = { positions, focused ->
                    positions.getOrNull(selected)?.let { position ->
                        TabRowDefaults.UnderlinedIndicator(currentTabPosition = position, doesTabRowHaveFocus = focused)
                    }
                },
            ) {
                titles.forEachIndexed { index, name ->
                    MastheadTab(
                        name = name,
                        selected = index == selected,
                        apart = index == firstKeptIndex,
                        onClick = { onSelect(index) },
                        modifier = if (index == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Outside the TabRow, not its last tab: a TabRow scrolls its tabs
        // inside its own width, and a full masthead pushed the name past
        // that edge, leaving only its leading rule on screen. Here it keeps
        // a slot of its own at the far end, as the web's `#who` does, and
        // is never selected — it is an action, not a place.
        TvTextRow(
            text = profile.name,
            onClick = profile.onChoose,
            focusRequester = if (titles.isEmpty()) selectedFocus else null,
            modifier =
                Modifier
                    .padding(start = Spacing.medium)
                    .leadingRule(MaterialTheme.colorScheme.borderVariant)
                    .padding(start = Spacing.medium, top = Spacing.small, bottom = Spacing.small),
        )
    }
}

/**
 * One masthead entry. [apart] draws a hairline before it — the web's
 * `class="kept"` break, drawn on the tab itself because a `TabRow` measures
 * its tabs as its only children and has nowhere to put a sibling between
 * them.
 */
@Composable
private fun androidx.tv.material3.TabRowScope.MastheadTab(
    name: String,
    selected: Boolean,
    apart: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val ruleColor = MaterialTheme.colorScheme.borderVariant
    Tab(
        selected = selected,
        onFocus = {},
        onClick = onClick,
        modifier = modifier.let { if (apart) it.padding(start = Spacing.medium).leadingRule(ruleColor) else it },
        colors =
            TabDefaults.underlinedIndicatorTabColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = Palette.Imprint,
                focusedSelectedContentColor = Palette.Imprint,
            ),
        interactionSource = interactionSource,
    ) {
        Text(
            text = name,
            style = TvFocus.textStyle(TvTypeScale.body, focused),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Tight enough that Home, three shelves and the four kept entries
            // fit a 960dp television beside the viewer's name without the
            // row having to scroll.
            modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.small),
        )
    }
}

/** A hairline down the tab's leading edge — see [MastheadTab]'s own note on why it is drawn here. */
private fun Modifier.leadingRule(color: Color): Modifier =
    drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, size.height * 0.25f),
            end = Offset(0f, size.height * 0.75f),
            strokeWidth = 1.dp.toPx(),
        )
    }
