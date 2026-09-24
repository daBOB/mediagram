package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import catalog.CatalogViewModel
import catalog.profile.ProfileViewModel
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.CoreProvider
import data.LibraryUpdateCoordinator
import data.WatchSync
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import model.Kind
import model.ListOfSets
import model.MediaSet
import model.Profile
import model.WatchSnapshot
import playback.CacheOccupancy
import player.PlayerViewModel
import settings.InMemoryTmdbSettings
import setup.SettingsCompletion
import setup.SettingsUiState
import setup.SettingsViewModel
import system.CacheBudgetViewModel
import system.FetchViewModel
import system.SystemUiState
import system.SystemViewModel
import ui.player.PlayerLifecycleFixture

/** Real routing and catalog/profile/player ViewModels; only external IO and unrelated menu facts are controlled. */
internal class LibraryFlowFixture(
    loading: Boolean = false,
    settingsModel: SettingsViewModel? = null,
) : ViewModelStoreOwner,
    AutoCloseable {
    override val viewModelStore = ViewModelStore()
    val playback = PlayerLifecycleFixture()
    val catalogReady = CompletableDeferred<Unit>()
    var refreshes = 0
    var startOvers = 0
    var signedOut = 0
    val settings = settingsModel ?: mockk<SettingsViewModel>(relaxed = true)
    val repository = mockk<CatalogRepository>()
    val watch = MutableStateFlow(WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("list", "Favourites", listOf("episode-1")))))
    val catalog: CatalogViewModel
    val player: PlayerViewModel

    init {
        if (!loading) catalogReady.complete(Unit)
        val stored = playback.repository
        every { stored.profiles } returns MutableStateFlow(listOf(Profile("viewer", "Viewer")))
        every { stored.chosenProfileId } returns MutableStateFlow("viewer")
        every { stored.snapshot } returns watch
        coEvery { repository.refresh() } coAnswers {
            refreshes++
            Result.success(sets.size)
        }
        coEvery { repository.sets() } coAnswers {
            catalogReady.await()
            sets
        }
        coEvery { repository.titleInfo(any()) } returns null
        coEvery { repository.posterPath(any()) } returns null
        val enrichment = CatalogEnrichmentFetcher(mockk<CoreProvider>(), InMemoryTmdbSettings())
        catalog = CatalogViewModel(repository, stored, LibraryUpdateCoordinator(repository, enrichment))
        player = playback.factory.create(PlayerViewModel::class.java)
        if (settingsModel == null) {
            every { settings.state } returns MutableStateFlow(SettingsUiState(account = "Viewer", library = "Library"))
            every { settings.completions } returns MutableStateFlow<List<SettingsCompletion>>(emptyList())
        }
        val system = mockk<SystemViewModel>(relaxed = true)
        every { system.state } returns
            MutableStateFlow(
                SystemUiState("channel", sets.size.toLong(), 0, 4, null, null, 0, 1_000_000, 0, 0, 0, 0, true, "test", 0),
            )
        val cache = mockk<CacheBudgetViewModel>(relaxed = true)
        every { cache.state } returns MutableStateFlow(CacheOccupancy(0, 1_000_000))
        val models =
            mapOf<Class<out ViewModel>, ViewModel>(
                CatalogViewModel::class.java to catalog,
                ProfileViewModel::class.java to ProfileViewModel(stored, mockk<WatchSync>(relaxed = true)),
                FetchViewModel::class.java to FetchViewModel(enrichment),
                PlayerViewModel::class.java to player,
                SettingsViewModel::class.java to settings,
                SystemViewModel::class.java to system,
                CacheBudgetViewModel::class.java to cache,
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
}
