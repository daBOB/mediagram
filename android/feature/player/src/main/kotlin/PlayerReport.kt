package player

import android.util.Log
import data.ReportChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.ReportFacts
import model.ReportReason
import model.reportText

enum class ReportStage { IDLE, SENDING, SENT, FAILED }

/** What a report sheet shows: whether it is open, how the send stands, and the reason it opens on. */
data class ReportUi(val stage: ReportStage = ReportStage.IDLE, val open: Boolean = false, val preset: ReportReason? = null)

/**
 * The open title's report: the last playback error it had, and a send that happens once.
 * Mirrors the web player's `player-report.js` — one send in flight, and a reply that lands
 * after another title opened is dropped, so it can never mark the new title Reported.
 */
class PlayerReport(private val channel: ReportChannel, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(ReportUi())
    val ui: StateFlow<ReportUi> = _ui.asStateFlow()

    /** The code of the last playback failure of the open title, for the report's technical line. */
    var lastError: String? = null
        private set

    private var opened = 0
    private var inFlight = false

    /** Another title opened, or the player closed: its error and its Reported are not the next one's. */
    fun titleOpened() {
        opened++
        lastError = null
        _ui.value = ReportUi()
    }

    fun failed(code: String) {
        lastError = code
    }

    /** Ignored while sending or once sent: Reported stays until another title opens. */
    fun openReport(fromFailure: Boolean) {
        val stage = _ui.value.stage
        if (stage == ReportStage.SENDING || stage == ReportStage.SENT) return
        _ui.value = ReportUi(stage, open = true, preset = if (fromFailure) ReportReason.WONT_PLAY else null)
    }

    fun closeReport() {
        _ui.value = _ui.value.copy(open = false)
    }

    fun send(facts: ReportFacts) {
        val stage = _ui.value.stage
        if (inFlight || (stage != ReportStage.IDLE && stage != ReportStage.FAILED)) return
        inFlight = true
        val title = opened
        _ui.value = _ui.value.copy(stage = ReportStage.SENDING)
        scope.launch {
            val result = try {
                channel.send(reportText(facts.copy(error = lastError)))
                ReportStage.SENT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Player", "report not sent", e)
                ReportStage.FAILED
            } finally {
                inFlight = false
            }
            if (title == opened) _ui.value = _ui.value.copy(stage = result, open = result != ReportStage.SENT && _ui.value.open)
        }
    }
}
