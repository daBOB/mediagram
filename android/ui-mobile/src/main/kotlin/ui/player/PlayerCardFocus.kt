package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/*
 * Where screen-reader focus goes as the card opens and closes things, as the
 * web moves focus into a menu or the sidebar and back to its opener on Esc.
 * TalkBack follows input focus, so moving that is what moves the reader.
 */

/**
 * Takes focus to this target; does nothing when it is not on screen — the
 * card was away, so there is no opener to go back to.
 */
internal fun FocusRequester.requestFocusIfOnScreen() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        // Not attached to any composed node.
    }
}

/**
 * Hands focus back to the button that opened a menu, or the sidebar, when it
 * closes — unless the close was the first half of opening something else
 * (☰ closes a menu as it opens the sidebar), which has its own place to put
 * it. A viewer who closed it by touch gets the same: focus only decides where
 * the next swipe of a screen reader starts.
 */
@Composable
internal fun CardFocusReturn(card: PlayerCardState) {
    var lastMenu by remember { mutableStateOf<CardMenu?>(null) }
    LaunchedEffect(card.menu) {
        val now = card.menu
        if (now == null && !card.sidebarOpen) lastMenu?.let { card.focusOf(it).requestFocusIfOnScreen() }
        lastMenu = now
    }
    var sidebarWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(card.sidebarOpen) {
        if (!card.sidebarOpen && sidebarWasOpen) card.focusOf(null).requestFocusIfOnScreen()
        sidebarWasOpen = card.sidebarOpen
    }
}
