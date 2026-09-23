package player

import model.ListOfSets

/**
 * What the player's three kept controls — Watchlist, Kids, Add to list —
 * show for whichever set is currently open. `null` while nothing is: the
 * web hides `watchlistButton`/`kidsButton`/`addToButton` the same way,
 * behind `if (!playing) return` in `player.js`.
 *
 * [lists] and [memberOf] are Collections' own state, carried here rather
 * than read a second time by the "Add to list" dialog: [PlayerViewModel]
 * already holds the one snapshot everything else in the app reads from.
 */
data class PlayerMarksState(
    val watchlisted: Boolean,
    val kids: Boolean,
    val lists: List<ListOfSets>,
    val memberOf: Set<String>,
)
