package catalog

/**
 * One entry of the library's masthead, keyed the way the web keys its nav —
 * by section id (`data-section`), never by position. Home is the first pill
 * and where the app opens, as the web player's start page is; each shelf's
 * department follows it; then the kept shelves, of which only Collections is
 * a pill — Continue and My List are the rail's own rows.
 *
 * [key] is the web's own section id ("home", "movies", …, "watchlist",
 * "collections"), and what a chosen tab is saved by: a library that gains or
 * loses a department moves every later tab's position, and only a key
 * survives that.
 */
sealed interface CatalogTab {
    val label: String
    val key: String

    data object Home : CatalogTab {
        override val label = "Home"
        override val key = "home"
    }

    data class Dept(val department: Department) : CatalogTab {
        override val label: String get() = department.label
        override val key: String get() = department.name.lowercase()
    }

    data class Kept(val kind: KeptKind) : CatalogTab {
        override val label: String get() = kind.label
        override val key: String get() = kind.name.lowercase()
    }
}

/**
 * The department pills both surfaces draw: Home, one per shelf, then
 * Collections — the web's `nav.departments` (`index.html`). The rail's own
 * entries are ui-common's `RailItem`, which the phone's overflow menu also
 * offers.
 */
fun mastheadTabsOf(shelves: List<Shelf>): List<CatalogTab> =
    listOf(CatalogTab.Home) + shelves.map { CatalogTab.Dept(it.department) } + CatalogTab.Kept(KeptKind.COLLECTIONS)

/**
 * The tab [key] names over [shelves], or Home when it names none: an unknown
 * key, or a department this library no longer shelves — a refresh that
 * dropped it, or a save from a library that had it.
 */
fun catalogTabOf(
    key: String,
    shelves: List<Shelf>,
): CatalogTab =
    (shelves.map { CatalogTab.Dept(it.department) } + KeptKind.entries.map(CatalogTab::Kept))
        .firstOrNull { it.key == key }
        ?: CatalogTab.Home
