package catalog

import model.MediaSet

/** How many titles a title's page offers under "Similar". */
private const val SIMILAR_LIMIT = 12

/**
 * "Similar" on a title's page, ranked from what the catalog already knows —
 * ported from `similarTo` in the web's `similar.js`.
 *
 * The same franchise first, then the most genres in common, then the more
 * popular. Unwatched titles come before watched ones: a recommendation of
 * something already seen is a reminder, not a suggestion.
 *
 * @param title the page's title (a film, or a show's first episode)
 * @param candidates others of the same kind; [title] itself is skipped
 * @param seen whether the viewer has watched a candidate
 */
fun similarTo(
    title: MediaSet,
    candidates: List<MediaSet>,
    seen: (MediaSet) -> Boolean = { false },
): List<MediaSet> {
    val own = title.genres.toHashSet()
    val franchise = title.collectionId
    return candidates
        .asSequence()
        .filter { it.setId != title.setId }
        .map { item ->
            SimilarRow(
                item = item,
                same = if (franchise != null && item.collectionId == franchise) 1 else 0,
                shared = item.genres.count { it in own },
                seen = if (seen(item)) 1 else 0,
            )
        }
        .filter { it.same > 0 || it.shared > 0 }
        .sortedWith(
            compareByDescending<SimilarRow> { it.same }
                .thenBy { it.seen }
                .thenByDescending { it.shared }
                .thenByDescending { it.item.popularity ?: 0.0 },
        )
        .take(SIMILAR_LIMIT)
        .map { it.item }
        .toList()
}

private data class SimilarRow(val item: MediaSet, val same: Int, val shared: Int, val seen: Int)
