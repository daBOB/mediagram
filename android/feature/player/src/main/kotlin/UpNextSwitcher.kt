package player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks the UI layer to move to a different title, and the gate a countdown's
 * own switch waits on before it may actually start — split out of
 * [UpNextController] to keep that file under the project's line guideline.
 *
 * Never opens the title itself: only the UI layer holds `LibraryPositions`,
 * and a switch that reopened the title through the handle without moving
 * the saved frame left a rotation or process restore replaying the title
 * that had just finished. [pendingSwitch] is how it asks instead;
 * [UpNextController.startTitle] running for the same id is how it knows the
 * ask landed.
 */
internal class UpNextSwitcher(private val scope: CoroutineScope, private val handle: PlayerHandle) {

    private val _pendingSwitch = MutableStateFlow<PendingPlayerSwitch?>(null)
    val pendingSwitch: StateFlow<PendingPlayerSwitch?> = _pendingSwitch.asStateFlow()

    private data class PendingGate(val setId: String, val runtimeSecs: Double?)
    private var pendingGate: PendingGate? = null
    private var gateJob: Job? = null

    /** An unattended switch is paused on the next title, waiting on the gate; the screen must stay awake through it. */
    var awaitingStart: Boolean = false
        private set

    /** Whether [setId] should start paused — true only for the one title a gated switch is headed to. Read before `handle.open`, which must happen before [startedTitle] consumes the same pending gate. */
    fun playWhenReadyFor(setId: String): Boolean = pendingGate?.setId != setId

    /** Asks the UI layer to move to [id]; [gate] says whether the switch should wait on the buffer once it lands, rather than start right away. */
    fun request(id: String, run: List<String>, gate: Boolean, runtimeForGate: Double?) {
        gateJob?.cancel(); gateJob = null
        pendingGate = if (gate) PendingGate(id, runtimeForGate) else null
        _pendingSwitch.value = PendingPlayerSwitch(id, run)
    }

    /** The UI layer moved `LibraryPositions` to match; nothing more to ask for until the next switch. */
    fun acknowledged() {
        _pendingSwitch.value = null
    }

    /**
     * The switch landed and [setId] is now truly open — starts the gate poll
     * if this was the title one was pending on, calling [onReady] once it
     * resolves. For any other open it only ends a wait left over from an
     * earlier switch.
     */
    fun startedTitle(setId: String, onReady: () -> Unit) {
        val gate = pendingGate?.takeIf { it.setId == setId }
        pendingGate = null
        if (gate == null) {
            // Any other title opening ends a wait still running for the one
            // a switch was headed to — left running, it would call `play()`
            // on this title whenever its buffer came in, straight over a
            // pause the viewer made in the meantime.
            gateJob?.cancel(); gateJob = null
            awaitingStart = false
            return
        }
        awaitingStart = true
        gateJob = scope.pollAutoplayGate(handle, gate.runtimeSecs) {
            awaitingStart = false
            onReady()
        }
    }

    /** Playback started by hand — a viewer pressing play is watching the screen, and a gate resolving later must not call `play()` over a pause that followed it. @return whether anything was actually cancelled, so the caller only republishes when it changed something. */
    fun cancelGateOnPlay(): Boolean {
        if (!awaitingStart) return false
        gateJob?.cancel(); gateJob = null
        awaitingStart = false
        return true
    }

    fun stop() {
        gateJob?.cancel(); gateJob = null
        pendingGate = null
        awaitingStart = false
        _pendingSwitch.value = null
    }
}
