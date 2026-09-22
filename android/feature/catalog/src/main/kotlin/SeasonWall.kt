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
 */
data class SeasonPlate(
    val title: String,
    val caption: String,
    val posterKey: String?,
    val division: Division,
)

/**
 * The seasons wall for [collection], or `null` when there is nothing to
 * wall.
 *
 * A course is never walled: it drills into chapters, not seasons, and
 * those are shown as the nested tree they already are. Nor is a show with
 * one season — a wall of one plate is a tap that says nothing a direct
 * list of its episodes did not already say, so that case is left to fall
 * through to the flat list too.
 */
fun seasonPlatesOf(collection: Entry.Collection): List<SeasonPlate>? {
    if (collection.kind != CollectionKind.SHOW || collection.divisions.size <= 1) return null
    return collection.divisions.map { division ->
        val episodes = division.walk().sumOf { it.items.size }
        SeasonPlate(
            title = division.title,
            caption = "$episodes ${plural(episodes, "episode")}",
            posterKey = collection.posterKey?.let { showKey ->
                division.season?.let { season -> seasonPosterKey(showKey, season) }
            },
            division = division,
        )
    }
}

/**
 * The key a season's artwork is held under, beside its show's own poster
 * key — mirrors `mlib_spec::package::season_poster_key` on the Rust side,
 * which is what the fetch that downloads this artwork names it with.
 */
fun seasonPosterKey(showPosterKey: String, season: Int): String = "$showPosterKey-s$season"

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"
