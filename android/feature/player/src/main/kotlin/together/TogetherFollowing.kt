package player.together

import data.LiveSession
import data.TOGETHER_UNREACHABLE
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.together.PlaybackChange
import model.together.changeOf
import model.together.follow
import model.together.isLive

/*
 * Following the session a screen is in, the twin of `together.js`'s
 * `onTogether`, `apply`, `check` and `over`, and of `together-view.js`'s
 * `draw`.
 */

/** How often a screen in a session checks it is still with it. */
internal const val DRIFT_CHECK_MS = 2_000L

/**
 * What [data.TogetherLive] holds now. Out of a session that only redraws.
 * In one, no session or another means this one is over here: it moves on to
 * another only once a read finds that one the newest, as a web tab ends its
 * session when a read answers another.
 */
internal suspend fun TogetherController.onLive(heard: LiveSession?) {
    val inside = id ?: return draw()
    // Ended or gone, checked before what changed: this screen plays on alone.
    if (heard == null || heard.id != inside || heard.session.ended) return end(ENDED)
    val before = current
    val followed = follow(before, heard.session, screen)
    if (followed === before) return
    current = followed
    if (before != null && followed.by != screen) changeOf(before, followed)?.let { view.show(say(it, followed)) }
    apply()
    draw()
}

/** The viewer's [change] to [session]. One this device no longer holds is over; one Telegram can't be reached for plays on alone, and says why. */
internal suspend fun TogetherController.send(session: Int, change: PlaybackChange) {
    val held = live.act(session, screen, change)
    if (session != id) return
    if (held == null) over() else if (!live.available.value) view.show(TOGETHER_UNREACHABLE)
}

/** Every two seconds: a session gone stale under this screen, as one nobody moved on from does, is over; else the screen catches up. */
internal suspend fun TogetherController.keepInStep() {
    while (true) {
        delay(DRIFT_CHECK_MS)
        val session = current ?: continue
        val length = titleOf(session.set)?.durationSecs?.toDouble()
        if (current !== session) continue
        if (isLive(session, length, now())) apply(drifting = true) else over()
    }
}

/**
 * Brings the player to the session, or opens the title it moved to: asked
 * again on every check until it has opened, as the web player does. One
 * this profile may not see is where this screen leaves.
 */
private fun TogetherController.apply(drifting: Boolean = false) {
    val session = current ?: return
    val set = steps.bring(session, setId, drifting) ?: return
    expecting = set
    val asked = generation
    scope.launch {
        val here = titleOf(set) != null
        if (asked != generation || expecting != set) return@launch
        if (here) openTitle(set) else leave(LEFT_NOT_HERE)
    }
}

/** Over, found here rather than heard: the read that follows deletes a stale one, so nothing offers it again. */
private fun TogetherController.over() {
    end(ENDED)
    scope.launch { live.refresh() }
}

/** The button and the label. A change while the title is looked up draws again once it lands. */
internal suspend fun TogetherController.draw() {
    val elsewhere = live.live.value?.takeIf { id == null }
    val here = elsewhere == null || titleOf(elsewhere.session.set) != null
    view.draw(current, elsewhere.takeIf { id == null }, here, live.available.value)
}
