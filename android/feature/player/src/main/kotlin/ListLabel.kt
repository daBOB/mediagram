package player

/**
 * What the list button says — `refreshWatchlist` in `player-library-marks.js`:
 * the list by the name the rail gives it, and whether this title is on it.
 * One copy, so the phone's player and the television's cannot word it apart.
 */
fun listLabel(marks: PlayerMarksState): String = if (marks.watchlisted) "On My List" else "My List"
