package player.together

import data.LiveSession
import data.Started
import data.TOGETHER_UNREACHABLE
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import model.together.Playback

/*
 * Starting and joining a session from the player, the twin of `together.js`'s
 * `start`, `joinLive`, `ask` and `enter`.
 */

/** How long a start or a join is waited for: its send has no limit of its own. */
internal const val ASK_MS = 10_000L

/** A session already live is joined instead: one start per library. */
internal suspend fun TogetherController.start() {
    val own = steps.own(setId) ?: return
    val playback = Playback(own.set ?: return, own.playing, own.pos, own.rate)
    // A start that met another screen's session never joined it: answered late, it leaves nothing.
    val answer = ask({ s: Started -> s.live.id.takeUnless { s.conflict } }) { live.start(me, playback) } ?: return
    val started = answer.getOrElse { return view.show(TOGETHER_UNREACHABLE) }
    if (started.conflict) joinLive(started.live) else enter(Result.success(started.live))
}

/** Joins [found]: here, on this title, or by opening its title first, which joins it as it opens. */
internal suspend fun TogetherController.joinLive(found: LiveSession) {
    if (found.session.set == setId) return joinHere(found.id)
    val asked = generation
    val here = titleOf(found.session.set) != null
    if (asked != generation) return
    if (!here) return view.show(NOT_HERE)
    live.joinOnOpen(found.id)
    openTitle(found.session.set)
}

/** Joins session [id] on the title open now. */
internal suspend fun TogetherController.joinHere(id: Int) = enter(ask({ s: LiveSession? -> s?.id }) { live.join(id, me) })

/**
 * A start or a join. One answered after this screen left, its player closed
 * or it stopped waiting is too late, and takes the screen out again rather
 * than in, as the web player's `ask()` does. The call runs on to its answer
 * whatever becomes of the player, so that leave is never lost
 * (`TogetherLive.start`). `null` when too late.
 */
private suspend fun <T> TogetherController.ask(entered: (T) -> Int?, call: suspend () -> T): Result<T>? {
    val asked = generation
    val reply = scope.async(NonCancellable) {
        runCatching { call() }.onSuccess { got -> if (asked != generation) entered(got)?.let { live.leave(it, screen) } }
    }
    val answer = withTimeoutOrNull(ASK_MS) { reply.await() }
    if (asked != generation) return null
    if (answer == null) {
        generation++
        view.show(COULD_NOT_START)
    }
    return answer
}

/** A start's or a join's answer: in, and following from there, or why not. */
private suspend fun TogetherController.enter(answer: Result<LiveSession?>?) {
    val got = answer ?: return
    val entered = got.getOrElse { return view.show(TOGETHER_UNREACHABLE) } ?: return view.show(ENDED)
    id = entered.id
    drift = drift ?: scope.launch { keepInStep() }
    onLive(live.live.value?.takeIf { it.id == entered.id } ?: entered)
}
