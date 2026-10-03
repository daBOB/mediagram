package ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.ChromeCounts
import designsystem.LocalCatalogueTones
import designsystem.Radius
import ui.RailItem

/**
 * What the rail needs beside its own click handlers — computed once where
 * the shelves are ([ui.LibraryBranches]) and read wherever the rail renders,
 * root or pushed frame alike, through [LocalRailData] rather than threaded
 * across every branch in between.
 */
data class RailData(val counts: ChromeCounts, val tally: List<String>, val onHome: () -> Unit) {
    companion object {
        val Empty = RailData(ChromeCounts.Empty, emptyList(), onHome = {})
    }
}

// compositionLocalOf, not static: this changes on every watch-state write
// (a position saved, a watchlist toggle), and a player frame sits under the
// same provider — static would recompose that whole subtree on every write
// even though nothing under the player reads this.
val LocalRailData = compositionLocalOf { RailData.Empty }

/** The window width the rail itself narrows at — the web's `--rail-width` breakpoint, `shell.css:169`. */
private val RailNarrowBreakpoint = 1180.dp
private val RailWidthNarrow = 184.dp
private val RailWidthWide = 224.dp

/**
 * The left rail: wordmark, the viewer's own shelves and utilities, then the
 * library's own tally — `index.html`'s `aside.library-rail` (46-70), read
 * fresh from [LocalRailData] wherever this renders. EXPANDED-only; every
 * caller decides that for itself, since a pushed frame and the root catalog
 * reach this from different layouts.
 */
@Composable
internal fun LibraryRail(
    active: RailItem?,
    onHome: () -> Unit,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rail = LocalRailData.current
    val tones = LocalCatalogueTones.current
    val narrow = LocalConfiguration.current.screenWidthDp.dp <= RailNarrowBreakpoint
    val width = if (narrow) RailWidthNarrow else RailWidthWide

    Column(
        modifier =
            modifier
                .width(width)
                .fillMaxHeight()
                .background(tones.sidebar)
                // Start/top/bottom only: the rail sits against the window's
                // own start edge, and an end-side cutout/gesture inset
                // belongs to whatever borders the rail on its other side,
                // not to the rail itself.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 28.dp),
    ) {
        Wordmark(
            // Android's own Fraunces metrics run wider than the web's at the
            // same nominal size — 27sp wraps inside the 184dp rail, which
            // `shell.css`'s own 1.7rem never does in a browser. The compact
            // header's own narrower tier (23sp) is reused here rather than
            // a third number invented just for this width.
            fontSize = if (narrow) 23.sp else WordmarkSize,
            modifier =
                Modifier
                    .padding(start = 10.dp, bottom = 36.dp)
                    .clickable(onClick = onHome)
                    .semantics { contentDescription = "mediagram — home" },
        )
        for (item in listOf(RailItem.MY_LIST, RailItem.CONTINUE_WATCHING, RailItem.LATEST, RailItem.GENRES, RailItem.STATS, RailItem.SETTINGS)) {
            RailRow(item = item, count = countFor(item, rail.counts), active = item == active, onSelect = onSelect)
        }
        RailRow(item = RailItem.SYSTEM, count = null, active = RailItem.SYSTEM == active, onSelect = onSelect, apart = true)

        if (rail.tally.isNotEmpty()) {
            // The web's `.rail-masthead` (`shell.css:64-73`): Geist 500
            // 10sp, tracked 0.34em, line height double.
            val tallyStyle =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.34.em,
                    lineHeight = 2.em,
                )
            HorizontalDivider(modifier = Modifier.padding(top = 28.dp, start = 10.dp, end = 10.dp), color = tones.ruleSoft)
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 24.dp)) {
                for (line in rail.tally) {
                    Text(text = line.uppercase(), style = tallyStyle, color = tones.quiet)
                }
            }
        }
    }
}

/** [ChromeCounts] carries a number for the two kept rows only — the rest read nothing after their name, the same as the web's own rail. */
private fun countFor(
    item: RailItem,
    counts: ChromeCounts,
): Int? =
    when (item) {
        RailItem.MY_LIST -> counts.myList
        RailItem.CONTINUE_WATCHING -> counts.continueWatching
        else -> null
    }

@Composable
private fun RailRow(
    item: RailItem,
    count: Int?,
    active: Boolean,
    onSelect: (RailItem) -> Unit,
    apart: Boolean = false,
) {
    val tones = LocalCatalogueTones.current
    val ink = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .padding(top = if (apart) 12.dp else 0.dp)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Radius.control))
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)) else Modifier)
                .selectable(selected = active, onClick = { onSelect(item) }, role = Role.Tab)
                // The icon carries no description of its own; merging lets a
                // screen reader read the label and the count as one row
                // rather than as two unrelated nodes a swipe lands on apart.
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(painter = painterResource(item.icon), contentDescription = null, tint = ink, modifier = Modifier.size(21.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = tones.quiet)
        }
    }
}
