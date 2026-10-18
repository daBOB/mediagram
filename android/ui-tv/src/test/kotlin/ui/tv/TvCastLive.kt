package ui.tv

import data.LiveSession
import data.TogetherLive
import kotlinx.coroutines.CompletableDeferred
import model.together.Member
import testing.FakeTogetherLive

/**
 * What the TV's cast receiver and its player hear, one instance between them
 * as in the app: a test puts a cast request up through [fake]'s `requests`.
 * The player's accept waits on the test rather than failing at once, so a
 * cast stays open until [lose] answers that another TV took it, which is
 * over for this one.
 */
internal class TvCastLive(
    val fake: FakeTogetherLive = FakeTogetherLive(),
) : TogetherLive by fake {
    private val answer = CompletableDeferred<LiveSession?>()

    override suspend fun accept(
        id: Int,
        me: Member,
    ): LiveSession? = answer.await()

    /** The cast this TV took is another's now: its player closes. */
    fun lose() {
        answer.complete(null)
    }
}
