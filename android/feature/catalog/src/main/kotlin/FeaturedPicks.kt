package catalog

import model.MediaSet
import kotlin.random.Random

/**
 * Which films the Featured reel shows, and in what order — `featured-picks.js`
 * in the web player, ported. The reel is there to help choose something to
 * watch, so it holds films this profile has not seen, shuffled so each
 * opening suggests something new. A film without a poster is left out: the
 * reel is made of posters, and a blank slide would tease nothing.
 */

/** How many films one opening of the reel runs through. */
const val FEATURED_COUNT = 12

/**
 * [films] not watched and with a poster, shuffled by [random] (a source in
 * [0, 1), passed in so the cover can seed it by the day and a test can fix
 * it), at most [count]. The one port of the web's `pickFeatured`, shared by
 * the Featured reel and the home page's cover.
 */
fun pickFeatured(
    films: List<MediaSet>,
    isWatched: (String) -> Boolean,
    random: () -> Double,
    count: Int = FEATURED_COUNT,
): List<MediaSet> {
    val pool = films.filter { it.posterPath != null && !isWatched(it.setId) }.toMutableList()
    // Fisher–Yates over a copy, drawn the way the web draws it: the shelf keeps its title order.
    for (index in pool.lastIndex downTo 1) {
        val other = (random() * (index + 1)).toInt()
        pool[index] = pool[other].also { pool[other] = pool[index] }
    }
    return pool.take(count)
}

/** [pickFeatured] for the reel, which knows the watched ids and draws from a [Random]. */
fun pickFeatured(
    films: List<MediaSet>,
    watchedIds: Set<String>,
    random: Random,
    count: Int = FEATURED_COUNT,
): List<MediaSet> = pickFeatured(films, { it in watchedIds }, random::nextDouble, count)

/** The slide [by] steps from [index], wrapping round a reel of [length]. */
fun stepFrom(
    index: Int,
    by: Int,
    length: Int,
): Int = ((index + by) % length + length) % length
