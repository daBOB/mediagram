package player

import model.MediaSet
import model.episodeLabel

/**
 * How a title is named to the viewer — a port of the web's `titleLine`
 * (`player.js`): show · episode · title, web order. Blank with nothing
 * open, which is also what a cold start straight into the player with no
 * catalog loaded yet answers, until the set resolves.
 *
 * Reads [MediaSet.rawTitle] rather than [MediaSet.title]: the index's own
 * title, unfilled-in, so an episode or a film the index never gave one
 * drops the segment instead of repeating the show's name or printing a
 * raw set id back at the viewer — which is what [MediaSet.title]'s own
 * fallback chain would otherwise hand this straight through.
 */
fun titleLine(set: MediaSet?): String {
    if (set == null) return ""
    return listOfNotNull(
        set.show?.trim()?.takeIf { it.isNotEmpty() },
        episodeLabel(set).takeIf { it.isNotEmpty() },
        set.rawTitle?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
}
