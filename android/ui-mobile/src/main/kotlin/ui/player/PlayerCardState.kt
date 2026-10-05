package ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

/**
 * The card's small menus, one open at a time. [SubtitleStyle] has no button
 * of its own: the subtitle menu's "Style…" row opens it, above the same ▾.
 */
internal enum class CardMenu { Subtitles, SubtitleStyle, Speed, Audio, Framing }

/**
 * What the card has open and where its pieces sit — one holder, read by the
 * screen, the card, its menus and Back, so none of them keeps its own copy of
 * the answer. Lives as long as the screen's composition: a rotation closes
 * whatever was open, which is what a tap away would have done.
 */
@Stable
internal class PlayerCardState {
    var menu by mutableStateOf<CardMenu?>(null)
        private set

    var sidebarOpen by mutableStateOf(false)

    /** The card's own bounds in root coordinates, once laid out — what a menu stays inside. */
    var bounds by mutableStateOf<IntRect?>(null)

    /** Where the stage sits in the root; the notes column can push it off the origin. */
    var origin by mutableStateOf(IntOffset.Zero)

    private val anchors = mutableStateMapOf<CardMenu, IntRect>()

    /** Whether Back, and the controls' fade, have something of the card's to answer to first. */
    val somethingOpen: Boolean get() = menu != null || sidebarOpen

    /** A menu's button: opens it, replacing any other, or closes it when it is already the one open. */
    fun toggle(next: CardMenu) {
        menu = if (menu == next) null else next
    }

    /** Moves the open menu to [next] in place — the subtitle menu's "Style…" row. */
    fun switchTo(next: CardMenu) {
        menu = next
    }

    /** A value was chosen: the menu has done its job. */
    fun closeMenu() {
        menu = null
    }

    /** ☰: closes any menu, so the sidebar is the one thing open, or puts the sidebar away. */
    fun toggleSidebar() {
        menu = null
        sidebarOpen = !sidebarOpen
    }

    /** Back while [somethingOpen]: the menu first, since it opened last, then the sidebar. */
    fun closeTopmost() {
        if (menu != null) menu = null else sidebarOpen = false
    }

    /** A tap on the picture with a menu open closes the menu instead of the card; true when it did. */
    fun dismissMenu(): Boolean {
        if (menu == null) return false
        menu = null
        return true
    }

    fun anchor(menu: CardMenu, bounds: IntRect) {
        anchors[menu] = bounds
    }

    /** What [menu] is drawn above: its own button, or for the style panel the ▾ that led to it. */
    fun anchorOf(menu: CardMenu): IntRect? = anchors[if (menu == CardMenu.SubtitleStyle) CardMenu.Subtitles else menu]
}
