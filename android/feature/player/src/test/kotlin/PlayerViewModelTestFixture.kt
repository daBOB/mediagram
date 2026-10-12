package player

import android.content.Context
import data.TogetherLive
import data.WatchStateRepository
import io.mockk.mockk
import data.WatchSync
import playback.PlaybackCounters
import playback.SummarySource
import player.together.ViewerRelay
import testing.FakeTogetherLive
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

/** A [PlayerViewModel] wired with fakes a test can inspect — shared across `PlayerViewModelTest`, `PlayerResumePositionTest` and `PlayerSaveTickerTest`. */
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
    together: TogetherLive = FakeTogetherLive(),
    relay: ViewerRelay = ViewerRelay(),
    context: Context = mockk(relaxed = true),
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
    together,
    relay,
    context,
)
