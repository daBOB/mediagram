package ui.chrome

import catalog.DOCUMENTARIES
import catalog.HOME
import catalog.KeptKind
import catalog.Shelf
import catalog.continueWall
import catalog.documentaryCountOf
import catalog.watchlistWall
import model.WatchSnapshot

/**
 * The rail's own `n-watchlist`/`n-continue` and the departments bar's own
 * `n-movies`/`n-series`/…/`n-collections` — one small port of `app.js`'s
 * `refreshShelfCounts` (114-135) and its `onData` handler (416-421), read
 * fresh from the shelves and this viewer's watch state rather than kept
 * beside them. No Compose import here on purpose: every reader of this —
 * the rail beside a pushed frame, the bar over the root — wants the same
 * numbers, and a plain data class is what lets both reach for it without
 * either owning where it came from.
 */
data class ChromeCounts(
    val myList: Int,
    val continueWatching: Int,
    private val perShelf: Map<String, Int>,
    val collections: Int,
) {
    /** A department pill's own count — every shelf by its title, Collections from the viewer's own lists, Home none. */
    fun departmentCount(title: String): Int? =
        when (title) {
            HOME -> null
            KeptKind.COLLECTIONS.label -> collections
            else -> perShelf[title]
        }

    companion object {
        val Empty = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0)
    }
}

/** [ChromeCounts] for [shelves] and [watch] — see its own doc for where each number comes from. */
fun chromeCountsOf(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
): ChromeCounts =
    ChromeCounts(
        myList = watchlistWall(shelves, watch).size,
        continueWatching = continueWall(shelves, watch).size,
        // Every other pill counts its own cards — a show or a course is one
        // card each. Documentaries' pill counts documentaries themselves,
        // the way its own tab does on the web, so a folder there counts
        // what it holds rather than standing for one.
        perShelf = shelves.associate { it.title to (if (it.title == DOCUMENTARIES) documentaryCountOf(it.entries) else it.entries.size) },
        collections = watch.collections.size,
    )

/** The avatar's own initial — the web's `initialOf`: the chosen name's first letter, uppercased, or "?" with nothing to read it from. */
fun profileInitial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"
