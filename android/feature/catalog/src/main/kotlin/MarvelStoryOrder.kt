package catalog

import model.MediaSet

/** Below every TMDB collection id, which are all positive, so it never collides with one. */
const val MCU_ID = -1L

/**
 * TMDB movie ids of the Marvel Cinematic Universe in story order, after
 * Marvel's own Disney+ timeline — ported from the web's `mcu.js`, whose list
 * `MarvelStoryOrderTest` holds this one to.
 */
val MCU_STORY_ORDER: List<Long> =
    listOf(
        1771, 299537, 1726, 10138, 1724, 10195, 24428, 76338, 68721, 100402,
        118340, 283995, 99861, 102899, 271110, 497698, 284054, 315635, 284052, 284053,
        363088, 299536, 299534, 429617, 566525, 524434, 634649, 453395, 616037, 505642,
        774752, 640146, 447365, 609681, 533535, 822119, 986056, 617126, 969681,
    )

/** The cut the collection plays when a film is held in more than one: Endgame's Encore. */
private val PREFERRED_CUT = mapOf(299534L to "Encore")

private const val MOVIE_KEY = "tmdb-movie-"

/**
 * The MCU as a franchise, or `null` below two held films — `mcuIn` in the
 * web's `mcu.js`. Each film appears once; a film with a preferred cut is that
 * copy, still carrying every version.
 */
fun mcuIn(movies: List<MediaSet>): Franchise? {
    val place = MCU_STORY_ORDER.withIndex().associate { (at, id) -> "$MOVIE_KEY$id" to at }
    val held =
        groupFilms(movies.flatMap(::versionsOf).filter { it.posterKey in place })
            .map(::preferredCopy)
            .sortedBy { place.getValue(it.posterKey!!) }
    if (held.size < 2) return null
    val lead = held.filter { it.backdropPath != null || it.posterPath != null }.maxByOrNull { it.popularity ?: 0.0 }
    return Franchise(MCU_ID, "Marvel Cinematic Universe", held, lead?.backdropPath ?: lead?.posterPath, order = "In story order")
}

private fun preferredCopy(film: MediaSet): MediaSet {
    val cut = PREFERRED_CUT[film.posterKey!!.removePrefix(MOVIE_KEY).toLong()] ?: return film
    val copy = film.versions.find { cutOf(it) == cut } ?: return film
    return copy.copy(versions = film.versions)
}
