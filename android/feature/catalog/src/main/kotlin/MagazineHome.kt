package catalog

import model.MediaSet
import model.WatchSnapshot

/**
 * The magazine home page's own data, assembled from the shelves and watch
 * state the plain grid already has — a port of `web/public/app.js`'s
 * `viewHome`, which is where the web wires `homeEditorial` and
 * `resumeCards` to the library.
 *
 * [resumeCards] merges Continue and Next up into the one landscape strip
 * the web draws, rather than the plain grid's two separate rows — see
 * `plans/260925-2245-android-magazine-parity`'s decision to match the web.
 * [recentlyAdded] is what "Recently added" shows; it is also `onRow` for
 * [editorial], so "This month" never repeats it.
 */
data class MagazineHome(
    val editorial: EditorialPicks,
    val resumeCards: List<SetCard>,
    val recentlyAdded: List<MediaSet>,
    /** [recentlyAdded], wrapped as the row the magazine layout's grid draws — the web's "Recently added", not the plain shelf's "Latest films". */
    val recentlyAddedRow: HomeRow,
)

fun magazineHomeOf(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    editorsChoice: String?,
    now: Long,
    heldIds: Set<String> = emptySet(),
    limit: Int = HOME_ROW_LIMIT,
    /** Overrides [limit] for "Recently added" only — Continue/Next up's own underway cards still use [limit] alone. */
    recentLimit: Int = limit,
): MagazineHome {
    val byId = indexById(shelves)
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }

    // Films only, the way the web's `library.movies` is: the household
    // decided parity over a series-eligible cover when the plan asked.
    val movieEntries = shelves.firstOrNull { it.title == "Movies" }?.entries.orEmpty().filterIsInstance<Entry.Film>()
    val movies = movieEntries.map { it.set }
    val recentlyAdded = movies.sortedByDescending(MediaSet::addedAt).take(recentLimit)
    val onRow = recentlyAdded.mapTo(HashSet(), MediaSet::setId)

    val editorial =
        homeEditorial(
            movies = movies,
            byId = byId,
            isWatched = { it in watchedIds },
            editorsChoice = editorsChoice,
            now = now,
            onRow = onRow,
        )

    val underway = underwayOf(collectionsForNextUp(shelves), byId, watch, limit)
    val resumeCards = resumeCardsOf(underway.continues, underway.nextUp, watch, heldIds)

    val recentlyAddedRow =
        HomeRow(
            title = "Recently added",
            seeAll = "Movies",
            total = movieEntries.size,
            content = RowContent.Entries(recentlyAdded.map(Entry::Film)),
        )

    return MagazineHome(
        editorial = editorial,
        resumeCards = resumeCards,
        recentlyAdded = recentlyAdded,
        recentlyAddedRow = recentlyAddedRow,
    )
}
