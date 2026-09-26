package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.BrowseViewModel
import catalog.CatalogViewModel
import catalog.Entry
import catalog.allTitles
import catalog.fetchResultMessage
import catalog.franchisePageOf
import catalog.genreIndex
import catalog.homeRowsOf
import catalog.personPageOf
import catalog.seriesResumeFor
import catalog.similarShows
import catalog.similarTo
import model.Person
import system.FetchViewModel
import ui.FrameKind
import ui.LibraryPositions
import ui.catalog.rememberFranchiseOverviews
import ui.catalog.rememberPerson
import ui.catalog.rememberPortrait
import ui.catalog.rememberTitleCredits
import ui.catalog.rememberTitleInfo
import ui.rememberLibraryPositions
import ui.resolve
import ui.tv.catalog.TvCollection
import ui.settings.SettingsOutcomes
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvFranchisePage
import ui.tv.catalog.TvGenresIndex
import ui.tv.catalog.TvLatestPage
import ui.tv.catalog.TvMenuEntryKey
import ui.tv.catalog.TvMoviesPage
import ui.tv.catalog.TvPersonPage
import ui.tv.catalog.TvSearchEntryKey
import ui.tv.catalog.TvSeason
import ui.tv.catalog.TvTitlePage
import ui.tv.profile.TvChosenProfile
import ui.tv.system.TvMenuPage

/**
 * The library on a television — the twin of the phone's `LibraryFlow`: the
 * catalogue, whichever show or course it opened, whichever season of that,
 * whichever title that described, whichever set that played, whichever
 * hand-built list the Collections tab opened, search, whichever genre a
 * title's link opened, a person's own page, a franchise's own page, the
 * Genres index, the Latest page, the Movies department's own full wall, and
 * the menu with the screens it opens. Where the viewer is, and what Back
 * uncovers, is the shared [LibraryPositions] stack, asked the same way the
 * phone asks it, so the two surfaces cannot disagree about where Back goes.
 *
 * What only a television needs is [TvRestoreKeys]: each screen remembers
 * what it opened, so Back puts the remote on that plate or row again.
 * Leaving a screen goes through one `leave` that pops its frame and
 * forgets what it remembered.
 *
 * The catalogue's own saved state — which masthead tab was chosen, how far
 * its wall had scrolled — is held apart while a title or a show covers it,
 * so Back finds the tab it left rather than Home.
 */
@Composable
internal fun TvLibrary(
    profile: TvChosenProfile,
    onStartOver: () -> Unit = {},
    onSignedOut: () -> Unit = {},
) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    val fetchViewModel: FetchViewModel = hiltViewModel()
    val fetchState by fetchViewModel.state.collectAsStateWithLifecycle()
    val browse: BrowseViewModel = hiltViewModel()
    val kidsProfile by catalogViewModel.kidsProfile.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions()
    val restore = rememberTvRestoreKeys()
    val saved = rememberSaveableStateHolder()
    // The menu page stands over the shelves rather than on the positions'
    // stack: it is where System, Settings and the key screen are chosen,
    // and Back from any of them comes back to it before the masthead.
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    SettingsOutcomes(onLibraryChanged = catalogViewModel::reload, onSignedOut = onSignedOut)

    val resolved = at.resolve(catalogState)
    val watch = resolved.watch
    val ready = catalogState as? CatalogUiState.Ready
    val shelves = ready?.shelves.orEmpty()
    val heldIds = ready?.heldIds.orEmpty()
    // Built once per shelves change, from shelves rather than a per-frame
    // fetch: every screen below that needs "every film"/"every show" reads
    // this, the same pool `feature:catalog`'s own pure rules already expect.
    val allFilms = remember(shelves) {
        shelves.firstOrNull { it.title == "Movies" }?.entries.orEmpty().filterIsInstance<Entry.Film>().map { it.set }
    }
    val allShows = remember(shelves) {
        shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Collection>().toList()
    }
    val top = at.top
    val here = at.depth
    val leave = {
        restore.forget(here)
        at.pop()
    }
    val menu = tvMenuActions(at, restore, here, catalogState, catalogViewModel, fetchState, { menuOpen = false }, onStartOver)

    when (top) {
        FrameKind.PLAYER -> TvPlayerBranch(at, catalogState, leave)

        FrameKind.MENU -> TvMenuScreenBranch(at, fetchState, fetchViewModel, leave)

        FrameKind.SEARCH -> TvSearchBranch(at, catalogState, watch, restore, browse, leave)

        FrameKind.GENRE -> TvGenreBranch(at, catalogState, watch, restore, leave)

        FrameKind.TITLE ->
            TvResolvedBranch(resolved.title, catalogState, leave) { title ->
                val credits = rememberTitleCredits(title.posterKey, catalogViewModel::titleCredits)
                val similar = remember(title, allFilms) { similarTo(title, allFilms) { false } }
                TvTitlePage(
                    set = title,
                    info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
                    progress = watch.progress.find { it.setId == title.setId },
                    // Coming back from the player lands on Play, not on a
                    // genre whose page was visited before it.
                    onPlay = {
                        restore.forget(here)
                        at.openPlayer(title.setId)
                    },
                    onOpenGenre = { name ->
                        restore.opened(here, name)
                        at.openGenre(name)
                    },
                    restoreKey = restore.of(here),
                    credits = credits,
                    onOpenPerson = { personId ->
                        restore.opened(here, personId.toString())
                        at.openPerson(personId.toString())
                    },
                    shouldRequestPortrait = browse::shouldRequestPortrait,
                    fetchPortrait = browse::fetchPortrait,
                    similar = similar,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    editorsChoice = watch.editorsChoice,
                    onToggleEditorsChoice =
                        if (kidsProfile) {
                            null
                        } else {
                            { catalogViewModel.setEditorsChoice(title.setId, watch.editorsChoice != title.setId) }
                        },
                )
            }

        FrameKind.SEASON ->
            TvResolvedBranch(resolved.season, catalogState, leave) { season ->
                TvSeason(
                    division = season,
                    watch = watch,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    restoreKey = restore.of(here),
                    heldIds = heldIds,
                )
            }

        FrameKind.COLLECTION ->
            TvResolvedBranch(resolved.collection, catalogState, leave) { collection ->
                val credits = rememberTitleCredits(collection.posterKey, catalogViewModel::titleCredits)
                val similar = remember(collection, allShows) { similarShows(collection, allShows) { false } }
                val resume = remember(collection, watch) { seriesResumeFor(collection, watch) }
                TvCollection(
                    collection = collection,
                    info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
                    watch = watch,
                    posterPath = catalogViewModel::posterPath,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    onOpenSeason = { division ->
                        restore.opened(here, division.title)
                        at.openSeason(division.title)
                    },
                    onOpenGenre = { name ->
                        restore.opened(here, name)
                        at.openGenre(name)
                    },
                    restoreKey = restore.of(here),
                    heldIds = heldIds,
                    credits = credits,
                    onOpenPerson = { personId ->
                        restore.opened(here, personId.toString())
                        at.openPerson(personId.toString())
                    },
                    shouldRequestPortrait = browse::shouldRequestPortrait,
                    fetchPortrait = browse::fetchPortrait,
                    similar = similar,
                    onOpenCollection = { key ->
                        restore.opened(here, key)
                        at.openCollection(key)
                    },
                    resume = resume,
                    onResume = { setId ->
                        restore.forget(here)
                        at.openPlayer(setId)
                    },
                )
            }

        FrameKind.LIST -> TvListBranch(at, resolved.list, catalogState, catalogViewModel, restore, leave)

        FrameKind.PERSON -> {
            val personId = at.personId?.toLongOrNull()
            if (personId == null) {
                LaunchedEffect(Unit) { leave() }
            } else {
                BackHandler(onBack = leave)
                val person: Person? = rememberPerson(personId, browse::person)
                val page = remember(person, shelves) { personPageOf(person, shelves) }
                val portrait = rememberPortrait(personId, person?.portraitPath, browse::shouldRequestPortrait, browse::fetchPortrait)
                TvPersonPage(
                    page = page,
                    portrait = portrait,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    onOpenCollection = { key ->
                        restore.opened(here, key)
                        at.openCollection(key)
                    },
                    restoreKey = restore.of(here),
                )
            }
        }

        FrameKind.FRANCHISE -> {
            val franchiseId = at.franchiseId?.toLongOrNull()
            if (franchiseId == null) {
                LaunchedEffect(Unit) { leave() }
            } else {
                BackHandler(onBack = leave)
                val overviews = rememberFranchiseOverviews(browse::franchiseOverviews)
                val page = remember(franchiseId, allFilms, overviews) { franchisePageOf(franchiseId, allFilms, overviews) }
                if (page == null) {
                    LaunchedEffect(Unit) { leave() }
                } else {
                    TvFranchisePage(
                        page = page,
                        watch = watch,
                        onOpenTitle = { setId ->
                            restore.opened(here, setId)
                            at.openTitle(setId)
                        },
                        restoreKey = restore.of(here),
                        heldIds = heldIds,
                    )
                }
            }
        }

        FrameKind.GENRES -> {
            BackHandler(onBack = leave)
            val genres = remember(shelves) { genreIndex(allTitles(shelves)) }
            TvGenresIndex(
                genres = genres,
                onOpenGenre = { name ->
                    restore.opened(here, name)
                    at.openGenre(name)
                },
                restoreKey = restore.of(here),
            )
        }

        FrameKind.LATEST -> {
            BackHandler(onBack = leave)
            val rows =
                remember(shelves, watch, heldIds) {
                    homeRowsOf(shelves, watch, heldIds, limit = 48)
                        .filter { it.title in setOf("Latest films", "Latest series", "Latest courses") }
                }
            TvLatestPage(
                rows = rows,
                watch = watch,
                onOpenTitle = { setId ->
                    restore.opened(here, setId)
                    at.openTitle(setId)
                },
                onOpenCollection = { key ->
                    restore.opened(here, key)
                    at.openCollection(key)
                },
                restoreKey = restore.of(here),
            )
        }

        FrameKind.MOVIES_PAGE -> {
            BackHandler(onBack = leave)
            TvMoviesPage(
                films = allFilms,
                watch = watch,
                onOpenTitle = { setId ->
                    restore.opened(here, setId)
                    at.openTitle(setId)
                },
                restoreKey = restore.of(here),
                heldIds = heldIds,
            )
        }

        // Nothing open, the menu page chosen from the masthead: Back from
        // it puts the remote back on the masthead's Menu.
        null if menuOpen ->
            TvMenuPage(
                menu = menu,
                restoreKey = restore.of(here),
                onMyList = {
                    menuOpen = false
                    restore.opened(here, TvWatchlistEntryKey)
                },
                onContinueWatching = {
                    menuOpen = false
                    restore.opened(here, TvContinueEntryKey)
                },
                onLatest = {
                    menuOpen = false
                    restore.forget(here)
                    at.openLatest()
                },
                onGenres = {
                    menuOpen = false
                    restore.forget(here)
                    at.openGenresIndex()
                },
            ) {
                menuOpen = false
                restore.opened(here, TvMenuEntryKey)
            }

        // Nothing open: the shelves.
        null -> {
            saved.SaveableStateProvider(CatalogStateKey) {
                TvCatalogRoot(
                    state = catalogState,
                    profile = profile,
                    fetching = fetchState.running,
                    restoreKey = restore.of(here),
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    onOpenCollection = { key ->
                        restore.opened(here, key)
                        at.openCollection(key)
                    },
                    onOpenList = { id ->
                        restore.opened(here, id)
                        at.openList(id)
                    },
                    onCreateList = catalogViewModel::createList,
                    onTabChanged = { restore.forget(here) },
                    onOpenSearch = {
                        restore.opened(here, TvSearchEntryKey)
                        at.openSearch()
                    },
                    onOpenMenu = {
                        restore.forget(here)
                        menuOpen = true
                    },
                    onEntryRestored = { restore.forget(here) },
                    onFinish = catalogViewModel::markFinished,
                    onOpenGenre = { name ->
                        restore.opened(here, name)
                        at.openGenre(name)
                    },
                    onOpenFranchise = { id ->
                        restore.opened(here, id.toString())
                        at.openFranchise(id.toString())
                    },
                    onOpenMoviesPage = {
                        restore.opened(here, TvMoviesPageEntryKey)
                        at.openMoviesPage()
                    },
                )
            }
        }
    }

    // Every branch but the player, for the phone's reason: a result held
    // until it is dismissed is still there when the film is left, which is
    // when there is somebody to read it.
    if (top != FrameKind.PLAYER) {
        TvFetchResultDialog(
            message = fetchResultMessage(fetchState.report, fetchState.error),
            onDismiss = fetchViewModel::dismissResult,
        )
    }
}

/** Where the catalogue's own saved state — its tab, its wall's scroll — is held while something covers it. */
private const val CatalogStateKey = "catalog"

/** The catalogue's restore key for "My List was opened from the overflow menu" — [TvMasthead]'s own sentinels' counterpart. */
internal const val TvWatchlistEntryKey = "menu:mylist"

/** The catalogue's restore key for "Continue watching was opened from the overflow menu". */
internal const val TvContinueEntryKey = "menu:continue"

/** The catalogue's restore key for ""All N films" was opened from the Movies department" — no plate of its own to remember instead. */
internal const val TvMoviesPageEntryKey = "movies:all"
