package testing

import data.ArmedCast
import data.CastAnswer
import data.LiveSession
import data.Started
import data.TogetherLive
import kotlinx.coroutines.flow.MutableStateFlow
import model.together.CastTracks
import model.together.Member
import model.together.Playback
import model.together.PlaybackChange
import model.together.Session

/**
 * A [TogetherLive] whose session and availability a test sets by hand, for
 * the screens that only show what is live and pass a Join on to the player:
 * Home and the title pages. [refreshCount] says how often they asked again.
 *
 * The moves that wait for an answer are not faked: a test of them drives
 * `data.DefaultTogetherLive` over [FakeCore], which is the sender they would
 * otherwise be standing in for. The cast moves that answer nothing are
 * recorded in [casts], for the screens that only make them. An offer and a
 * replace are recorded there too, and answered by [onOffer] and [onReplace],
 * which a test sets for the screens that only ask the TV.
 */
class FakeTogetherLive(
    live: LiveSession? = null,
    available: Boolean = true,
) : TogetherLive {
    override val live = MutableStateFlow(live)
    override val requests = MutableStateFlow<LiveSession?>(null)
    override val available = MutableStateFlow(available)

    var refreshCount = 0
        private set

    private var joinNext: Int? = null

    /** The cast moves that answer nothing, in order, as written: `"busy 11 tv-1 Dune"`. */
    val casts = mutableListOf<String>()

    /** What the next offer answers, or throws; it may suspend to stand for a TV that has not answered. */
    var onOffer: suspend () -> CastAnswer = { error(MOVES) }

    /** What the next replace answers, as [onOffer]. */
    var onReplace: suspend () -> CastAnswer = { error(MOVES) }

    private var castNext: ArmedCast? = null

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
    ): Session? {
        casts += "act $id $screen ${change.playing}"
        return null
    }

    override fun leave(
        id: Int,
        screen: String,
    ): Unit = error(MOVES)

    override fun end(
        id: Int,
        screen: String,
    ): Unit = error(MOVES)

    override suspend fun offer(
        me: Member,
        own: Playback,
        profile: String,
    ): CastAnswer {
        casts += "offer ${me.label} ${own.set} $profile"
        return onOffer()
    }

    override suspend fun replace(
        id: Int,
        me: Member,
        own: Playback,
        profile: String,
    ): CastAnswer {
        casts += "replace $id ${own.set} $profile"
        return onReplace()
    }

    override suspend fun accept(
        id: Int,
        me: Member,
    ): LiveSession? = error(MOVES)

    override fun cancel(id: Int) {
        casts += "cancel $id"
    }

    override fun refuse(
        id: Int,
        by: String,
        note: String,
    ) {
        casts += "refuse $id $by $note"
    }

    override fun busy(
        id: Int,
        by: String,
        note: String,
    ) {
        casts += "busy $id $by $note"
    }

    override fun tracks(
        id: Int,
        screen: String,
        tracks: CastTracks,
        audio: String,
        text: String,
    ) {
        casts += "tracks $id $screen $tracks $audio $text"
    }

    override fun choose(
        id: Int,
        screen: String,
        audio: String?,
        text: String?,
    ) {
        casts += "choose $id $screen $audio $text"
    }

    override fun castOnOpen(cast: ArmedCast) {
        castNext = cast
    }

    override fun takeCast(): ArmedCast? = castNext.also { castNext = null }

    private companion object {
        const val MOVES = "this fake does not move sessions: use DefaultTogetherLive over FakeCore"
    }
}
