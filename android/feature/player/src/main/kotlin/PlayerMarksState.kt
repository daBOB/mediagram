package player

import model.KidsVerdict
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
    /** The age the open title is marked for kids from — 6 or 12 — or null when it is not marked. */
    val kidsMark: Int?,
    val lists: List<ListOfSets>,
    val memberOf: Set<String>,
    /**
     * What the open title's rating decides at the widest limit a kid can
     * have — `refreshKids` in `player-library-marks.js`. Only an
     * [KidsVerdict.UNRATED] title is marked by hand; a rated one is shown
     * what its rating decided, and cannot be pressed.
     */
    val kidsVerdict: KidsVerdict = KidsVerdict.UNRATED,
    /** `"FSK 12"`, or null for an unrated title. */
    val ageLabel: String? = null,
    /** Rated for the youngest limit too, so for every kid — not only one at 12. */
    val forEveryKid: Boolean = false,
    /** False on a kids profile: a child does not approve titles for themselves. */
    val canMarkKids: Boolean = true,
) {
    /** Whether some child may watch this: rated for one, or unrated and marked. */
    val forKids: Boolean get() = kidsVerdict == KidsVerdict.SAFE || (kidsVerdict == KidsVerdict.UNRATED && kidsMark != null)
}
