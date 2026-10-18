package player

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import data.CatalogRepository
import data.LiveSession
import data.TOGETHER_UNREACHABLE
import data.TogetherLive
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.MediaSet
import model.together.CastWords
import model.together.Ending
import model.together.Member
import model.together.PlaybackChange
import model.together.Session
import model.together.acted
import model.together.changeOf
import model.together.chose
import model.together.endingOf
import model.together.follow
import player.together.CastRemotePlayer
import player.together.CastRemoteView
import player.together.RemoteUi
import player.together.sayOnRemote
import java.util.UUID
import javax.inject.Inject

/**
 * The remote of a title playing on the TV, on a phone or tablet: it joins
 * the cast session as a remote member, follows it, and turns the player
 * card's presses into the viewer's moves, as the web player's
 * `cast-remote.js`. Leaving never ends the cast; only [stop] does.
 */
@HiltViewModel
class CastRemoteViewModel internal constructor(
    private val live: TogetherLive,
    catalog: CatalogRepository,
    label: String,
    private val now: () -> Long,
) : ViewModel() {
    @Inject constructor(live: TogetherLive, catalog: CatalogRepository, @ApplicationContext context: Context) :
        this(live, catalog, labelOf(context), System::currentTimeMillis)

    private val me = Member(UUID.randomUUID().toString(), label, remote = true)
    private val view = CastRemoteView(viewModelScope, catalog, CastRemotePlayer(Looper.getMainLooper(), now, ::move))

    val ui: StateFlow<RemoteUi?> = view.ui

    /** The player card's player: it shows the TV's session, and each press on it is a move. */
    val player: Player = view.player

    private var id: Int? = null
    private var current: Session? = null
    private var following: Job? = null

    /** Joins cast [id] as a remote and follows it; Next episode goes on through [runOf] a title's run, as the player's own. */
    fun open(
        id: Int,
        runOf: (MediaSet) -> List<String>,
    ) {
        if (this.id != null) return
        this.id = id
        view.open(id, runOf)
        live.live.value?.takeIf { it.id == id }?.let { take(it.session) }
        following =
            viewModelScope.launch {
                val joined = runCatching { withContext(NonCancellable) { live.join(id, me) } }
                // Once read, a join is sent whatever became of this screen: one closed meanwhile leaves after it.
                if (this@CastRemoteViewModel.id != id) return@launch live.leave(id, me.screen)
                joined
                    .onFailure { view.show(TOGETHER_UNREACHABLE) }
                    .onSuccess { if (it == null) return@launch finish(null) else take(it.session) }
                live.live.collect(::heard)
            }
    }

    /** Back out of the remote: this screen leaves, and the TV plays on. */
    fun close() {
        val id = id ?: return
        release()
        view.close()
        live.leave(id, me.screen)
    }

    /** Stop: the cast is over for everyone, and the TV closes its player. */
    fun stop() {
        val id = id ?: return
        release()
        view.close()
        live.end(id, me.screen)
    }

    fun next() {
        move(PlaybackChange(set = view.next ?: return, pos = 0.0, playing = true))
    }

    fun chooseAudio(track: String) = choose(audio = track)

    fun chooseText(track: String) = choose(text = track)

    override fun onCleared() = close()

    private fun choose(
        audio: String? = null,
        text: String? = null,
    ) {
        val id = id ?: return
        take(chose(current?.takeIf { it.cast != null } ?: return, me.screen, audio, text))
        live.choose(id, me.screen, audio, text)
    }

    /**
     * The viewer's move on the card: shown at once, and sent at the
     * session's expected position unless it names one. One the channel no
     * longer has comes back as the session gone.
     */
    private fun move(change: PlaybackChange) {
        val id = id ?: return
        take(acted(current ?: return, change, me.screen, now()))
        viewModelScope.launch { live.act(id, me.screen, change) }
    }

    /** What [live] holds now, read or heard alike: another session, or none, is this one over. */
    private fun heard(heard: LiveSession?) {
        if (id == null) return
        val session = heard?.takeIf { it.id == id }?.session
        if (session == null || session.ended) return finish(session?.let { endingOf(it, me.screen) })
        val before = current
        // A move given up on comes back as what the channel holds, which this screen follows too.
        val next = if (heard.lost) session else follow(before, session, me.screen)
        if (next === before) return
        take(next)
        if (before != null && next.by != me.screen) changeOf(before, next)?.let { sayOnRemote(it, next) }?.let(view::show)
    }

    private fun take(session: Session) {
        current = session
        view.draw(session)
    }

    /** The cast is over here: said, for the screen to show and go. */
    private fun finish(ending: Ending?) {
        release()
        view.over(if (ending == Ending.FINISHED) CastWords.FINISHED else CastWords.STOPPED)
    }

    private fun release() {
        id = null
        current = null
        following?.cancel()
        following = null
    }
}
