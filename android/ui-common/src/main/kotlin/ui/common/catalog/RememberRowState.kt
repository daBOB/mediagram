package ui.common.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * A row's scroll state that keeps the row at its start when new items arrive
 * in front of it.
 *
 * A keyed `LazyRow` holds on to whichever item was first on screen when its
 * list changes, so a show that moves to the front of a newest-first row lands
 * off-screen to the left: the tablet's "New episodes" row kept opening on
 * Bones while Seinfeld and Boston Legal, newer, sat before it. A row still at
 * its start shows its new start instead, as the web player's rebuilt row
 * does. One the viewer is in keeps its place: scrolled into, or, on the
 * television, holding focus ([inUse]) — moving it would slide the focused
 * tile away under the remote.
 *
 * [keys] are the row's item keys in order. [state] is for a row that needs
 * its own (the television's carry a cache window).
 */
@Composable
fun rememberRowState(
    keys: List<Any>,
    state: LazyListState = rememberLazyListState(),
    inUse: () -> Boolean = { false },
): LazyListState {
    val shown = remember { ShownKeys(keys) }
    // After the composition holding the new keys has applied and before this
    // frame measures it, so `state` still describes the row as last drawn,
    // and a composition that is thrown away changes nothing.
    SideEffect {
        if (shown.keys == keys) return@SideEffect
        shown.keys = keys
        val atStart = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        // Replaces the key anchoring for the next measure only.
        if (atStart && !inUse()) state.requestScrollToItem(0)
    }
    return state
}

/** The keys the row last drew, to tell a changed list from a recomposition. */
private class ShownKeys(
    var keys: List<Any>,
)
