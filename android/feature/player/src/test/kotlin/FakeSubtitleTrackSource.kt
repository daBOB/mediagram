package player

import kotlinx.coroutines.CompletableDeferred
import playback.SubtitleTrackSource
import playback.TimedCue

/**
 * In-memory cues, keyed by set and language — real enough for
 * [SubtitleChoiceController] to load from without a core behind it.
 *
 * [gate], held while running, is what lets a test open a genuine window
 * where [load] is still resolving — the same reason [FakeCatalogRepository]
 * carries one.
 */
class FakeSubtitleTrackSource(
    private val cues: Map<Pair<String, String>, List<TimedCue>> = emptyMap(),
    private val gate: CompletableDeferred<Unit>? = null,
) : SubtitleTrackSource {

    /** Every `(setId, lang)` this fake was asked to load, in order. */
    val loadedFor: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun load(setId: String, lang: String): List<TimedCue> {
        loadedFor += setId to lang
        gate?.await()
        return cues[setId to lang].orEmpty()
    }
}
