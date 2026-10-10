package ui.chrome

import androidx.compose.runtime.compositionLocalOf
import catalog.ChromeCounts
import stats.NEW_ACHIEVEMENT
import ui.common.RailItem

/**
 * What the rail needs beside its own click handlers — computed once where
 * the shelves are ([ui.LibraryBranches]) and read wherever the rail renders,
 * root or pushed frame alike, through [LocalRailData] rather than threaded
 * across every branch in between.
 */
data class RailData(
    val counts: ChromeCounts,
    val tally: List<String>,
    val onHome: () -> Unit,
    /** Whether something the chosen profile earned has not been shown on this device: the Stats row's dot. */
    val newAchievement: Boolean = false,
) {
    companion object {
        val Empty = RailData(ChromeCounts.Empty, emptyList(), onHome = {})
    }
}

/** What the dot on [item] says to a screen reader, or `null` when it wears none — only Stats ever does. */
internal fun RailData.dotFor(item: RailItem): String? = if (item == RailItem.STATS && newAchievement) NEW_ACHIEVEMENT else null

// compositionLocalOf, not static: this changes on every watch-state write
// (a position saved, a watchlist toggle), and a player frame sits under the
// same provider — static would recompose that whole subtree on every write
// even though nothing under the player reads this.
val LocalRailData = compositionLocalOf { RailData.Empty }
