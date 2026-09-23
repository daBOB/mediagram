package player

import data.WatchSync
import playback.PlaybackCounters

/**
 * A [PlayerViewModel] wired with fakes that record and sync nothing worth
 * asserting on — for tests that only care about the player-handle
 * mechanics ([PlayerReopenTest], [PlayerConstructionFailureTest]), not
 * [ProgressRecorder] or [WatchSync]. [PlayerViewModelTest] builds its own,
 * with fakes it inspects.
 */
internal fun testViewModel(handle: PlayerHandle): PlayerViewModel {
    val repository = FakeWatchStateRepository()
    return PlayerViewModel(handle, PlaybackCounters(), repository, ProgressRecorder(repository), NoopWatchSync)
}

private object NoopWatchSync : WatchSync {
    override fun onForeground() = Unit
    override fun onBackground() = Unit
    override fun soon() = Unit
    override suspend fun awaitFirstRound() = Unit
}
