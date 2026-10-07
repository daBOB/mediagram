package player

import model.humanSize
import playback.FilmPreloadRow
import playback.FilmPreloadState
import playback.PauseReason

/**
 * What a film's Preload control says, on the phone and on TV alike — one
 * set of pure functions so the two surfaces can never spell the same state
 * two different ways. Android-only by decision: the web player has no film
 * preload.
 */

/**
 * An [FilmPreloadState.Idle] the cache already holds in full reads exactly
 * like [FilmPreloadState.Done] — nothing left to preload, nothing for a tap
 * to start. This is the one gap the engine's own re-verification
 * (`FilmPreloader.stateOf`) does not close on its own: it only re-checks an
 * existing `Done` override against the cache, not a plain `Idle` that
 * playback alone happened to fill — so every function below normalizes
 * through this first, rather than three of them agreeing by accident.
 */
private fun normalized(state: FilmPreloadState): FilmPreloadState =
    if (state is FilmPreloadState.Idle && state.totalBytes > 0 && state.heldBytes >= state.totalBytes) {
        FilmPreloadState.Done
    } else {
        state
    }

/**
 * The control's own visible text — "Preload · 5.8 GB", "Preloaded ✓", and
 * so on. [queuedAhead] names what a [FilmPreloadState.Queued] film is
 * waiting on (`null` for the bare "Queued", either because nothing asked
 * or because the engine's own queue does not (yet) place it); [budgetBytes]
 * is the live cache allowance a [FilmPreloadState.NeedsSpace] film's label
 * names, `null` when a caller has not read it.
 */
fun preloadLabel(state: FilmPreloadState, queuedAhead: String? = null, budgetBytes: Long? = null): String =
    when (val s = normalized(state)) {
        is FilmPreloadState.Idle ->
            if (s.heldBytes <= 0) "Preload · ${humanSize(s.totalBytes)}" else "Preload · ${heldPercentLabel(s.heldBytes, s.totalBytes)} held"
        FilmPreloadState.Queued -> queuedAhead ?: "Queued"
        is FilmPreloadState.Running -> "Preloading"
        is FilmPreloadState.Paused -> pauseLabel(s.reason)
        FilmPreloadState.Done -> "Preloaded ✓"
        is FilmPreloadState.NeedsSpace -> needsSpaceLabel(s.neededBytes, budgetBytes)
        is FilmPreloadState.Failed -> "${ellipsize(s.reason, FAILED_REASON_MAX_LENGTH)} · Retry"
    }

private fun needsSpaceLabel(neededBytes: Long, budgetBytes: Long?): String =
    if (budgetBytes == null) {
        "Needs ${humanSize(neededBytes)} · Try again"
    } else {
        "Needs ${humanSize(neededBytes)} · budget is ${humanSize(budgetBytes)} · Try again"
    }

/**
 * What a [FilmPreloadState.Queued] film's label says beyond the bare
 * "Queued" — "after <running title>, 36%" when nothing but the running
 * film (paused or not) precedes it, "N ahead" otherwise, where N counts
 * every film ahead of it in [rows] including the running one. `null` when
 * [setId] leads [rows] itself (nothing precedes it — about to run) or is
 * not in it at all (already settled elsewhere by the time this is read).
 * The running title is ellipsized the same reason [FilmPreloadState.Failed]'s
 * reason already is: a long real title would otherwise wrap this pill onto
 * a second line at a phone's own width.
 */
fun queuedAheadLabel(rows: List<FilmPreloadRow>, setId: String): String? {
    val index = rows.indexOfFirst { it.setId == setId }
    if (index <= 0) return null
    val running = rows.firstOrNull() as? FilmPreloadRow.Running
    return if (index == 1 && running != null) {
        "Queued · after ${ellipsize(running.title, QUEUED_AHEAD_TITLE_MAX_LENGTH)}, ${heldPercentLabel(running.heldBytes, running.totalBytes)}"
    } else {
        "Queued · $index ahead"
    }
}

/**
 * The [FilmPreloadState] a Preloads page's own [FilmPreloadRow.Running] row
 * corresponds to — reusing [preloadLabel]/[preloadBarLabel] rather than a
 * second copy of the same words for a page that only ever shows the front
 * of the queue in a different place.
 */
fun preloadRowState(row: FilmPreloadRow.Running): FilmPreloadState =
    row.pauseReason?.let { FilmPreloadState.Paused(row.heldBytes, row.totalBytes, it) } ?: FilmPreloadState.Running(row.heldBytes, row.totalBytes)

private fun pauseLabel(reason: PauseReason): String =
    when (reason) {
        PauseReason.Playing -> "Paused while playing"
        PauseReason.Metered -> "Waiting for Wi-Fi"
        PauseReason.TimeLimit -> "Paused (background limit reached)"
    }

/** What a tap or an OK press on the control does right now — read by both the accessible label and the ViewModel's own dispatch, so neither can say one thing and do another. */
enum class PreloadTapAction { ENQUEUE, CANCEL, NONE }

fun preloadTapAction(state: FilmPreloadState): PreloadTapAction =
    when (val s = normalized(state)) {
        // NeedsSpace retries the same way Idle/Failed start: a fresh
        // `enqueue` is the only thing that ever re-judges `fits` against a
        // budget the viewer may just have raised — the engine never
        // reconsiders a NeedsSpace override on its own.
        is FilmPreloadState.Idle, is FilmPreloadState.Failed, is FilmPreloadState.NeedsSpace -> PreloadTapAction.ENQUEUE
        FilmPreloadState.Queued, is FilmPreloadState.Running -> PreloadTapAction.CANCEL
        is FilmPreloadState.Paused ->
            if (s.reason == PauseReason.TimeLimit) PreloadTapAction.ENQUEUE else PreloadTapAction.CANCEL
        // Done is read-only here: its own action is "Remove preload",
        // offered beside the control rather than on it.
        FilmPreloadState.Done -> PreloadTapAction.NONE
    }

/**
 * Whether the control reads as an affirmative action (a phone's Line pill,
 * a TV plate's own equivalent) or a quiet one — not simply "does a tap
 * enqueue", since a background-limit pause enqueues on a tap too but is not
 * a fresh choice the viewer made, only a queue unclogging on its own:
 * DESIGN.md's own Quiet reading of a pause covers all three [PauseReason]s
 * alike. `feature/player` cannot see either surface's actual pill/plate
 * type, so this only names which the caller should reach for.
 */
fun preloadIsAffirmative(state: FilmPreloadState): Boolean =
    when (normalized(state)) {
        is FilmPreloadState.Idle, is FilmPreloadState.Failed, is FilmPreloadState.NeedsSpace -> true
        else -> false
    }

/** Whether the control still takes a press — false only for Done, which is read-only until "Remove preload" is asked for beside it. */
fun preloadIsEnabled(state: FilmPreloadState): Boolean = preloadTapAction(state) != PreloadTapAction.NONE

/**
 * The fuller sentence a screen reader gets, beyond the short visible
 * [preloadLabel] — what a tap or an OK press actually does. Keyed on
 * [state] directly rather than routed back through [preloadTapAction]: that
 * indirection once let [FilmPreloadState.Paused]'s [PauseReason.TimeLimit]
 * (an `ENQUEUE`) fall into the `CANCEL` branch's hint by construction,
 * saying "Tap to cancel." for a tap that actually resumed it.
 */
fun preloadAccessibilityHint(state: FilmPreloadState): String? =
    when (val s = normalized(state)) {
        is FilmPreloadState.Idle -> "Tap to preload this film to your device."
        is FilmPreloadState.Failed -> "${s.reason} Tap to retry."
        is FilmPreloadState.NeedsSpace -> "Tap to try again."
        FilmPreloadState.Queued, is FilmPreloadState.Running -> "Tap to cancel."
        is FilmPreloadState.Paused -> if (s.reason == PauseReason.TimeLimit) "Tap to resume." else "Tap to cancel."
        FilmPreloadState.Done -> null
    }

/** The thin bar's own line: "2.1 of 5.8 GB · 36%". */
fun preloadBarLabel(heldBytes: Long, totalBytes: Long): String =
    "${heldSizeLabel(heldBytes, totalBytes)} of ${humanSize(totalBytes)} · ${heldPercentLabel(heldBytes, totalBytes)}"

/**
 * The paired home server's own line, or `null` when there is nothing to
 * say — no server, the LAN cache off, or the server holding none of this
 * film yet. [totalBytes] is always the film's own size from the catalogue,
 * never the server's reported total, which can be `null` while chunks are
 * already held (`LanSetStatus`'s own doc).
 */
fun preloadServerLine(bytesHeld: Long, totalBytes: Long): String? =
    if (bytesHeld <= 0) null else "Home server: ${heldSizeLabel(bytesHeld, totalBytes)} of ${humanSize(totalBytes)}"

private fun heldPercent(held: Long, total: Long): Int = if (total <= 0) 0 else (held * 100 / total).toInt().coerceIn(0, 100)

/** As [heldPercent], but a film with some real bytes held never reads as flatly "0%" — the same reason a battery reads "1%" rather than "0%" moments before it actually reaches empty. */
private fun heldPercentLabel(held: Long, total: Long): String {
    val percent = heldPercent(held, total)
    return if (percent <= 0 && held > 0) "< 1%" else "$percent%"
}

/**
 * "x of y GB": [humanSize] on both sides, except the held figure drops its
 * own unit when it already matches the total's — "2.0 of 5.0 GB", not
 * "2.0 GB of 5.0 GB" — since repeating the unit a few characters later adds
 * nothing. A held figure still in MB against a total in GB keeps its own
 * unit instead ("500 MB of 5.8 GB"): dropping it there would silently claim
 * 500 *gigabytes* are held.
 */
private fun heldSizeLabel(held: Long, total: Long): String {
    val heldText = humanSize(held)
    return if (unitOf(heldText) == unitOf(humanSize(total))) heldText.substringBeforeLast(' ') else heldText
}

private fun unitOf(sized: String): String = sized.substringAfterLast(' ')

/** How long [FilmPreloadState.Failed]'s reason gets to run before the pill would grow past a reasonable width — the full sentence still reaches a screen reader through [preloadAccessibilityHint]. */
private const val FAILED_REASON_MAX_LENGTH = 28

/** As [FAILED_REASON_MAX_LENGTH], for the running title [queuedAheadLabel] names — shorter, since it shares the line with a percentage. */
private const val QUEUED_AHEAD_TITLE_MAX_LENGTH = 20

private fun ellipsize(text: String, maxLength: Int): String =
    if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"
