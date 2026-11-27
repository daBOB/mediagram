package com.mediagram.spieltisch

import java.net.URLEncoder

/**
 * The TV page's address: [tvUrl] with `films=0` when the media app did not answer this night, and
 * `game=<kind>` when the TV home screen opened Spieltisch on a game (the lobby picks it once).
 */
fun gamePageAddress(
    tvUrl: String,
    filmsUnavailable: Boolean,
    game: String?,
): String {
    val query =
        listOfNotNull(
            "films=0".takeIf { filmsUnavailable },
            game?.let { "game=${URLEncoder.encode(it, Charsets.UTF_8)}" },
        ).joinToString("&")
    return if (query.isEmpty()) tvUrl else "$tvUrl?$query"
}
