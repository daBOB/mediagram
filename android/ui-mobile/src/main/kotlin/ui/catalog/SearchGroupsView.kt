package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.BrowseViewModel
import catalog.Entry
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchGroups
import catalog.SearchRow
import catalog.VisiblePerson
import catalog.extentOf
import catalog.factsLine
import designsystem.Spacing
import model.Progress
import model.WatchSnapshot

private val SEARCH_CARD_WIDTH = 120.dp

/**
 * Search's grouped results — a Compose port of `search-view.js`'s own
 * layout: films and matched shows as posters, episodes and lessons as rows
 * that say where each sits and why it matched, people as round portraits,
 * and collections as destination cards. [filter] narrows the page to one
 * kind without asking the server again, the same job the web's own pills
 * do; the choice itself is the screen's, not [SearchGroups]'s.
 *
 * Every section wraps in a [FlowRow] rather than a nested lazy grid: this
 * whole view already sits inside one scrolling [LazyColumn], and a lazy
 * grid nested inside another lazy container of unbounded height cannot be
 * measured — the same reason every other department page in this phase
 * keeps its own sections to one [LazyVerticalGrid] or one [FlowRow], never
 * both nested.
 */
@Composable
internal fun SearchGroupsView(
    groups: SearchGroups,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    watch: WatchSnapshot,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onPlay: (String) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenFranchise: (Long) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val positions = watch.progress.associateBy { it.setId }
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    val shows = groups.matchedShows.takeIf { filter == SearchFilter.ALL || filter == SearchFilter.SERIES }.orEmpty()
    val films = groups.films.takeIf { filter == SearchFilter.ALL || filter == SearchFilter.MOVIES }.orEmpty()
    val episodes = groups.episodes.takeIf { filter == SearchFilter.ALL || filter == SearchFilter.SERIES }.orEmpty()
    val lessons = groups.lessons.takeIf { filter == SearchFilter.ALL || filter == SearchFilter.TUTORIALS }.orEmpty()
    val people = groups.people.takeIf { filter == SearchFilter.ALL || filter == SearchFilter.PEOPLE }.orEmpty()
    val collections = groups.collections.takeIf { filter == SearchFilter.ALL || filter == SearchFilter.COLLECTIONS }.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        if (groups.filters.size > 1) SearchFilterChips(groups.filters, filter, onFilterChange)
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.medium)) {
            if (films.isNotEmpty()) {
                item(key = "movies") { PosterSection("Movies", posterCards(films, watchedIds, onOpenTitle)) }
            }
            if (shows.isNotEmpty()) {
                item(key = "series") { PosterSection("Series", shows.map { show -> { ShowPoster(show, onOpenCollection) } }) }
            }
            if (episodes.isNotEmpty()) {
                item(key = "episodes") { RowSection("Episodes", episodes, positions, watchedIds, onPlay) }
            }
            if (lessons.isNotEmpty()) {
                item(key = "lessons") { RowSection("Lessons", lessons, positions, watchedIds, onPlay) }
            }
            if (people.isNotEmpty()) {
                item(key = "people") { PeopleSection(people, onOpenPerson) }
            }
            if (collections.isNotEmpty()) {
                item(key = "collections") { CollectionsSection(collections, onOpenFranchise, onOpenList) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterChips(filters: List<Pair<SearchFilter, Int>>, chosen: SearchFilter, onChoose: (SearchFilter) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        for ((kind, count) in filters) {
            val selected = kind == chosen
            Text(
                text = "${filterLabel(kind)} $count",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(role = Role.Button) { onChoose(kind) }
                    .padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
            )
        }
    }
}

private fun filterLabel(filter: SearchFilter): String = when (filter) {
    SearchFilter.ALL -> "All"
    SearchFilter.MOVIES -> "Movies"
    SearchFilter.SERIES -> "Series"
    SearchFilter.TUTORIALS -> "Tutorials"
    SearchFilter.PEOPLE -> "People"
    SearchFilter.COLLECTIONS -> "Collections"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterSection(title: String, cards: List<@Composable () -> Unit>) {
    Column(modifier = Modifier.padding(top = Spacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (card in cards) card()
        }
    }
}

private fun posterCards(rows: List<SearchRow>, watchedIds: Set<String>, onOpenTitle: (String) -> Unit): List<@Composable () -> Unit> =
    rows.map { row ->
        {
            PosterCard(
                posterPath = row.set.posterPath,
                title = row.set.title,
                caption = factsLine(row.set.year, row.set.durationSecs),
                watched = row.set.setId in watchedIds,
                held = row.held,
                modifier = Modifier.width(SEARCH_CARD_WIDTH),
                onClick = { onOpenTitle(row.set.setId) },
            )
        }
    }

@Composable
private fun ShowPoster(show: Entry.Collection, onOpenCollection: (String) -> Unit) {
    PosterCard(
        posterPath = show.posterPath,
        title = show.name,
        caption = extentOf(show),
        modifier = Modifier.width(SEARCH_CARD_WIDTH),
        onClick = { onOpenCollection(show.key) },
    )
}

@Composable
private fun RowSection(title: String, rows: List<SearchRow>, positions: Map<String, Progress>, watchedIds: Set<String>, onPlay: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.medium)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        for (row in rows) {
            SearchResultRow(row, positions[row.set.setId], row.set.setId in watchedIds, onPlay)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleSection(people: List<VisiblePerson>, onOpenPerson: (Long) -> Unit) {
    val browseViewModel: BrowseViewModel = hiltViewModel()
    Column(modifier = Modifier.padding(top = Spacing.medium)) {
        Text("People", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (person in people) {
                // Fetched lazily, at most once per session per person — this
                // phase's own portrait rule, the same [rememberPortrait] a
                // person's own page asks with.
                val portrait = rememberPortrait(person.personId, person.portraitPath, browseViewModel::shouldRequestPortrait, browseViewModel::fetchPortrait)
                Column(
                    modifier = Modifier.clickable(role = Role.Button) { onOpenPerson(person.personId) }.padding(Spacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PersonFace(name = person.name, portraitPath = portrait, size = 64.dp)
                    Text(person.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.extraSmall))
                    Text(
                        countOf(person.titles, "title"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionsSection(collections: List<SearchDestination>, onOpenFranchise: (Long) -> Unit, onOpenList: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.medium)) {
        Text("Collections", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (destination in collections) {
                PosterCard(
                    posterPath = destination.art,
                    title = destination.name,
                    caption = countOf(destination.itemCount, "title"),
                    modifier = Modifier.width(SEARCH_CARD_WIDTH),
                    onClick = {
                        val franchiseId = destination.href.removePrefix("tmdb-").toLongOrNull()
                        if (franchiseId != null) onOpenFranchise(franchiseId) else onOpenList(destination.href)
                    },
                )
            }
        }
    }
}
