package player

import android.util.Log
import data.ReportChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import model.ReportFacts
import model.ReportReason
import model.reportText
import kotlin.time.Duration.Companion.seconds

enum class ReportStage { IDLE, SENDING, SENT, FAILED }

/** What a report sheet shows: whether it is open, how the send stands, and the reason it opens on. */
data class ReportUi(val stage: ReportStage = ReportStage.IDLE, val open: Boolean = false, val preset: ReportReason? = null)

/**
 * The open title's report: the last playback error it had, and a send that happens once.
 * Mirrors the web player's `player-report.js` — one send per title, and a reply that lands
 * after another title opened is dropped, so it can never mark the new title Reported.
 */
class PlayerReport(private val channel: ReportChannel, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(ReportUi())
    val ui: StateFlow<ReportUi> = _ui.asStateFlow()

    /** The code of the last playback failure of the open title, for the report's technical line. */
    var lastError: String? = null
        private set

    private var opened = 0

    /** Another title opened, or the player closed: its error and its Reported are not the next one's. */
    fun titleOpened() {
        opened++
        lastError = null
        _ui.value = ReportUi()
    }

    fun failed(code: String) {
        lastError = code
    }

    /**
     * Ignored while sending or once sent: Reported stays until another title opens. Otherwise a fresh
     * sheet, as the web's: a failed send the viewer cancelled away is not said again on the next open.
     */
    fun openReport(fromFailure: Boolean) {
        val stage = _ui.value.stage
        if (stage == ReportStage.SENDING || stage == ReportStage.SENT) return
        _ui.value = ReportUi(open = true, preset = if (fromFailure) ReportReason.WONT_PLAY else null)
    }

    fun closeReport() {
        _ui.value = _ui.value.copy(open = false)
    }

    fun send(facts: ReportFacts) {
        val stage = _ui.value.stage
        if ((stage != ReportStage.IDLE && stage != ReportStage.FAILED)) return
        val title = opened
        _ui.value = _ui.value.copy(stage = ReportStage.SENDING)
        scope.launch {
            val result = try {
                // A dead connection would otherwise hold the sheet at Sending for over a minute; the web gives up at 20 s too.
                withTimeout(SEND_TIMEOUT) { channel.send(reportText(facts.copy(error = lastError))) }
                ReportStage.SENT
            } catch (e: TimeoutCancellationException) {
                Log.w("Player", "report not sent: no answer in $SEND_TIMEOUT", e)
                ReportStage.FAILED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Player", "report not sent", e)
                ReportStage.FAILED
            }
            if (title == opened) _ui.value = _ui.value.copy(stage = result, open = result != ReportStage.SENT && _ui.value.open)
        }
    }
}

private val SEND_TIMEOUT = 20.seconds
