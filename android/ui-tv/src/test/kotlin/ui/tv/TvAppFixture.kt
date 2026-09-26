package ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import catalog.CatalogViewModel
import catalog.SearchViewModel
import catalog.profile.ProfileViewModel
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.CoreClient
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.LibraryUpdateCoordinator
import data.StoredCoreProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import model.MediaSet
import model.Profile
import model.WatchSnapshot
import playback.CacheOccupancy
import playback.CacheVolume
import playback.HeldSetsQuery
import playback.INTERNAL_VOLUME_ID
import player.PlayerViewModel
import settings.InMemoryLibrarySettings
import settings.InMemoryTelegramSettings
import settings.InMemoryTmdbSettings
import setup.Libraries
import setup.SettingsCompletion
import setup.SettingsUiState
import setup.SettingsViewModel
import setup.SetupViewModel
import setup.login.LoginViewModel
import system.CacheBudgetViewModel
import system.FetchViewModel
import system.LanCacheConnection
import system.LanCacheUiState
import system.LanCacheViewModel
import system.SystemUiState
import system.SystemViewModel
import ui.tv.player.TvPlayerFixture
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SearchHit
import java.io.File

/** Which of [SetupViewModel]'s outstanding steps a [TvAppFixture] should land on. */
internal enum class TvSetupStage { APPLICATION, SIGN_IN, LIBRARY, READY }

/**
 * A real [SetupViewModel] over a mocked [CoreClient] — the minimum TvApp's
 * setup screens need, not ui-mobile's full login/library fixture. TvApp and
 * `TvSetupStep` only ever drive [SetupViewModel] itself; sign-in and library
 * selection each have their own ViewModel, which nothing under test here
 * reaches into.
 *
 * [stage] alone decides the outcome: an application identity is on record
 * for every stage but [TvSetupStage.APPLICATION] (otherwise the step would
 * always be `NeedsApplication`, telling a test nothing about the branch it
 * exists to check), the core reports authorized from [TvSetupStage.LIBRARY]
 * on, and only [TvSetupStage.READY] also picks a library — the three
 * remaining questions [SetupViewModel] asks before it reports `Ready`.
 *
 * [profiles] and [chosenProfileId] back a real [ProfileViewModel] the same
 * way — `TvApp` resolves one through `TvProfileGate` the moment `Ready` is
 * reached, so any test that reaches `READY` needs one ready to resolve too,
 * over [FakeWatchStateRepository] rather than the `CoreClient` mock the
 * setup plumbing above uses: that mock is stubbed only for the calls
 * `SetupViewModel` itself makes. A real [CatalogViewModel] over a mocked
 * [CatalogRepository] holding [sets] stands behind the catalogue the gate
 * opens onto, the way ui-mobile's `LibraryFlowFixture` builds its own. A
 * real [SearchViewModel] asks the same repository, whose search stands in
 * for the core's ranking with a plain match on each set's title. [watch]
 * is where the viewer already stands, and [heldIds] the titles this device
 * is taken to hold in full.
 */
internal class TvAppFixture(
    stage: TvSetupStage,
    profiles: List<Profile> = emptyList(),
    chosenProfileId: String? = null,
    sets: List<MediaSet> = emptyList(),
    watch: WatchSnapshot = WatchSnapshot.Empty,
    heldIds: Set<String> = emptySet(),
) : ViewModelStoreOwner, AutoCloseable {
    override val viewModelStore = ViewModelStore()
    val setup: SetupViewModel
    private val login: LoginViewModel
    private val profile: ProfileViewModel
    val catalog: CatalogViewModel
    private val search: SearchViewModel
    private val fetch: FetchViewModel
    private val playback: TvPlayerFixture
    private val player: PlayerViewModel
    val settings = mockk<SettingsViewModel>(relaxed = true)
    private val system = mockk<SystemViewModel>(relaxed = true)
    val cacheBudget = mockk<CacheBudgetViewModel>(relaxed = true)
    val lanCache = mockk<LanCacheViewModel>(relaxed = true)
    val lanCacheState =
        MutableStateFlow<LanCacheUiState?>(
            LanCacheUiState(
                enabled = true,
                hasToken = false,
                manualAddress = "",
                connection = LanCacheConnection.NOT_FOUND,
                connectedHost = null,
                heldBytes = null,
                tokenRejected = false,
            ),
        )

    init {
        val core = mockk<CoreClient>()
        every { core.isAuthorized() } returns (stage == TvSetupStage.LIBRARY || stage == TvSetupStage.READY)
        coEvery { core.listLibraries() } returns listOf(LibraryChoice("films", "Family films"))
        val telegram = InMemoryTelegramSettings()
        val library = InMemoryLibrarySettings()
        val dispatcher = Dispatchers.Main.immediate
        val provider = StoredCoreProvider(telegram, dispatcher) { core }
        val libraries = Libraries(provider, library, dispatcher)
        val watchState = DefaultWatchStateRepository(provider, dispatcher)
        runBlocking {
            if (stage != TvSetupStage.APPLICATION) telegram.write(1234, "0123456789abcdef0123456789abcdef")
            if (stage == TvSetupStage.READY) library.write("films")
        }
        setup =
            SetupViewModel(
                coreProvider = provider,
                libraries = libraries,
                tmdbSettings = InMemoryTmdbSettings(),
                coreStorage = InMemoryCoreStorage(),
                dispatcher = dispatcher,
                watchState = watchState,
            )
        // TvSignInScreen, TvProfileGate and the library each resolve their
        // own ViewModel through hiltViewModel(), the same way TvApp resolves
        // SetupViewModel — this ViewModelStoreOwner has to hand back every one,
        // or reaching that step through TvApp falls back to
        // ViewModelProvider's default factory, which cannot construct one
        // with no Hilt entry point to supply its arguments.
        login = LoginViewModel(provider, dispatcher)
        val viewer = FakeWatchStateRepository(profiles, chosenProfileId, watch)
        profile = ProfileViewModel(viewer, NoopWatchSync)
        val repository = mockk<CatalogRepository>()
        coEvery { repository.refresh() } returns Result.success(sets.size)
        coEvery { repository.sets() } returns sets
        coEvery { repository.titleInfo(any()) } returns null
        coEvery { repository.posterPath(any()) } returns null
        coEvery { repository.search(any()) } answers {
            val query = firstArg<String>()
            sets.filter { it.title.contains(query, ignoreCase = true) }.map { SearchHit(setId = it.setId, matched = "title", excerpt = null) }
        }
        val enrichment = CatalogEnrichmentFetcher(provider, InMemoryTmdbSettings())
        catalog =
            CatalogViewModel(
                repository,
                viewer,
                LibraryUpdateCoordinator(repository, enrichment),
                heldSets = HeldIds(heldIds),
            )
        fetch = FetchViewModel(enrichment)
        search = SearchViewModel(repository)
        // The player a title's Play opens, over an ExoPlayer that decodes
        // nothing — the library's walk only needs it to open and to stop.
        playback = TvPlayerFixture(viewer)
        player = playback.factory.create(PlayerViewModel::class.java)
        // The menu's three screens, as the phone's own library fixture holds
        // them: stand-ins with the facts a screen draws, since what those
        // screens do is each ViewModel's own tests' business.
        every { settings.state } returns MutableStateFlow(SettingsUiState(account = "Ada", library = "Family films"))
        every { settings.completions } returns MutableStateFlow<List<SettingsCompletion>>(emptyList())
        every { system.failure } returns MutableStateFlow(null)
        every { system.state } returns
            MutableStateFlow(
                SystemUiState(
                    origin = "channel",
                    sets = sets.size.toLong(),
                    posters = 0,
                    schema = 4,
                    publishedAt = null,
                    lastRefresh = null,
                    heldBytes = 0,
                    budgetBytes = 1_000_000,
                    volumeLabel = "Internal storage",
                    fellBack = false,
                    fromCacheBytes = 0,
                    fromUpstreamBytes = 0,
                    fetches = 0,
                    failedReads = 0,
                    connected = true,
                    versionName = "test",
                    uptimeSeconds = 0,
                ),
            )
        every { cacheBudget.state } returns
            MutableStateFlow(
                CacheOccupancy(
                    heldBytes = 0,
                    budgetBytes = 1_000_000,
                    volumeLabel = "Internal storage",
                    fellBack = false,
                    capBytes = 1_000_000,
                ),
            )
        every { cacheBudget.failure } returns MutableStateFlow(null)
        every { cacheBudget.volumes } returns
            MutableStateFlow(
                listOf(
                    CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File("internal"), freeBytes = 1_000_000, removable = false),
                    CacheVolume("6BBF-D2D8", "USB drive", File("usb"), freeBytes = 2_000_000, removable = true),
                ),
            )
        every { cacheBudget.chosenVolumeId } returns MutableStateFlow(null)
        every { lanCache.state } returns lanCacheState
        val models =
            mapOf<Class<out ViewModel>, ViewModel>(
                SetupViewModel::class.java to setup,
                LoginViewModel::class.java to login,
                ProfileViewModel::class.java to profile,
                CatalogViewModel::class.java to catalog,
                FetchViewModel::class.java to fetch,
                SearchViewModel::class.java to search,
                PlayerViewModel::class.java to player,
                SettingsViewModel::class.java to settings,
                SystemViewModel::class.java to system,
                CacheBudgetViewModel::class.java to cacheBudget,
                LanCacheViewModel::class.java to lanCache,
            )
        val held =
            ViewModelProvider(
                viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.getValue(modelClass))!!
                },
            )
        models.keys.forEach { held[it] }
    }

    override fun close() {
        viewModelStore.clear()
        playback.close()
    }
}

/** Exactly [held] is held, whatever its size — the cache itself is not what these tests are about. */
private class HeldIds(
    private val held: Set<String>,
) : HeldSetsQuery {
    override suspend fun isHeld(
        setId: String,
        totalBytes: Long,
    ) = setId in held

    override suspend fun heldIds(sets: List<Pair<String, Long>>): Set<String> = sets.map { it.first }.filterTo(HashSet()) { it in held }
}
