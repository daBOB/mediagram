package player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import playback.ActivePreload
import playback.FilmPreloadRow
import playback.FilmPreloadState
import playback.FilmPreloading
import playback.HeldSetsQuery
import playback.LanChunkProtocol
import playback.LanPutResult
import playback.LanServer
import playback.LanServerSource
import playback.LanServerStatus
import playback.LanSetStatus
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

    override suspend fun heldBytes(setId: String, totalBytes: Long): Long = if (setId in held) totalBytes else 0L
}

/** A settable per-film [FilmPreloadState], plus every enqueue/cancel/remove call — see [playback.FilmPreloading]. */
internal class FakeFilmPreloading : FilmPreloading {
    private val states = mutableMapOf<String, MutableStateFlow<FilmPreloadState>>()
    val enqueueCalls = mutableListOf<Triple<String, String, Long>>()
    val cancelCalls = mutableListOf<String>()
    val removeCalls = mutableListOf<String>()

    /** Pushes a new state for [setId] — the only way a test drives what [stateOf] reports. */
    fun setState(setId: String, totalBytes: Long, state: FilmPreloadState) {
        flowFor(setId, totalBytes).value = state
    }

    private fun flowFor(setId: String, totalBytes: Long) =
        states.getOrPut(setId) { MutableStateFlow(FilmPreloadState.Idle(0L, totalBytes)) }

    override fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState> = flowFor(setId, totalBytes)

    override fun enqueue(setId: String, title: String, totalBytes: Long) {
        enqueueCalls += Triple(setId, title, totalBytes)
    }

    override fun cancel(setId: String) {
        cancelCalls += setId
    }

    override fun remove(setId: String) {
        removeCalls += setId
    }

    override fun pauseForTimeLimit() = Unit

    override val heldEvents: SharedFlow<String> = MutableSharedFlow()
    override val unheldEvents: SharedFlow<String> = MutableSharedFlow()
    override val hasWork: StateFlow<Boolean> = MutableStateFlow(false)
    override val active: StateFlow<ActivePreload?> = MutableStateFlow(null)

    private val _queueOverview = MutableStateFlow<List<FilmPreloadRow>>(emptyList())
    override val queueOverview: StateFlow<List<FilmPreloadRow>> = _queueOverview

    /** The only way a test drives what [queueOverview] reports. */
    fun setQueueOverview(rows: List<FilmPreloadRow>) {
        _queueOverview.value = rows
    }

    private val _timeLimitPaused = MutableStateFlow<List<FilmPreloadRow.TimeLimitPaused>>(emptyList())
    override val timeLimitPaused: StateFlow<List<FilmPreloadRow.TimeLimitPaused>> = _timeLimitPaused

    /** The only way a test drives what [timeLimitPaused] reports. */
    fun setTimeLimitPaused(rows: List<FilmPreloadRow.TimeLimitPaused>) {
        _timeLimitPaused.value = rows
    }
}

/** A fixed (or absent) paired server — see [playback.LanServerSource]. */
internal class FakeLanServerSource(server: LanServer? = null) : LanServerSource {
    override val server: StateFlow<LanServer?> = MutableStateFlow(server)
    override val searching: StateFlow<Boolean> = MutableStateFlow(false)

    override fun discover() = Unit
}

/** Answers [setStatus] with whatever [answer] currently holds, and counts how often it was asked — the rest of [playback.LanChunkProtocol] is never exercised through this fake. */
internal class FakeLanChunkProtocol(var answer: LanSetStatus? = null) : LanChunkProtocol {
    var setStatusCalls = 0
        private set

    override suspend fun get(baseUrl: String, setId: String, index: Long, expectedLength: Int): ByteArray? = null

    override suspend fun put(baseUrl: String, token: String, setId: String, index: Long, total: Long, body: ByteArray): LanPutResult =
        LanPutResult.Stored

    override suspend fun verify(baseUrl: String): Boolean = true

    override suspend fun status(baseUrl: String): LanServerStatus? = null

    override suspend fun setStatus(baseUrl: String, setId: String): LanSetStatus? {
        setStatusCalls++
        return answer
    }
}
