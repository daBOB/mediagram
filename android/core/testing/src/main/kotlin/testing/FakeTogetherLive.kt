package testing

import data.LiveSession
import data.Started
import data.TogetherLive
import kotlinx.coroutines.flow.MutableStateFlow
import model.together.Member
import model.together.Playback
import model.together.PlaybackChange
import model.together.Session

/**
 * A [TogetherLive] whose session and availability a test sets by hand, for
 * the screens that only show what is live and pass a Join on to the player:
 * Home and the title pages. [refreshCount] says how often they asked again.
 *
 * The moves are not faked: a test of them drives `data.DefaultTogetherLive`
 * over [FakeCore], which is the sender they would otherwise be standing in for.
 */
class FakeTogetherLive(
    live: LiveSession? = null,
    available: Boolean = true,
) : TogetherLive {
    override val live = MutableStateFlow(live)
    override val available = MutableStateFlow(available)

    var refreshCount = 0
        private set

    private var joinNext: Int? = null

    override suspend fun refresh(): LiveSession? {
        refreshCount++
        return live.value
    }

    override fun joinOnOpen(id: Int) {
        joinNext = id
    }

    override fun takeJoin(): Int? = joinNext.also { joinNext = null }

    override suspend fun start(
        me: Member,
        own: Playback,
    ): Started = error(MOVES)

    override suspend fun join(
        id: Int,
        me: Member,
    ): LiveSession? = error(MOVES)

    override suspend fun act(
        id: Int,
        screen: String,
        change: PlaybackChange,
    ): Session? = error(MOVES)

    override fun leave(
        id: Int,
        screen: String,
    ): Unit = error(MOVES)

    override fun end(
        id: Int,
        screen: String,
    ): Unit = error(MOVES)

    private companion object {
        const val MOVES = "this fake does not move sessions: use DefaultTogetherLive over FakeCore"
    }
}
