package ui.common.catalog

import playback.FilmPreloadState
import player.heldPercentLabel

/**
 * What an episode row says around its Preload control — one copy for the
 * phone's row and the television's, so the two lists cannot come to read
 * the same share two different ways.
 */

/** The row's own held share, "42%" — `null` with nothing held, and in full, where the held badge says it instead. */
fun TitlePreloadUi.rowShareLabel(totalBytes: Long): String? =
    heldBytes?.takeIf { it in 1 until totalBytes }?.let { heldPercentLabel(it, totalBytes) }

/**
 * The state a row's control is labelled as: a partly held idle row reads as
 * for nothing held ("Preload · 5.8 GB"), its share said once, as
 * [rowShareLabel]. A press still acts on the real [TitlePreloadUi.state].
 */
fun TitlePreloadUi.rowControlState(): FilmPreloadState =
    when (val s = state) {
        is FilmPreloadState.Idle -> if (s.heldBytes < s.totalBytes) FilmPreloadState.Idle(0L, s.totalBytes) else s
        else -> s
    }

/** Whether a row wears its held badge: the catalogue's held set, or the row's own count, which may hear of a finished preload first. */
fun heldInFull(held: Boolean, preload: TitlePreloadUi?, totalBytes: Long): Boolean =
    held || preload?.heldBytes?.let { it >= totalBytes } == true
