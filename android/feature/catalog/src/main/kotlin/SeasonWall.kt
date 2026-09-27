package catalog

/**
 * One season's plate on a show's wall.
 *
 * [posterPath] is the season's own resolved artwork — the core carries it on
 * every episode of the division already (`MediaSet.seasonPosterPath`), so
 * the plate reads it off the first episode that has one rather than
 * deriving a key and asking the core to resolve it. `null` for a division
 * with no season number — "Episodes", specials — gated on [Division.season]
 * itself rather than trusted to an episode never carrying one for such a
 * division, and for a numbered season with no poster of its own; either way
 * the plate falls back to the show's poster, the same way any other missing
 * poster does.
 *
 * [watched] is true once every episode under it is — the only sense in
 * which a season is watched, ported from `shelf-view.js`'s `seasonGrid`.
 */
data class SeasonPlate(
    val title: String,
    val caption: String,
    val posterPath: String?,
    val division: Division,
    val watched: Boolean,
)

/**
 * The seasons wall for [collection], or `null` when there is nothing to
 * wall. [watchedIds] is this viewer's finished sets, for [SeasonPlate.watched].
 *
 * A course is never walled: it drills into chapters, not seasons, and
 * those are shown as the nested tree they already are. Nor is a show with
 * one season — a wall of one plate is a tap that says nothing a direct
 * list of its episodes did not already say, so that case is left to fall
 * through to the flat list too.
 */
fun seasonPlatesOf(
    collection: Entry.Collection,
    watchedIds: Set<String> = emptySet(),
): List<SeasonPlate>? {
    if (collection.kind != CollectionKind.SHOW || collection.divisions.size <= 1) return null
    return collection.divisions.map { division ->
        val items = division.walk().flatMap { it.items }.toList()
        SeasonPlate(
            title = division.title,
            caption = "${items.size} ${plural(items.size, "episode")}",
            posterPath = division.season?.let { items.firstNotNullOfOrNull { item -> item.seasonPosterPath } },
            division = division,
            watched = items.isNotEmpty() && items.all { it.setId in watchedIds },
        )
    }
}

private fun plural(
    count: Int,
    word: String,
): String = if (count == 1) word else "${word}s"
