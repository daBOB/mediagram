package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * What the viewer has open over the picture, apart from the controls
 * themselves: the statistics, the list and Kids dialogs, a card menu and the
 * episode list. Saved, as on the phone: a configuration change is not a
 * viewer asking for any of them to go.
 */
internal class TvPlayerOverlayState(
    stats: MutableState<Boolean>,
    list: MutableState<Boolean>,
    kids: MutableState<Boolean>,
    menuState: MutableState<TvCardMenu?>,
    sidebar: MutableState<Boolean>,
) {
    var statsShown by stats
    var choosingList by list
    var choosingKids by kids
    var menu by menuState
    var sidebarOpen by sidebar

    /** Closes the dialogs that file or mark a title, which go with the title or its marks. */
    fun closeMarkDialogs() {
        choosingList = false
        choosingKids = false
    }

    /** Closes whatever holds the D-pad for itself: a menu first, otherwise the episode list. */
    fun closePanel() {
        if (menu != null) menu = null else sidebarOpen = false
    }

    /** Closes the menu and the episode list both. */
    fun closeAllPanels() {
        menu = null
        sidebarOpen = false
    }
}

@Composable
internal fun rememberTvPlayerOverlayState() =
    TvPlayerOverlayState(
        stats = rememberSaveable { mutableStateOf(false) },
        list = rememberSaveable { mutableStateOf(false) },
        kids = rememberSaveable { mutableStateOf(false) },
        menuState = rememberSaveable { mutableStateOf<TvCardMenu?>(null) },
        sidebar = rememberSaveable { mutableStateOf(false) },
    )
