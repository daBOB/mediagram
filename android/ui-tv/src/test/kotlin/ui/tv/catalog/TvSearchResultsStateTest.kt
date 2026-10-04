package ui.tv.catalog

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchGroups
import catalog.SearchRow
import catalog.SearchUiState
import catalog.VisiblePerson
import model.Kind
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [sectionStartsOf] and the [RowAsk] wiring it feeds — a result below the
 * fold, in a section after the first, must restore to *that* row, not to
 * whatever a shared composition-order counter last happened to land on
 * (the regression this guards: `var index = 0; val at = index++` inside a
 * lazy item's own content composes again whenever that item scrolls into
 * view or its inputs change, in whatever order that happens to be — never
 * guaranteed to be section order).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h1400dp")
class TvSearchResultsStateTest : TvScreenStateTest() {
    @Test
    fun sectionStartsCountEveryEarlierSectionsOwnEntriesNotJustItsOwnHeading() {
        val sections =
            listOf(
                SearchSection("Films", (0..2).map { SearchEntry.Title(titleRow("f$it")) }),
                SearchSection("Series", listOf(SearchEntry.Title(titleRow("s0")))),
                SearchSection("People", emptyList()),
            )

        assertEquals(listOf(0, 3, 4), sectionStartsOf(sections))
    }

    /** Flat index 3 is "Series"'s own first entry (local index 0) — not "Films"'s fourth, which does not exist. */
    @Test
    fun askingForAFlatIndexInALaterSectionFocusesThatSectionsOwnEntry() {
        val sections =
            listOf(
                SearchSection("Films", (0..2).map { SearchEntry.Title(titleRow("f$it")) }),
                SearchSection("Series", listOf(SearchEntry.Title(titleRow("s0")))),
            )

        showResults(sections, ask = RowAsk(3))

        compose.onNodeWithText("s0").assertIsFocused()
    }

    /** `search-view.js`'s own headings: films under "Movies", as their chip says, and lessons under "Lessons". */
    @Test
    fun filmsAreHeadedMoviesAsTheirChipSaysAndLessonsAreHeadedLessons() {
        val groups =
            SearchGroups(
                films = listOf(titleRow("f")),
                matchedShows = emptyList(),
                episodes = emptyList(),
                animeFilms = emptyList(),
                matchedAnimeShows = emptyList(),
                animeEpisodes = emptyList(),
                documentaries = emptyList(),
                lessons = listOf(titleRow("l")),
                people = emptyList(),
                collections = emptyList(),
                filters = emptyList(),
            )

        assertEquals(listOf("Movies", "Lessons"), sectionsFor(groups, SearchFilter.ALL).map { it.title })
        assertEquals(listOf(labelFor(SearchFilter.MOVIES)), sectionsFor(groups, SearchFilter.MOVIES).map { it.title })
    }

    /** A franchise counts its films and a list its titles, as the web's cards do; a person counts theirs — all spelled up to twenty. */
    @Test
    fun collectionsAndPeopleSayTheirCountsInWords() {
        val sections =
            listOf(
                SearchSection("People", listOf(SearchEntry.Person(VisiblePerson(personId = 1L, name = "Ada Actor", portraitPath = null, titles = 1)))),
                SearchSection(
                    "Collections",
                    listOf(
                        SearchEntry.Destination(SearchDestination(SearchFilter.COLLECTIONS, "Saga", 3, null, "tmdb-9")),
                        SearchEntry.Destination(SearchDestination(SearchFilter.COLLECTIONS, "Sunday", 1, null, "list-a")),
                    ),
                ),
            )

        showResults(sections, ask = null)

        compose.onNodeWithText("one title").assertExists()
        compose.onNodeWithText("Saga · three films").assertExists()
        compose.onNodeWithText("Sunday · one title").assertExists()
    }

    private fun showResults(
        sections: List<SearchSection>,
        ask: RowAsk?,
    ) {
        show {
            TvSearchResults(
                state = SearchUiState.Ready(hits = emptyList()),
                sections = sections,
                filters = emptyList(),
                filter = SearchFilter.ALL,
                onFilterChange = {},
                catalogReady = true,
                watch = WatchSnapshot.Empty,
                ask = ask,
                onAnswered = {},
                onPlay = {},
                onOpenCollection = {},
                onOpenPerson = {},
                onOpenDestination = {},
                shouldRequestPortrait = { false },
                fetchPortrait = { null },
            )
        }
    }

    private fun titleRow(id: String) = SearchRow(set = set(id, Kind.MOVIE, id, addedAt = 0), matched = "title", excerpt = null)
}
