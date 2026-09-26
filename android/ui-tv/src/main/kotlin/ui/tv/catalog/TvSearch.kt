package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.CatalogUiState
import catalog.Entry
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchUiState
import catalog.SearchViewModel
import catalog.franchisesIn
import catalog.searchGroupsOf
import designsystem.Overscan
import model.WatchSnapshot
import ui.catalog.searchVisitState
import ui.tv.TvTextField

/** The search field's own tag — it carries no text of its own to be found by until something is typed. */
internal const val TvSearchFieldTag = "tv-search-field"

/**
 * Wires [SearchViewModel] to [TvSearchScreen], as the phone's `SearchBranch`
 * wires it to its own — when an answer belongs to this visit is
 * [searchVisitState]'s, the one rule both surfaces keep.
 */
@Composable
internal fun TvSearch(
    query: String,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restoreKey: String?,
    onQueryChange: (String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit = {},
    onOpenPerson: (personId: Long) -> Unit = {},
    onOpenFranchise: (id: Long) -> Unit = {},
    onOpenList: (id: String) -> Unit = {},
    shouldRequestPortrait: (Long) -> Boolean = { false },
    fetchPortrait: suspend (Long) -> String? = { null },
) {
    val viewModel: SearchViewModel = hiltViewModel()
    TvSearchScreen(
        query = query,
        state = searchVisitState(viewModel, query),
        catalogState = catalogState,
        watch = watch,
        restoreKey = restoreKey,
        onQueryChange = { text ->
            onQueryChange(text)
            viewModel.setQuery(text)
        },
        onPlay = onPlay,
        onOpenCollection = onOpenCollection,
        onOpenPerson = onOpenPerson,
        onOpenFranchise = onOpenFranchise,
        onOpenList = onOpenList,
        shouldRequestPortrait = shouldRequestPortrait,
        fetchPortrait = fetchPortrait,
    )
}

/**
 * Search on a television — the phone's `SearchScreen` and the web's
 * `search-view.js`: a field, typed through the system keyboard, over the
 * query grouped the way [catalog.searchGroupsOf] groups it — films, matched
 * shows, episodes, lessons, people (only those this profile can see) and
 * collections — each its own section, with a filter chip row over them once
 * there is more than one kind to narrow to.
 *
 * The remote lands in the field when nothing has been typed, so the
 * keyboard is up the moment search opens. Coming back with a query —
 * from the title a row played — it waits for the answer and lands on that
 * row ([restoreKey]), or the first, rather than reopening the keyboard over
 * an answer already found. The keyboard's Search key hands the remote to
 * the first row; Up from the top row goes back to the field.
 *
 * [query] seeds the field from the saved position; every keystroke after
 * that is [onQueryChange]'s to save.
 */
@Composable
internal fun TvSearchScreen(
    query: String,
    state: SearchUiState,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restoreKey: String?,
    onQueryChange: (String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit = {},
    onOpenPerson: (personId: Long) -> Unit = {},
    onOpenFranchise: (id: Long) -> Unit = {},
    onOpenList: (id: String) -> Unit = {},
    shouldRequestPortrait: (Long) -> Boolean = { false },
    fetchPortrait: suspend (Long) -> String? = { null },
) {
    var text by rememberSaveable { mutableStateOf(query) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val catalogReady = catalogState is CatalogUiState.Ready
    val movies =
        remember(catalogState) {
            (catalogState as? CatalogUiState.Ready)?.shelves?.firstOrNull { it.title == "Movies" }
                ?.entries.orEmpty().filterIsInstance<Entry.Film>().map { it.set }
                ?: emptyList()
        }
    val franchises = remember(movies) { franchisesIn(movies) }
    val groups =
        remember(state, catalogState, text, franchises, watch.collections) {
            if (state is SearchUiState.Ready && catalogReady) {
                searchGroupsOf(text, catalogState, state.hits, state.people, franchises, watch.collections)
            } else {
                null
            }
        }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    // A narrower query can drop the kind a viewer had chosen — back to All
    // rather than a filter chip that no longer exists to switch away from.
    LaunchedEffect(groups?.filters) {
        if (groups != null && filter != SearchFilter.ALL && groups.filters.none { it.first == filter }) filter = SearchFilter.ALL
    }
    val sections = remember(groups, filter) { groups?.let { sectionsFor(it, filter) }.orEmpty() }
    val entries = remember(sections) { sections.flatMap { it.entries } }
    var ask by remember { mutableStateOf<RowAsk?>(null) }
    // Whether this visit has put the remote somewhere yet — at once for an
    // empty field, and once the answer is in when it arrives with a query.
    var arrived by remember { mutableStateOf(query.isBlank()) }
    val answered = catalogReady && (state is SearchUiState.Ready || state is SearchUiState.Failed)

    LaunchedEffect(Unit) { if (query.isBlank()) field.requestFocus() }
    LaunchedEffect(answered) {
        if (answered && !arrived) {
            arrived = true
            if (entries.isEmpty()) {
                field.requestFocus()
            } else {
                ask = RowAsk(entries.indexOfFirst { keyOf(it) == restoreKey }.coerceAtLeast(0))
            }
        }
    }

    val onOpenDestination: (SearchDestination) -> Unit = { destination ->
        val franchiseId = destination.href.removePrefix("tmdb-").toLongOrNull()
        if (destination.href.startsWith("tmdb-") && franchiseId != null) onOpenFranchise(franchiseId) else onOpenList(destination.href)
    }

    Column(modifier = Modifier.fillMaxSize().padding(start = Overscan.horizontal, end = Overscan.horizontal, top = Overscan.vertical)) {
        TvTextField(
            value = text,
            onValue = { new ->
                text = new
                onQueryChange(new)
            },
            onAction = {
                keyboard?.hide()
                if (entries.isNotEmpty()) ask = RowAsk(0)
            },
            focusRequester = field,
            fieldModifier = Modifier.testTag(TvSearchFieldTag),
            placeholder = "Search titles and summaries",
            imeAction = ImeAction.Search,
        )
        TvSearchResults(
            state = state,
            sections = sections,
            filters = groups?.filters.orEmpty(),
            filter = filter,
            onFilterChange = { filter = it },
            catalogReady = catalogReady,
            watch = watch,
            ask = ask,
            onAnswered = { ask = null },
            onPlay = onPlay,
            onOpenCollection = onOpenCollection,
            onOpenPerson = onOpenPerson,
            onOpenDestination = onOpenDestination,
            shouldRequestPortrait = shouldRequestPortrait,
            fetchPortrait = fetchPortrait,
        )
    }
}

/**
 * One request for an entry to take the remote. A fresh object each time, so
 * asking for the same entry twice — Search pressed again on the same answer —
 * is still a new request. Dropped the moment it is answered: the entries
 * leave composition whenever an answer has none, and a request still
 * standing when they came back would pull the remote out of the field
 * mid-typing.
 */
internal class RowAsk(val index: Int)
