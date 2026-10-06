package player

import data.WatchStateRepository
import data.WatchSync
import playback.PlaybackCounters
import playback.SummarySource
import testing.FakeHeldSets
import testing.FakeSeriesPreloader
import testing.WatchStateFixture

/** Records `soon()` calls rather than doing anything with them — shared by every [PlayerViewModel] test that does not care about sync itself. */
internal class FakeWatchSync : WatchSync {
    var soonCalls = 0
        private set

    override fun onForeground() = Unit
    override fun onBackground() = Unit
    override fun soon() {
        soonCalls++
    }
    override suspend fun awaitFirstRound() = Unit
}

/** A [PlayerViewModel] wired with fakes a test can inspect — shared across `PlayerViewModelTest`, `PlayerResumePositionTest` and `PlayerSaveTickerTest`, split apart to keep each file under the project's line guideline. */
internal fun buildViewModel(
    handle: FakePlayerHandle = FakePlayerHandle(),
    repository: WatchStateRepository = WatchStateFixture().repository,
    watchSync: FakeWatchSync = FakeWatchSync(),
    catalogRepository: FakeCatalogRepository = FakeCatalogRepository(),
    preferences: FakePlayerPreferences = FakePlayerPreferences(),
    subtitleTrackSource: FakeSubtitleTrackSource = FakeSubtitleTrackSource(),
    playbackServiceController: FakePlaybackServiceController = FakePlaybackServiceController(),
    seriesPreloader: FakeSeriesPreloader = FakeSeriesPreloader(),
    heldSets: FakeHeldSets = FakeHeldSets(),
    summarySource: SummarySource = SummarySource.None,
) = PlayerViewModel(
    handle,
    PlaybackCounters(),
    repository,
    ProgressRecorder(repository),
    watchSync,
    catalogRepository,
    preferences,
    subtitleTrackSource,
    playbackServiceController,
    seriesPreloader,
    heldSets,
    summarySource,
)
