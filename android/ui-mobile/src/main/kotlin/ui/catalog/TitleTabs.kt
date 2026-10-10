package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.entrance

/**
 * The tab last chosen, kept by its own label rather than its position, and
 * surviving recomposition and rotation ([rememberSaveable]) — a page
 * rebuilt once a watch-state update lands, or once Cast arrives and inserts
 * itself ahead of a later tab, must not throw the viewer back to the first
 * tab, as `tabs.js`'s own doc comment records for the web. [labels] missing the chosen one (Cast disappearing because
 * credits came back empty) falls back to the first tab rather than nothing.
 *
 * Split from the row that reads it ([TitleTabRow]) so a page whose Episodes
 * tab is itself a long, lazily-scrolled list (the series page) can put that
 * list's rows straight into its own [androidx.compose.foundation.lazy.LazyColumn]
 * rather than nesting one scrollable list inside another.
 */
@Composable
internal fun rememberChosenTab(labels: List<String>): Pair<String, (String) -> Unit> {
    require(labels.isNotEmpty()) { "a title page always has at least one tab" }
    var chosen by rememberSaveable { mutableStateOf(labels.first()) }
    val shown = if (chosen in labels) chosen else labels.first()
    return shown to { label: String -> chosen = label }
}

@Composable
internal fun TitleTabRow(
    labels: List<String>,
    shown: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No `containerColor`, so `ScrollableTabRow` painted its M3 default
    // (`surface`) — invisible while the page beneath it was `surface` too,
    // a full-width band once the page moved to `background` (`ui.pageGround`).
    // `Transparent` lets the page's own ground show through instead, the
    // way `.tab-list` draws no fill of its own (`title-page.css:120`).
    ScrollableTabRow(
        selectedTabIndex = labels.indexOf(shown),
        modifier = modifier,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
    ) {
        labels.forEach { label ->
            Tab(
                selected = label == shown,
                onClick = { onSelect(label) },
                text = { Text(label) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

/** `.tab-panel { animation: panel-in 520ms var(--ease) }`, from 8px below (`title-page.css:133-134`). */
internal const val PANEL_IN_MS = 520
internal val PANEL_IN_RISE = 8.dp

/**
 * A title page's own tab row and body together — Overview/Cast/Similar/
 * Details on a film, whose bodies are all short enough to sit inside the
 * page's own scroll without needing a lazily-scrolled list of their own. A
 * Compose port of `tabs.js`'s own `tabbed`.
 */
@Composable
internal fun TitleTabs(
    labels: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val (shown, onSelect) = rememberChosenTab(labels)
    Column(modifier = modifier) {
        TitleTabRow(labels, shown, onSelect)
        // The web replays the rise whenever a panel is shown (`tabs.js:85-97`
        // un-hides it). A new tab is a new key and so a new node, which plays;
        // a redraw keeps the key and the node; a page come back to is
        // composed anew and plays it again, as on the web.
        key(shown) {
            Box(Modifier.entrance(PANEL_IN_MS, fromY = PANEL_IN_RISE)) { content(shown) }
        }
    }
}
