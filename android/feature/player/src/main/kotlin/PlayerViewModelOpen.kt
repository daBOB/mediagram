package player

import androidx.lifecycle.viewModelScope
import data.ProgressPoint
import data.ResumePoint
import kotlinx.coroutines.launch

/**
 * Opens [setId], keeps its run current, and carries out an up-next switch
 * once the VM asks for one — split out of [PlayerViewModel] to keep that
 * file under the project's line guideline.
 *
 * [fsk] is the title's age rating as the catalog listed it, handed in
 * rather than looked up: the screen that opened this already has the
 * set in hand from whichever shelf it came from, and a lookup here
 * could still be racing the catalog on a cold start. The set itself is
 * resolved anyway, in [PlayerChoicesController.resolve], for the title
 * line and the preference scope — both can wait the moment it takes.
 *
 * A rotation destroys and recreates the whole screen, which re-runs the
 * `LaunchedEffect` that calls this with the *same* [setId] —
 * [sameTitle] is what tells that apart from a genuinely new title.
 * Retracing any of this for it is exactly what turned a rotation
 * mid-film into a flicker of the title, a reset of the chosen speed,
 * and (a leftover `setPlaybackSpeed(1f)` used to run here regardless)
 * an audible drop to 1x — none of which the handle needs help with:
 * asking it to open a set that is already loaded and playing
 * republishes rather than reloading (`DefaultPlayerHandle.open`), and
 * only a real reload floors the rate to 1x (`DefaultPlayerHandle.openOn`).
 */
fun PlayerViewModel.open(setId: String, run: List<String> = emptyList(), fsk: String? = null) {
    val sameTitle = session.openSetId == setId
    session.open(setId)
    openFsk.value = fsk
    _openSetId.value = setId
    // Reset unconditionally, same as always: for a rotation reopening
    // an already-playing title this is corrected straight back by the
    // handle's own synchronous republish (below), within this same
    // call — never actually shown — and for one still buffering it is
    // exactly what has to stay put until the player's own ready event
    // ends the wait (`PlayerReopenTest`). Only the choice, title and
    // scope skip resetting for [sameTitle], since those really would
    // otherwise flicker and reset for no reload at all.
    _state.value = PlayerUiState.Preparing
    if (!sameTitle) choicesController.reset()
    val progress = repository.snapshot.value.progress.find { it.setId == setId }
    val resumeSeconds = ResumePoint.resumeAt(progress?.let { ProgressPoint(it.at, it.duration) })
    val startAtMs = ((resumeSeconds ?: 0.0) * 1000).toLong()
    // False only for the one title a gated up-next switch is headed to
    // — asked before `startTitle`, which consumes the same pending gate.
    handle.open(setId, startAtMs, upNextController.playWhenReadyFor(setId))
    // Alongside the handle, not gated on `sameTitle`: a rotation's own
    // reopen must find the service already running exactly as often as it
    // must find the player already loaded, and starting an
    // already-started service is a no-op.
    playbackServiceController.start()
    if (!sameTitle) {
        viewModelScope.launch { choicesController.resolve(setId) }
        upNextController.startTitle(setId, run)
    }
}
