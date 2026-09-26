package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Overscan
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
        // Held to the start like the masthead's tabs, so they line up over the title below.
        modifier = modifier.fillMaxWidth().wrapContentWidth(Alignment.Start).focusRequester(focusRequester),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        // An underline, as the colours below assume: the default pill fills with the same light
        // colour as the selected tab's label once the row has focus, and the label vanishes.
        indicator = { positions, focused ->
            positions.getOrNull(selected)?.let { position ->
                TabRowDefaults.UnderlinedIndicator(currentTabPosition = position, doesTabRowHaveFocus = focused)
            }
        },
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

/**
 * [TvTitlePage]'s own scrollable body's tag — its tab row above carries a
 * horizontal scroll capability of its own on a television-wide tab strip, so
 * a test asking for "the" scrollable node by `hasScrollAction()` alone finds
 * two; this is the one that is actually the page's own body, whichever tab
 * is showing.
 */
internal const val TvTitlePageBodyTag = "tv-title-page-body"

/**
 * One tab's own scrollable body — a film page's Cast/Similar/Details, a
 * series page's About/Cast/Similar — padded and scrolled the same way
 * whichever tab is showing, so each tab's file only supplies what actually
 * differs between them.
 *
 * [testTag] names the scrollable node for a test that needs to tell it apart
 * from [TvSectionTabs]' own horizontal scroll on a television-wide tab strip
 * — `hasScrollAction()` alone would otherwise find both.
 */
@Composable
internal fun TvTabBody(
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .let { if (testTag != null) it.testTag(testTag) else it }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal, vertical = Spacing.medium),
    ) {
        content()
    }
}
