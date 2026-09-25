package catalog

import model.MediaSet
import kotlin.math.floor

/**
 * What the magazine home page features, decided before anything is drawn.
 *
 * A pure port of `web/public/lib/catalog/editorial-picks.js` and
 * `featured-picks.js`: every pick comes from a fact the library holds — a
 * pin, a provider score, a provider popularity figure, an arrival date —
 * and every label says which fact it was. Nothing here invents a reason to
 * watch something, and nothing here draws — `HomeScreen.kt` does that.
 *
 * `web/test/fixtures/editorial-picks/` pins exact seeded outputs so this
 * port and the web's own tests cannot drift apart; see [seededRandom].
 */

/** How many films the cover story rotates through. */
const val COVER_COUNT = 5

/** How many films one opening of the cover's own shuffle draws from. */
const val FEATURED_COUNT = 12

/** The staff pick rotates among this many of the best-rated. */
const val STAFF_POOL = 10

/** "This month" means arrivals in the last thirty days. */
const val MONTH_MS = 30 * DAY_MS
const val THIS_MONTH_LIMIT = 5

enum class FeatureKind { EDITOR, STAFF, TRENDING, NEW }

/** One of the three feature cards: which fact earned it a slot, and the title itself. */
data class Feature(val kind: FeatureKind, val set: MediaSet)

/** The whole page's picks, with no title featured twice. */
data class EditorialPicks(
    val cover: List<MediaSet>,
    val features: List<Feature>,
    val quote: MediaSet?,
    val thisMonth: List<MediaSet>,
)

/**
 * The whole page's picks, with no title featured twice.
 *
 * The editor's choice is honoured first — it is the one pick a person made
 * — then the two ranked features, then the cover from what is left, then
 * the quote. A pin may be any title, watched or not: pinning is the
 * statement. Everything else is drawn from films this profile has not
 * watched that have artwork to show — a backdrop, for the cover.
 *
 * [onRow] is what the Recently added row already shows; "This month" beside
 * it lists the arrivals after those, so the two never repeat each other.
 */
fun homeEditorial(
    movies: List<MediaSet>,
    byId: Map<String, MediaSet>,
    isWatched: (String) -> Boolean,
    editorsChoice: String?,
    now: Long,
    onRow: Set<String> = emptySet(),
): EditorialPicks {
    val day = dayOf(now)
    val taken = mutableSetOf<String>()
    fun take(set: MediaSet?): MediaSet? {
        if (set != null) taken += set.setId
        return set
    }
    // Features take a poster where there is no backdrop; the cover is a
    // full-width photograph and needs a backdrop, passed as [art] below.
    fun open(art: (MediaSet) -> String? = { it.backdropPath ?: it.posterPath }): List<MediaSet> =
        movies.filter { art(it) != null && !isWatched(it.setId) && it.setId !in taken }

    val pinned = editorsChoice?.let { byId[it] }
    val editor = take(pinned)

    val trendingSet = take(mostPopular(open()))
    // Nothing carries a popularity figure — an index from before it was
    // recorded. Say what the card actually is rather than claim a trend.
    val trending = if (trendingSet != null) Feature(FeatureKind.TRENDING, trendingSet) else newest(open(), ::take)

    val staff = take(staffPick(open(), day))
    // No pin: the slot follows the staff rule, and is labelled as such.
    val lead = if (editor != null) Feature(FeatureKind.EDITOR, editor) else take(staffPick(open(), day + 1))?.let { Feature(FeatureKind.STAFF, it) }

    val features = listOfNotNull(lead, trending, staff?.let { Feature(FeatureKind.STAFF, it) })

    val cover = pickFeatured(open { it.backdropPath }, isWatched, seededRandom(day), COVER_COUNT)
    for (set in cover) take(set)

    return EditorialPicks(
        cover = cover,
        features = features,
        quote = quoteOf(movies, taken, day),
        thisMonth = arrivedWithin(movies.filter { it.setId !in onRow }, now),
    )
}

private fun mostPopular(pool: List<MediaSet>): MediaSet? {
    var best: MediaSet? = null
    for (set in pool) {
        if ((set.popularity ?: 0.0) > (best?.popularity ?: 0.0)) best = set
    }
    return best
}

/** A real tagline from the newest arrival, labelled as that rather than a trend nothing measured. */
private fun newest(pool: List<MediaSet>, take: (MediaSet?) -> MediaSet?): Feature? {
    val set = pool.maxByOrNull(MediaSet::addedAt) ?: return null
    val kept = take(set) ?: return null
    return Feature(FeatureKind.NEW, kept)
}

/** One of the best-rated, turning over daily; ties broken by title for a stable order. */
private fun staffPick(pool: List<MediaSet>, day: Long): MediaSet? {
    val ranked = pool
        .filter { (it.rating ?: 0.0) > 0.0 }
        .sortedWith(compareByDescending<MediaSet> { it.rating ?: 0.0 }.thenBy { it.title })
        .take(STAFF_POOL)
    if (ranked.isEmpty()) return null
    return ranked[(day % ranked.size).toInt()]
}

/** A real tagline for the typographic break, from a film not featured above. */
private fun quoteOf(movies: List<MediaSet>, taken: Set<String>, day: Long): MediaSet? {
    val pool = movies.filter { !it.tagline.isNullOrEmpty() && it.setId !in taken }
    if (pool.isEmpty()) return null
    val random = seededRandom(day + 7)
    return pool[floor(random() * pool.size).toInt()]
}

/** Films that arrived in the last thirty days, newest first. */
fun arrivedWithin(
    movies: List<MediaSet>,
    now: Long,
    windowMs: Long = MONTH_MS,
    limit: Int = THIS_MONTH_LIMIT,
): List<MediaSet> =
    movies
        .filter { it.addedAt > now - windowMs }
        .sortedByDescending(MediaSet::addedAt)
        .take(limit)

/**
 * Which films the cover reel shows, and in what order — a port of
 * `featured-picks.js`'s `pickFeatured`.
 *
 * The reel is there to help choose something to watch, so it holds films
 * this profile has not seen, shuffled so each opening suggests something
 * new. A film without a poster is left out even when its backdrop already
 * passed the caller's own filter: the reel's own pool is built from
 * posters, and a blank slide would tease nothing.
 */
fun pickFeatured(
    movies: List<MediaSet>,
    isWatched: (String) -> Boolean,
    random: () -> Double,
    count: Int = FEATURED_COUNT,
): List<MediaSet> {
    val pool = movies.filter { it.posterPath != null && !isWatched(it.setId) }.toMutableList()
    // Fisher-Yates over a copy: the shelf itself keeps its title order.
    for (index in pool.size - 1 downTo 1) {
        val other = floor(random() * (index + 1)).toInt()
        val swapped = pool[index]
        pool[index] = pool[other]
        pool[other] = swapped
    }
    return pool.take(count)
}
