package player

import kotlinx.coroutines.CompletableDeferred
import playback.SubtitleTrackSource
import playback.TimedCue

/**
 * In-memory cues, keyed by set and track position — real enough for
 * [SubtitleChoiceController] to load from without a core behind it.
 *
 * [gate], held while running, is what lets a test open a genuine window
 * where [load] is still resolving — the same reason [FakeCatalogRepository]
 * carries one.
 */
class FakeSubtitleTrackSource(
    private val cues: Map<Pair<String, Int>, List<TimedCue>> = emptyMap(),
    private val gate: CompletableDeferred<Unit>? = null,
) : SubtitleTrackSource {

    /** Every `(setId, track)` this fake was asked to load, in order. */
    val loadedFor: MutableList<Pair<String, Int>> = mutableListOf()

    override suspend fun load(setId: String, track: Int): List<TimedCue> {
        loadedFor += setId to track
        gate?.await()
        return cues[setId to track].orEmpty()
    }
}
