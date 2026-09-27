package ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import playback.ActivePreload
import playback.FilmPreloadState
import playback.FilmPreloading
import playback.LanChunkProtocol
import playback.LanPutResult
import playback.LanServer
import playback.LanServerSource
import playback.LanServerStatus
import playback.LanSetStatus

/**
 * As `feature/player`'s own test-only fakes of the same name — duplicated
 * here rather than shared across modules, the same reason `TvAppFixture`
 * and this file's own `LibraryFlowFixture` already keep separate ViewModel
 * fixtures rather than one shared one: each surface's test source set has
 * no way to reach another module's test sources, only its main ones.
 */
internal class FakeFilmPreloading : FilmPreloading {
    private val states = mutableMapOf<String, MutableStateFlow<FilmPreloadState>>()
    val enqueueCalls = mutableListOf<Triple<String, String, Long>>()
    val cancelCalls = mutableListOf<String>()
    val removeCalls = mutableListOf<String>()

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
}

internal class FakeLanServerSource(server: LanServer? = null) : LanServerSource {
    override val server: StateFlow<LanServer?> = MutableStateFlow(server)
    override val searching: StateFlow<Boolean> = MutableStateFlow(false)

    override fun discover() = Unit
}

/** Counts every [setStatus] call — the H1 regression's own probe reads this. */
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
