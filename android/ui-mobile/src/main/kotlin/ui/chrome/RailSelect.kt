package ui.chrome

import ui.Destination
import ui.common.MenuActions
import ui.common.RailItem

/** Which rail row, if any, is where [destination] already is — Latest, Genres and Stats are the only pushed frames the rail also names; My List/Continue/Settings/System resolve to their own root tab or their own frame, never this one. */
internal fun railItemFor(destination: Destination): RailItem? =
    when (destination) {
        Destination.Latest -> RailItem.LATEST
        Destination.Genres -> RailItem.GENRES
        Destination.Stats -> RailItem.STATS
        else -> null
    }

/** A rail tap's own effect, the same wherever the rail renders — [ui.chrome.LibraryHome] reuses this rather than repeating the same seven-way branch. */
internal fun railSelect(
    item: RailItem,
    browse: BrowseActions,
    menu: MenuActions,
) {
    when (item) {
        RailItem.MY_LIST -> browse.onMyList()
        RailItem.CONTINUE_WATCHING -> browse.onContinueWatching()
        RailItem.LATEST -> browse.onLatest()
        RailItem.GENRES -> browse.onGenres()
        RailItem.STATS -> browse.onStats()
        RailItem.SETTINGS -> menu.onSettings()
        RailItem.SYSTEM -> menu.onSystem()
    }
}
