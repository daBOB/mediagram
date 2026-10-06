package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import catalog.BrowseViewModel
import catalog.CatalogViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ProfileViewModel
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.CoreProvider
import data.LibraryUpdateCoordinator
import data.PortraitRequestLog
import data.WatchSync
import designsystem.InMemoryAppearanceSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import model.Kind
import model.MediaSet
import model.Profile
import model.TitleCredits
import playback.CacheOccupancy
import playback.InMemoryLanCacheSettings
import playback.LanServer
import player.PlayerViewModel
import player.TitlePreloadViewModel
import settings.InMemoryTmdbSettings
import stats.AchievementDotViewModel
import stats.InMemoryAchievementsSeen
import stats.NO_ACHIEVEMENTS
import stats.StatsRead
import stats.StatsViewModel
import setup.SettingsCompletion
import setup.SettingsUiState
import setup.SettingsViewModel
import system.CacheBudgetViewModel
import system.FetchViewModel
import system.LanCacheViewModel
import system.SystemUiState
import system.SystemViewModel
import testing.FakeCore
import testing.FakeCoreProvider
import testing.WatchStateFixture
import ui.player.PlayerLifecycleFixture
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.StatsSummary
import java.time.ZonedDateTime
import catalog.ShelfViewModel
import catalog.SearchViewModel
import settings.InMemoryShelfViewSettings
import setup.AppearanceViewModel
import setup.ProfileSettingsViewModel

/** Real routing and catalog/profile/player ViewModels; only external IO and unrelated menu facts are controlled. */
internal class LibraryFlowFixture(
    loading: Boolean = false,
    settingsModel: SettingsViewModel? = null,
    achievements: Achievements = NO_ACHIEVEMENTS,
) : ViewModelStoreOwner,
    AutoCloseable {
    override val viewModelStore = ViewModelStore()
    private val viewer = Profile("viewer", "Viewer")

    /** The viewer is chosen, with one list, Favourites, holding the first episode. */
    val playback =
        PlayerLifecycleFixture(
            WatchStateFixture(listOf(viewer)) {
                createList("Favourites")?.let { setInList(it.id, "episode-1", true) }
            },
        )
    val catalogReady = CompletableDeferred<Unit>()
    var refreshes = 0
    var startOvers = 0
    var signedOut = 0
    val settings = settingsModel ?: mockk<SettingsViewModel>(relaxed = true)

    /** What this device has shown the fixture's viewer; a test marks it to put the dot out. */
    val achievementsSeen = InMemoryAchievementsSeen()

    /** The Stats page's ViewModel — relaxed, so a test can verify what the page asked of it. */
    val stats = mockk<StatsViewModel>(relaxed = true)

    val repository = mockk<CatalogRepository>()
    val catalog: CatalogViewModel
    val player: PlayerViewModel

    /** Drives and counts what a film page's Preload control does — see `TitlePreloadFixtures.kt`. Paired by default, so the server line is reachable without a test wiring one up itself. */
    val filmPreloading = FakeFilmPreloading()
    val lanServerSource = FakeLanServerSource(LanServer(baseUrl = "http://192.168.1.9:7788", host = "192.168.1.9:7788"))
    val lanChunkProtocol = FakeLanChunkProtocol()

    init {
        if (!loading) catalogReady.complete(Unit)
        val stored = playback.repository
        coEvery { repository.refresh() } coAnswers {
            refreshes++
            Result.success(sets.size)
        }
        coEvery { repository.sets() } coAnswers {
            catalogReady.await()
            sets
        }
        coEvery { repository.titleInfo(any()) } returns null
        // The title/series page's own Cast tab and franchise link: this
        // fixture's own sets carry neither, so both stay off exactly as they
        // did before either existed — but the pages ask every time they
        // render, and a strict `mockk<CatalogRepository>()` throws for an
        // unstubbed call whether or not a test ever opens that tab.
        coEvery { repository.titleCredits(any()) } returns TitleCredits.Empty
        coEvery { repository.fetchPortrait(any()) } returns null
        val enrichment = CatalogEnrichmentFetcher(mockk<CoreProvider>(), InMemoryTmdbSettings())
        catalog = CatalogViewModel(repository, stored, LibraryUpdateCoordinator(repository, enrichment))
        player = playback.factory.create(PlayerViewModel::class.java)
        if (settingsModel == null) {
            every { settings.state } returns MutableStateFlow(SettingsUiState(account = "Viewer", library = "Library"))
            every { settings.completions } returns MutableStateFlow<List<SettingsCompletion>>(emptyList())
        }
        val system = mockk<SystemViewModel>(relaxed = true)
        every { system.failure } returns MutableStateFlow(null)
        every { system.state } returns
            MutableStateFlow(
                SystemUiState("channel", sets.size.toLong(), 0, 4, null, null, 0, 1_000_000, "Internal storage", false, 0, 0, 0, 0, true, "test", 0),
            )
        val cache = mockk<CacheBudgetViewModel>(relaxed = true)
        every { cache.state } returns
            MutableStateFlow(CacheOccupancy(heldBytes = 0, budgetBytes = 1_000_000, volumeLabel = "Internal storage", fellBack = false, capBytes = 1_000_000))
        every { cache.failure } returns MutableStateFlow(null)
        // CacheVolumeBlock (Settings' own "Where" row) reads these two
        // directly, unwrapped from `relaxed`'s own answer for a generic
        // StateFlow — which is not a List, and throws a ClassCastException
        // the moment this screen collects it. Empty is the same "nothing to
        // choose between" CacheVolumeBlock already renders as nothing.
        every { cache.volumes } returns MutableStateFlow(emptyList())
        every { cache.chosenVolumeId } returns MutableStateFlow(null)
        // LanCacheBlock (Settings' own "Home cache server" row), the same
        // reason: `state == null` is its own already-handled "nothing to
        // show yet" — the same row a real device shows before its first
        // discovery pass returns.
        val lanCache = mockk<LanCacheViewModel>(relaxed = true)
        every { lanCache.state } returns MutableStateFlow(null)
        every { lanCache.failure } returns MutableStateFlow(null)
        // The Stats page resolves StatsViewModel through hiltViewModel(),
        // the same reason every entry below exists; an empty history is the
        // page a fresh profile shows.
        every { stats.state } returns
            MutableStateFlow<StatsRead>(
                StatsRead.Done(StatsSummary(weekSeconds = 0.0, monthSeconds = 0.0, allSeconds = 0.0, last30 = emptyList(), history = emptyList()), ZonedDateTime.now()),
            )
        // The rail's dot reads the chosen profile's achievements from a core of its own.
        val achievementsCore = FakeCore().apply { achievementsByProfile = mapOf(viewer.id to achievements) }
        val models =
            mapOf<Class<out ViewModel>, ViewModel>(
                CatalogViewModel::class.java to catalog,
                ProfileViewModel::class.java to ProfileViewModel(stored, mockk<WatchSync>(relaxed = true)),
                // The gate resolves Manage profiles beside the picker every time it draws.
                ManageProfilesViewModel::class.java to ManageProfilesViewModel(stored, mockk<WatchSync>(relaxed = true)),
                FetchViewModel::class.java to FetchViewModel(enrichment),
                PlayerViewModel::class.java to player,
                SettingsViewModel::class.java to settings,
                SystemViewModel::class.java to system,
                CacheBudgetViewModel::class.java to cache,
                ShelfViewModel::class.java to ShelfViewModel(InMemoryShelfViewSettings()),
                SearchViewModel::class.java to SearchViewModel(repository),
                BrowseViewModel::class.java to BrowseViewModel(repository, PortraitRequestLog()),
                LanCacheViewModel::class.java to lanCache,
                StatsViewModel::class.java to stats,
                // LibraryFlow resolves the dot's ViewModel through hiltViewModel(), the same
                // reason every entry here exists — and every library test reaches it.
                AchievementDotViewModel::class.java to AchievementDotViewModel(FakeCoreProvider(achievementsCore), stored, achievementsSeen),
                // A film's own TitleDetailScreen resolves TitlePreloadViewModel
                // through hiltViewModel() too — the same reason every entry
                // here exists. Real, over fakes this fixture exposes so a
                // test can drive a state or count a poll.
                TitlePreloadViewModel::class.java to
                    TitlePreloadViewModel(filmPreloading, InMemoryLanCacheSettings(), lanServerSource, lanChunkProtocol),
                // MobileApp's MediagramTheme and SettingsScreen (reachable from
                // this flow's own menu) each resolve an AppearanceViewModel
                // through hiltViewModel(); this owner has to hand it back too.
                AppearanceViewModel::class.java to AppearanceViewModel(InMemoryAppearanceSettings()),
                ProfileSettingsViewModel::class.java to profileSettingsModel(),
            )
        val provider =
            ViewModelProvider(
                viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.getValue(modelClass))!!
                },
            )
        // Use the real provider's keys so Hilt retrieval finds the same held
        // instances. The test replaces only its generated-Activity factory lookup.
        models.keys.forEach { provider[it] }
    }

    override fun close() {
        try {
            viewModelStore.clear()
        } finally {
            playback.close()
        }
    }

    private val sets get() =
        listOf(
            episode("episode-1", "First episode", 1),
            episode("episode-2", "Second episode", 2),
            // Two films sharing a genre — a film's own "Similar" tab needs a
            // second one to offer, per `similarTo`'s "shares a genre" gate.
            film("film-1", "Example Film"),
            film("film-2", "Second Feature"),
        )

    private fun episode(
        id: String,
        title: String,
        season: Int,
    ) = MediaSet(
        setId = id,
        kind = Kind.EPISODE,
        title = title,
        show = "Example Show",
        chapter = null,
        path = null,
        season = season,
        episodeFirst = 1,
        episodeLast = 1,
        year = 2020,
        durationSecs = 600,
        posterPath = null,
        totalBytes = 10,
    )

    /** A film's own Preload control needs [MediaSet.totalBytes] > 0 — a size real enough that a test can also check "5.0 GB" reads back correctly. */
    private fun film(id: String, title: String) = MediaSet(
        setId = id,
        kind = Kind.MOVIE,
        title = title,
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = 2021,
        durationSecs = 9000,
        posterPath = null,
        totalBytes = 5 * 1_073_741_824L,
        genres = listOf("Drama"),
    )
}
