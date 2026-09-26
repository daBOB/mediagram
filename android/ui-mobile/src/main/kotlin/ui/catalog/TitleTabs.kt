package ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The tab last chosen, kept by its own label rather than its position, and
 * surviving recomposition and rotation ([rememberSaveable]) — a page
 * rebuilt once a watch-state update lands, or once Cast arrives and inserts
 * itself ahead of a later tab, must not throw the viewer back to the first
 * tab, the finding `tabs.js`'s own doc comment records and this phase was
 * asked to port. [labels] missing the chosen one (Cast disappearing because
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
    ScrollableTabRow(selectedTabIndex = labels.indexOf(shown), modifier = modifier, edgePadding = 0.dp) {
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
        content(shown)
    }
}
