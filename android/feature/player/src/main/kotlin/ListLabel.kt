package player

/**
 * What the list button says — `refreshWatchlist` in `player-library-marks.js`:
 * the list by the name the rail gives it, and whether this title is on it.
 * One copy, so the phone's player, the television's, and the television's
 * title pages cannot word it apart.
 */
fun listLabel(watchlisted: Boolean): String = if (watchlisted) "On My List" else "My List"

fun listLabel(marks: PlayerMarksState): String = listLabel(marks.watchlisted)
