package player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.Kind
import model.MediaSet
import model.markdown.Block
import model.markdown.parseMarkdown
import playback.SummarySource

/** The open title's notes, parsed, and whether their panel is showing. */
data class PlayerNotes(val blocks: List<Block>, val open: Boolean)

/**
 * The notes panel's state — `showSummary` in the web's `player.js`, split
 * out of [PlayerViewModel] to keep that file under the line guideline.
 *
 * Driven by [openSet], like [PlayerHeldController]: a panel belongs to the
 * title it was opened on, so every change of title — an up-next switch
 * included — drops it before the next title's notes load, and
 * `collectLatest` abandons a load the viewer has already moved past. A
 * rotation reopens the same title, [openSet] does not change, and the panel
 * stays as the viewer left it.
 *
 * A lesson's notes open by themselves — they are the point of a lesson. A
 * film's are an extra and wait for the Notes button.
 *
 * Parsed where it lands rather than on a worker, unlike a subtitle track:
 * a lesson's notes are a few kilobytes, a film's transcript is hundreds.
 */
class PlayerNotesController(
    scope: CoroutineScope,
    private val summarySource: SummarySource,
    openSet: StateFlow<MediaSet?>,
) {
    private val _notes = MutableStateFlow<PlayerNotes?>(null)
    val notes: StateFlow<PlayerNotes?> = _notes.asStateFlow()

    init {
        scope.launch {
            openSet.collectLatest { set ->
                _notes.value = null
                if (set == null || !set.hasSummary) return@collectLatest
                val text = summarySource.load(set.setId) ?: return@collectLatest
                val blocks = parseMarkdown(text)
                if (blocks.isNotEmpty()) _notes.value = PlayerNotes(blocks, open = set.kind == Kind.TUTORIAL)
            }
        }
    }

    /** The Notes button, and the panel's own ✕. */
    fun toggle() = _notes.update { it?.copy(open = !it.open) }
}
