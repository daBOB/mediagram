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

/**
 * Where each destination in web 0.62.1's masthead belongs on a screen too
 * narrow to print `index.html`'s two navigations side by side: the
 * departments (`nav.departments` — Home, Movies, Series, Tutorials,
 * Collections) as the visible tabs, and the rail's own utilities (My List,
 * Continue watching, Latest, Genres, Settings) reachable once each from the
 * overflow menu that already carries System, Settings, TMDB key and Start
 * over — see [android.ui.OverflowMenu]. `catalogTabsOf` above is untouched:
 * both phone and TV screens still read it, and this is additive for the
 * phase that rebuilds those screens onto the new split.
 *
 * Web has no Documentaries counterpart here: that department has no
 * Android kind of its own (a prior, already-recorded difference), so it is
 * not part of either list.
 */
data class MastheadSplit(val departments: List<String>, val utilities: List<UtilityDestination>)

/** A utility destination, web 0.62.1's own order — `index.html`'s rail-nav, minus Settings' spot before System. */
enum class UtilityDestination(val label: String) {
    MY_LIST("My List"),
    CONTINUE_WATCHING("Continue watching"),
    LATEST("Latest"),
    GENRES("Genres"),
    SETTINGS("Settings"),
}

/** [MastheadSplit] for [shelves] — the department tab row plus the five utilities, every one reachable once. */
fun mastheadSplitOf(shelves: List<Shelf>): MastheadSplit =
    MastheadSplit(
        departments = listOf(HOME) + shelves.map(Shelf::title) + KeptKind.COLLECTIONS.label,
        utilities = UtilityDestination.entries,
    )
