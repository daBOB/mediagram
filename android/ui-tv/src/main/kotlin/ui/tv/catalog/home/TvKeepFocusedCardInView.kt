package ui.tv.catalog.home

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Scrolls a focused card back into its row's view when the row reorders
 * under it — a played title's own position write moving it to the front of
 * Continue, a new upload pushing Recently added along. The card keeps focus
 * because its row keys it by title; the row's own scroll does not follow,
 * since no focus move happened to ask it to, and the card would sit half
 * past the row's edge.
 *
 * [index] is the card's place in its row; only a change of it while this
 * card holds focus asks for the scroll.
 */
@Composable
internal fun Modifier.keepsInViewWhenMoved(index: Int): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(index) { if (focused) requester.bringIntoView() }
    return bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }
}
