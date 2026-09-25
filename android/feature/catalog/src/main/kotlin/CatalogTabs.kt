package catalog

/**
 * The masthead the web player has: the three catalog shelves, then the
 * three that come from what has been watched rather than from the catalog —
 * `index.html`'s own order, Home, Movies, Series, Tutorials, Continue,
 * Watchlist, Collections.
 *
 * Home is the first entry and the one the app opens on, as the web player's
 * start page is; the catalog shelves follow it, and the three kept entries
 * follow those — so index 0 is Home, shelf n is index n + 1, and
 * [firstKept] is the first of the three.
 */
data class CatalogTabs(
    val titles: List<String>,
    val firstKept: Int,
)

/** The masthead's tabs for [shelves] — see [CatalogTabs]. */
fun catalogTabsOf(shelves: List<Shelf>): CatalogTabs =
    CatalogTabs(
        titles = listOf(HOME) + shelves.map(Shelf::title) + KEPT_TITLES,
        firstKept = 1 + shelves.size,
    )

/** The first thing in the masthead, and not a shelf. */
const val HOME = "Home"

/** The three kept labels, in the web's own order — `index.html`'s Continue, Watchlist, Collections. */
private val KEPT_TITLES: List<String> = KeptKind.entries.map(KeptKind::label)
