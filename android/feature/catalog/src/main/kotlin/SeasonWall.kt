package catalog

/**
 * One season's plate on a show's wall.
 *
 * [posterKey] is the season's own artwork key, derived from the show's —
 * `<showKey>-s<n>`, the same rule the index's fetch already downloads
 * artwork under (`mlib_spec::package::season_poster_key`). It is `null`
 * for a division with no season number — "Episodes", specials — and for
 * a show that has no poster key of its own to build one from; either way
 * the plate falls back to the show's poster, the same way any other
 * missing poster does.
 *
 * [watched] is true once every episode under it is — the only sense in
 * which a season is watched, ported from `shelf-view.js`'s `seasonGrid`.
 */
data class SeasonPlate(
    val title: String,
    val caption: String,
    val posterKey: String?,
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
            posterKey =
                collection.posterKey?.let { showKey ->
                    division.season?.let { season -> seasonPosterKey(showKey, season) }
                },
            division = division,
            watched = items.isNotEmpty() && items.all { it.setId in watchedIds },
        )
    }
}

/**
 * The key a season's artwork is held under, beside its show's own poster
 * key — mirrors `mlib_spec::package::season_poster_key` on the Rust side,
 * which is what the fetch that downloads this artwork names it with.
 */
fun seasonPosterKey(
    showPosterKey: String,
    season: Int,
): String = "$showPosterKey-s$season"

private fun plural(
    count: Int,
    word: String,
): String = if (count == 1) word else "${word}s"
