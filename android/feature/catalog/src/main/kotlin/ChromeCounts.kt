package catalog

import model.WatchSnapshot

/**
 * The rail's own `n-watchlist`/`n-continue` and the departments bar's own
 * `n-movies`/`n-series`/…/`n-collections` — one small port of `app.js`'s
 * `refreshShelfCounts` (114-135) and its `onData` handler (416-421), read
 * fresh from the shelves and this viewer's watch state rather than kept
 * beside them. No Compose import here on purpose: every reader of this —
 * the tablet's rail beside a pushed frame, its bar over the root, the
 * television's own rail and departments bar — wants the same numbers, and
 * a plain data class is what lets every one of them reach for it without
 * any of them owning where it came from.
 */
data class ChromeCounts(
    val myList: Int,
    val continueWatching: Int,
    private val perShelf: Map<Department, Int>,
    val collections: Int,
) {
    /** A department pill's own count — every shelf by its department, Collections from the viewer's own lists, Home none. */
    fun departmentCount(tab: CatalogTab): Int? =
        when (tab) {
            CatalogTab.Home -> null
            is CatalogTab.Dept -> perShelf[tab.department]
            is CatalogTab.Kept -> collections.takeIf { tab.kind == KeptKind.COLLECTIONS }
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
        perShelf =
            shelves.associate {
                it.department to
                    when (it.department) {
                        Department.DOCUMENTARIES -> documentaryCountOf(it.entries)
                        Department.MOVIES, Department.SERIES, Department.ANIME, Department.TUTORIALS -> it.entries.size
                    }
            },
        collections = watch.collections.size,
    )

/** The avatar's own initial — the web's `initialOf`: the chosen name's first letter, uppercased, or "?" with nothing to read it from. */
fun profileInitial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"
