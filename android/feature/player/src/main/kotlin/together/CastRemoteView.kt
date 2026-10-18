package player.together

import androidx.media3.common.C
import data.CatalogRepository
import data.orDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.MediaSet
import model.together.CastTracks
import model.together.CastWords
import model.together.Session
import player.nextInQueue
import player.titleLine

/**
 * What the remote screen draws: the title or episode and its backdrop, the
 * label, the TV's two-second [notice], the TV's [tracks] and those it plays,
 * whether Next episode has a title to go to, and, once the cast is over,
 * the words it ended with ([over]), which the screen shows for two seconds
 * before it goes.
 */
data class RemoteUi(
    val id: Int,
    val title: String,
    val backdrop: String?,
    val label: String,
    val notice: String?,
    val tracks: CastTracks,
    val audio: String,
    val text: String,
    val hasNext: Boolean,
    val over: String?,
)

/**
 * Draws [RemoteUi] and the player card's [player], the twin of the web
 * player's `cast-remote-card.js`: `CastRemoteViewModel` decides, this draws.
 * The title, its picture and its length are looked up once per title.
 */
internal class CastRemoteView(
    private val scope: CoroutineScope,
    private val catalog: CatalogRepository,
    val player: CastRemotePlayer,
) {
    private val _ui = MutableStateFlow<RemoteUi?>(null)
    val ui: StateFlow<RemoteUi?> = _ui.asStateFlow()

    private var id = 0
    private var session: Session? = null
    private var runOf: (MediaSet) -> List<String> = { emptyList() }

    /** The set the session plays, once looked up; `null` until then, or when this catalog lacks it. */
    private var set: MediaSet? = null

    /** What Next episode plays; `null` with no title after this one. */
    var next: String? = null
        private set
    private var notice: String? = null
    private var hide: Job? = null

    /** Draws cast [id]; Next episode goes on through [runOf] a title's run. */
    fun open(
        id: Int,
        runOf: (MediaSet) -> List<String>,
    ) {
        this.id = id
        this.runOf = runOf
    }

    /** Draws [s], looking its title up again when it plays another one. */
    fun draw(s: Session) {
        val moved = session?.set != s.set
        session = s
        if (moved) {
            set = null
            next = null
            scope.launch { lookUp(s.set) }
        }
        redraw()
    }

    fun show(text: String) {
        notice = text
        redraw()
        hide?.cancel()
        hide =
            scope.launch {
                delay(NOTICE_MS)
                notice = null
                redraw()
            }
    }

    /** The cast is over: [words] say how. */
    fun over(words: String) {
        clear()
        _ui.update { it?.copy(notice = null, over = words) }
    }

    /** Nothing is drawn: the remote is gone. */
    fun close() {
        clear()
        _ui.value = null
    }

    private fun clear() {
        session = null
        notice = null
        hide?.cancel()
    }

    private suspend fun lookUp(setId: String) {
        val found = orDefault(null, "looking up $setId") { catalog.mediaSet(setId) }
        if (session?.set != setId) return
        set = found
        next = found?.let { nextInQueue(runOf(it), setId) }
        redraw()
    }

    private fun redraw() {
        val s = session ?: return
        val set = set
        player.show(s, set?.durationSecs?.let { it * 1000L } ?: C.TIME_UNSET)
        _ui.value =
            RemoteUi(
                id, titleLine(set), set?.let { it.backdropPath ?: it.posterPath }, CastWords.PLAYING_ON_TV, notice,
                s.cast?.tracks ?: CastTracks(), s.cast?.audio.orEmpty(), s.cast?.text.orEmpty(), next != null, over = null,
            )
    }
}
