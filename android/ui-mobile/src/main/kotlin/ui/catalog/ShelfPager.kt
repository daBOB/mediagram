package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import catalog.PageLink
import catalog.pageLinks
import designsystem.Spacing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * The row under a paged shelf — `pager` in the web's `pager.js`: ‹ Prev, the
 * page numbers [pageLinks] chooses with a gap where pages are left out, and
 * Next ›. Nothing at all for a single page. Wraps rather than scrolls, so a
 * phone never hides a page number off the edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelfPager(
    page: Int,
    pages: Int,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val links = pageLinks(page, pages)
    if (links.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.medium),
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text("‹ Prev") }
        for (link in links) {
            when (link) {
                PageLink.Gap -> Text("…", modifier = Modifier.padding(horizontal = Spacing.small))
                is PageLink.To ->
                    if (link.page == page) {
                        // The page on screen, marked rather than offered.
                        Text(
                            text = link.page.toString(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Spacing.medium).semantics { selected = true },
                        )
                    } else {
                        TextButton(onClick = { onPage(link.page) }, modifier = Modifier.semantics { role = Role.Button }) {
                            Text(link.page.toString())
                        }
                    }
            }
        }
        TextButton(onClick = { onPage(page + 1) }, enabled = page < pages) { Text("Next ›") }
    }
}

/**
 * A new page starts at its top, as the web's does; the same page drawn again
 * — a rotation, a catalog refresh — keeps the viewer where they were.
 */
@Composable
internal fun ScrollToTopOnNewPage(
    page: Int,
    scrollToTop: suspend () -> Unit,
) {
    var last by rememberSaveable { mutableIntStateOf(page) }
    LaunchedEffect(page) {
        if (page != last) {
            scrollToTop()
            last = page
        }
    }
}
