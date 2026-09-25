package player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import playback.HeldSetsQuery
import playback.PreloadItem
import playback.SeriesPreloading

/** Records every [want] call rather than touching a real cache or thread — see [playback.SeriesPreloading]. */
internal class FakeSeriesPreloader : SeriesPreloading {
    val wantCalls = mutableListOf<Pair<List<PreloadItem>, Long>>()

    private val _heldEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val heldEvents: SharedFlow<String> = _heldEvents

    override fun want(items: List<PreloadItem>, currentPlayingBytes: Long) {
        wantCalls += items to currentPlayingBytes
    }

    /** For a test that wants [heldEvents] to fire without a real write. */
    fun emitHeld(setId: String) {
        _heldEvents.tryEmit(setId)
    }
}

/** A fixed answer rather than a real disk cache — see [playback.HeldSets]. */
internal class FakeHeldSets(private val held: Set<String> = emptySet()) : HeldSetsQuery {
    override suspend fun isHeld(setId: String, totalBytes: Long): Boolean = setId in held

    override suspend fun heldIds(sets: List<Pair<String, Long>>): Set<String> =
        sets.map { it.first }.filterTo(mutableSetOf()) { it in held }
}
