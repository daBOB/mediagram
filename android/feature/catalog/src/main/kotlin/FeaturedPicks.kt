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

/** [films] this profile has not watched and that have a poster, shuffled by [random], at most [count]. */
fun pickFeatured(
    films: List<MediaSet>,
    watchedIds: Set<String>,
    random: Random,
    count: Int = FEATURED_COUNT,
): List<MediaSet> {
    val pool = films.filter { it.posterPath != null && it.setId !in watchedIds }.toMutableList()
    // Fisher–Yates over a copy, drawn the way the web draws it: the shelf keeps its title order.
    for (index in pool.lastIndex downTo 1) {
        val other = (random.nextDouble() * (index + 1)).toInt()
        pool[index] = pool[other].also { pool[other] = pool[index] }
    }
    return pool.take(count)
}

/** The slide [by] steps from [index], wrapping round a reel of [length]. */
fun stepFrom(
    index: Int,
    by: Int,
    length: Int,
): Int = ((index + by) % length + length) % length
