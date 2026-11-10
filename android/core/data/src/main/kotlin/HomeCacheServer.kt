package data

/**
 * Whether this device has found its household's cache server. With no TMDB
 * key stored, a fetch reads the artwork and descriptions the server holds
 * instead of stopping; away from home it stops as it always has.
 */
fun interface HomeCacheServer {
    fun connected(): Boolean
}
