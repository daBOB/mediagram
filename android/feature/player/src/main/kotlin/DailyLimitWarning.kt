package player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.DailyLimitState
import model.DailyLimitWords

/** How long the words stay over the picture: longer than watch together's two seconds, for a child to read. */
internal const val DAILY_LIMIT_NOTICE_MS = 8_000L

/**
 * The one warning per opened title, once five minutes or less are left —
 * at open if already there, else on the first save tick that finds it so.
 * Never stops, pauses or seeks anything: it only [tell]s.
 */
internal class DailyLimitWarning(
    private val scope: CoroutineScope,
    private val gate: DailyLimitGate,
    private val tell: (String) -> Unit,
) {
    private var warned = false

    /** A title opened: its warning is due again. */
    fun opened() {
        warned = false
        check()
    }

    /** The save ticker's beat. */
    fun tick() {
        if (!warned) check()
    }

    private fun check() {
        if (!gate.applies()) return
        scope.launch {
            if (!warned && gate.standing() == DailyLimitState.WARN) {
                warned = true
                tell(DailyLimitWords.WARNING)
            }
        }
    }
}
