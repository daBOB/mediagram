package player

import data.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import model.MediaSet

/**
 * What follows the open title and the countdown that may start it
 * unattended — split out of [PlayerViewModel] to keep that file under the
 * project's line guideline; [UpNextSwitcher] holds the switch itself (never
 * opens a title directly, only asks the UI layer to — see its own doc) and
 * `UpNextAsync.kt` the phase ticker, countdown and title resolution. A port
 * of `refreshUpNext`/`startWhenReady` in `player.js`, standing on the pure
 * [upNextPhase]/[autoplayReady] as that file stands on `up-next.js`/`autoplay.js`.
 *
 * [cancelledIds] lives as long as this controller does — the handle's own
 * equivalent of the web's in-memory `Set`, lost only with the process.
 */
class UpNextController(
    private val scope: CoroutineScope,
    private val handle: PlayerHandle,
    private val session: PlayerSession,
    private val catalogRepository: CatalogRepository,
    private val openSet: StateFlow<MediaSet?>,
) {
    private val _state = MutableStateFlow(UpNextUiState())
    val state: StateFlow<UpNextUiState> = _state.asStateFlow()

    private val switcher = UpNextSwitcher(scope, handle)

    /** A title to navigate to, once — `PlayerScreen` consumes it and moves `LibraryPositions` to match. */
    val pendingSwitch: StateFlow<PendingPlayerSwitch?> = switcher.pendingSwitch
    fun switchAcknowledged() = switcher.acknowledged()
    fun playWhenReadyFor(setId: String): Boolean = switcher.playWhenReadyFor(setId)

    private var run: List<String> = emptyList()
    private val cancelledIds = mutableSetOf<String>()
    private var ended = false

    private var nextId: String? = null

    /** Resolved once [startTitle] asks for it — the next title's own line and runtime, needed before it is ever opened. */
    private var nextSet: MediaSet? = null
    private var nextTitleLine: String = ""
    private var countdownLeft: Int? = null

    private var tickerJob: Job? = null
    private var countdownJob: Job? = null
    private var resolveJob: Job? = null

    /** A new title is open; works out what follows it, and — if this is the title a gated switch was headed to — starts the gate poll. */
    fun startTitle(setId: String, run: List<String>) {
        ended = false
        stopCountdown()
        resolveNext(setId, run)
        evaluate()
        switcher.startedTitle(setId) { publish(state.value.phase) }
        publish(state.value.phase)
    }

    /**
     * The run changed for the still-open title — the catalog was not Ready
     * yet when [startTitle] first ran. Recomputes what follows without
     * touching the ticker, countdown or [ended]; those belong to the title
     * already playing, not to what follows it.
     */
    fun updateRun(setId: String, run: List<String>) {
        if (session.openSetId != setId || run == this.run) return
        resolveNext(setId, run)
        evaluate()
    }

    private fun resolveNext(setId: String, run: List<String>) {
        this.run = run
        nextId = nextInQueue(run, setId)
        nextSet = null; nextTitleLine = ""
        resolveJob?.cancel()
        val id = nextId
        // A later resolveNext must win over a resolution landing after it.
        resolveJob = if (id == null) null else scope.resolveUpNextTitle(catalogRepository, id) { set ->
            if (nextId == id) { nextSet = set; nextTitleLine = titleLine(set); evaluate() }
        }
    }

    /**
     * The phase ticker runs only while playing, stopping itself on the same
     * signal [PlayerSession]'s own save ticker does. Starting playback also
     * cancels a pending gate — `player.js`'s `stopWaitingToStart` — since a
     * viewer pressing play is watching the screen, and a gate resolving
     * later would call `play()` straight over a pause that followed it.
     */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (switcher.cancelGateOnPlay()) publish(state.value.phase)
            ensureTicking()
            evaluate()
        } else {
            tickerJob?.cancel(); tickerJob = null
        }
    }

    /** A seek landed while paused — the ticker does not run then, so this is the only way the card notices; a single re-check, not a reason to start ticking. */
    fun onSeeked() {
        if (session.openSetId != null) evaluate()
    }

    /** The open title ran out, or was seeked past its last frame — the only event that may start the next one. */
    fun onEnded() {
        ended = true
        evaluate()
    }

    /** The viewer asked for it now — starts as soon as the player can give it, the same as opening any title by hand. */
    fun playNow() = switchTo(gate = false)

    /** Remembered for this title only, so watching the last moments again does not start the countdown a second time. */
    fun cancel() {
        session.openSetId?.let { cancelledIds += it }
        evaluate()
    }

    /** The player screen is being left for good — clears everything but [cancelledIds], which outlives any one title. */
    fun stop() {
        tickerJob?.cancel(); tickerJob = null
        stopCountdown()
        resolveJob?.cancel(); resolveJob = null
        run = emptyList(); nextId = null; nextSet = null; nextTitleLine = ""; ended = false
        switcher.stop()
        _state.value = UpNextUiState()
    }

    private fun ensureTicking() {
        tickerJob?.cancel()
        tickerJob = scope.runTicker(::evaluate)
    }

    private fun evaluate() {
        val setId = session.openSetId
        val phase = if (setId == null) {
            UpNextPhase.HIDDEN
        } else {
            upNextPhase(
                UpNextAt(
                    hasNext = nextId != null,
                    cancelled = setId in cancelledIds,
                    remainingSeconds = remainingSeconds(),
                    ended = ended,
                ),
            )
        }
        if (phase == UpNextPhase.COUNTING) startCountdownIfNeeded() else stopCountdown()
        publish(phase)
    }

    private fun remainingSeconds(): Double? {
        val runtime = openSet.value?.durationSecs?.toDouble() ?: return null
        if (runtime <= 0) return null
        val posMs = handle.positionMs() ?: return null
        return runtime - posMs / 1_000.0
    }

    private fun startCountdownIfNeeded() {
        if (countdownJob?.isActive == true) return
        countdownLeft = COUNTDOWN_SECONDS
        countdownJob = scope.runCountdown(
            onTick = { left -> countdownLeft = left; publish(UpNextPhase.COUNTING) },
            onFinished = { switchTo(gate = true) },
        )
    }

    private fun stopCountdown() {
        countdownJob?.cancel(); countdownJob = null; countdownLeft = null
    }

    private fun switchTo(gate: Boolean) {
        val id = nextId ?: return
        stopCountdown()
        ended = false
        // The existing save path: wherever the ending title is, saved as it
        // switches away from it, not left to a ten-second ticker that may
        // not land before the next `open()` resets what it would save against.
        session.save()
        switcher.request(id, run, gate, nextSet?.durationSecs?.toDouble())
    }

    private fun publish(phase: UpNextPhase) {
        _state.value = UpNextUiState(
            phase = phase,
            titleLine = nextTitleLine,
            countdownSecondsLeft = countdownLeft,
            hasNext = nextId != null,
            awaitingStart = switcher.awaitingStart,
        )
    }
}
