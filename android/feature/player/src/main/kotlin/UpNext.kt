package player

/**
 * When the next title is offered, and when it starts on its own — a port of
 * `up-next.js`. These were one web decision that should have been two:
 * offering the next episode a little before the end is a heads-up, and puts
 * a way past the credits within reach; starting it before the end cuts off
 * whatever is still playing. So the phase is worked out here from facts
 * rather than from whether a timer happens to be running, and counting
 * cannot begin before the title has actually ended.
 */

/** How long before the end the next title is offered. */
const val WARN_SECONDS = 30.0

/** How long the viewer has, once it has ended, before it goes on its own. */
const val COUNTDOWN_SECONDS = 10

/** `WAITING` shows the panel and says what follows; `COUNTING` is the only phase that may start it. */
enum class UpNextPhase { HIDDEN, WAITING, COUNTING }

/**
 * @param remainingSeconds Seconds left in the title, or `null` for a runtime
 * this device does not know — kept apart from a real zero, which is the one
 * value that would otherwise open the panel immediately.
 */
data class UpNextAt(
    val hasNext: Boolean,
    val cancelled: Boolean,
    val remainingSeconds: Double?,
    val ended: Boolean,
)

fun upNextPhase(at: UpNextAt): UpNextPhase {
    if (!at.hasNext || at.cancelled) return UpNextPhase.HIDDEN
    // The end is the end however it arrived — run out, or seeked past the
    // last frame. Either way there is nothing left of this title to interrupt.
    if (at.ended) return UpNextPhase.COUNTING

    val left = at.remainingSeconds
    // An unknown runtime cannot be counted down from. Nothing is offered
    // early rather than something offered at the wrong moment; `ended` still
    // catches it.
    if (left == null || !left.isFinite()) return UpNextPhase.HIDDEN
    return if (left <= WARN_SECONDS) UpNextPhase.WAITING else UpNextPhase.HIDDEN
}

/**
 * What follows [setId] in [run], or `null` at the end — the same web
 * function name as `nextInQueue` in library.js, over a run of ids rather
 * than sets: `:feature:player` may not import `:feature:catalog` (feature
 * modules do not import one another), so this is all it is ever handed.
 */
fun nextInQueue(run: List<String>, setId: String): String? {
    val at = run.indexOf(setId)
    return if (at == -1 || at == run.lastIndex) null else run[at + 1]
}
