package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.CollectionKind
import catalog.Entry
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
 * [searchLinesOf] and the [RowAsk] wiring it feeds — a result below the
 * fold, in a section after the first, must restore to *that* entry, not to
 * whatever a shared composition-order counter last happened to land on
 * (the regression this guards: `var index = 0; val at = index++` inside a
 * lazy item's own content composes again whenever that item scrolls into
 * view or its inputs change, in whatever order that happens to be — never
 * guaranteed to be section order) — and each kind drawn the way the web
 * draws it: films and shows as posters, collections as cards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h1400dp")
class TvSearchResultsStateTest : TvScreenStateTest() {
    /** Posters six to a line, rows one, cards three; every line names the flat indices of the entries it holds, counting every earlier section's. */
    @Test
    fun linesChunkEachSectionByItsLayoutAndCountEveryEarlierSectionsEntries() {
        val sections =
            listOf(
                SearchSection("Movies", (0..7).map { SearchEntry.Title(titleRow("f$it")) }, SearchLayout.POSTERS),
                SearchSection("Episodes", listOf(SearchEntry.Title(titleRow("e0"))), SearchLayout.ROWS),
                SearchSection("Collections", (0..3).map { destination("d$it", "tmdb-$it") }, SearchLayout.CARDS),
            )

        val lines = searchLinesOf(sections)

        assertEquals(
            listOf(
                SearchLine.Heading("Movies"),
                SearchLine.Entries(SearchLayout.POSTERS, 0..5),
                SearchLine.Entries(SearchLayout.POSTERS, 6..7),
                SearchLine.Heading("Episodes"),
                SearchLine.Entries(SearchLayout.ROWS, 8..8),
                SearchLine.Heading("Collections"),
                SearchLine.Entries(SearchLayout.CARDS, 9..11),
                SearchLine.Entries(SearchLayout.CARDS, 12..12),
            ),
            lines,
        )
        assertEquals(2, lineOf(lines, 7))
        assertEquals(4, lineOf(lines, 8))
        assertEquals(-1, lineOf(lines, 13))
    }

    /** Flat index 7 is the second poster line's own second film; flat index 8 is "Episodes"'s own first row — not a seventh poster, which does not exist. */
    @Test
    fun askingForAFlatIndexFocusesThatEntryOnItsOwnLine() {
        val sections =
            listOf(
                SearchSection("Movies", (0..7).map { SearchEntry.Title(titleRow("f$it")) }, SearchLayout.POSTERS),
                SearchSection("Episodes", listOf(SearchEntry.Title(titleRow("e0")))),
            )

        showResults(sections, ask = RowAsk(7))
        entry("f7").assertIsFocused()
        close()

        showResults(sections, ask = RowAsk(8))
        entry("e0").assertIsFocused()
    }

    /** A film's poster opens its page, a show's poster its show, a card its franchise or list; an episode's row plays. */
    @Test
    fun eachEntryOpensWhatItStandsFor() {
        val opened = mutableListOf<String>()
        val sections =
            listOf(
                SearchSection("Movies", listOf(SearchEntry.Title(titleRow("f0"))), SearchLayout.POSTERS),
                SearchSection("Series", listOf(SearchEntry.Show(show("A Show"))), SearchLayout.POSTERS),
                SearchSection("Episodes", listOf(SearchEntry.Title(row("e0", Kind.EPISODE)))),
                SearchSection("Collections", listOf(destination("Saga", "tmdb-9")), SearchLayout.CARDS),
            )

        showResults(
            sections,
            ask = null,
            onPlay = { opened += "play:$it" },
            onOpenTitle = { opened += "title:$it" },
            onOpenCollection = { opened += "show:$it" },
            onOpenDestination = { opened += "dest:${it.href}" },
        )
        for (name in listOf("f0", "A Show", "e0", "SAGA")) {
            entry(name).performSemanticsAction(SemanticsActions.OnClick)
        }

        assertEquals(listOf("title:f0", "show:SHOW/A Show", "play:e0", "dest:tmdb-9"), opened)
    }

    /**
     * Right walks a line of posters; Down keeps the column onto a short
     * line, then lands on the card under it; Up from a card past the short
     * line's end comes back to that line's last poster.
     */
    @Test
    fun theRemoteWalksPosterLinesByColumnIntoTheNextSection() {
        val sections =
            listOf(
                SearchSection("Movies", (0..7).map { SearchEntry.Title(titleRow("f$it")) }, SearchLayout.POSTERS),
                SearchSection("Collections", (0..2).map { destination("d$it", "tmdb-$it") }, SearchLayout.CARDS),
            )

        showResults(sections, ask = RowAsk(0))
        press(Key.DirectionRight)
        entry("f1").assertIsFocused()
        press(Key.DirectionDown)
        entry("f7").assertIsFocused()
        press(Key.DirectionDown)
        entry("D0").assertIsFocused()
        press(Key.DirectionRight)
        entry("D1").assertIsFocused()
        press(Key.DirectionUp)
        entry("f7").assertIsFocused()
    }

    /** `search-view.js`'s own headings: films under "Movies", as their chip says, and lessons under "Lessons" — films as posters, lessons as rows. */
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
        assertEquals(listOf(SearchLayout.POSTERS, SearchLayout.ROWS), sectionsFor(groups, SearchFilter.ALL).map { it.layout })
        assertEquals(listOf(labelFor(SearchFilter.MOVIES)), sectionsFor(groups, SearchFilter.MOVIES).map { it.title })
    }

    /** A franchise counts its films and a list its titles on the cards Collections draws, names in capitals; a person counts theirs — all spelled up to twenty. */
    @Test
    fun collectionsAreCardsAndPeopleSayTheirCountsInWords() {
        val sections =
            listOf(
                SearchSection("People", listOf(SearchEntry.Person(VisiblePerson(personId = 1L, name = "Ada Actor", portraitPath = null, titles = 2)))),
                SearchSection(
                    "Collections",
                    listOf(
                        SearchEntry.Destination(SearchDestination(SearchFilter.COLLECTIONS, "Saga", 3, null, "tmdb-9", franchiseId = 9)),
                        SearchEntry.Destination(SearchDestination(SearchFilter.COLLECTIONS, "Sunday", 1, null, "list-a")),
                    ),
                    SearchLayout.CARDS,
                ),
            )

        showResults(sections, ask = null)

        compose.onNodeWithText("two titles").assertExists()
        compose.onNodeWithText("SAGA").assertExists()
        compose.onNodeWithText("three films").assertExists()
        compose.onNodeWithText("SUNDAY").assertExists()
        compose.onNodeWithText("one title").assertExists()
        compose.onNodeWithText("Saga · three films").assertDoesNotExist()
    }

    private fun showResults(
        sections: List<SearchSection>,
        ask: RowAsk?,
        onPlay: (String) -> Unit = {},
        onOpenTitle: (String) -> Unit = {},
        onOpenCollection: (String) -> Unit = {},
        onOpenDestination: (SearchDestination) -> Unit = {},
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
                onPlay = onPlay,
                onOpenTitle = onOpenTitle,
                onOpenCollection = onOpenCollection,
                onOpenPerson = {},
                onOpenDestination = onOpenDestination,
                shouldRequestPortrait = { false },
                fetchPortrait = { null },
            )
        }
    }

    private fun entry(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun titleRow(id: String) = row(id, Kind.MOVIE)

    private fun row(
        id: String,
        kind: Kind,
    ) = SearchRow(set = set(id, kind, id, addedAt = 0), matched = "title", excerpt = null)

    private fun destination(
        name: String,
        href: String,
    ) = SearchEntry.Destination(SearchDestination(SearchFilter.COLLECTIONS, name, 2, null, href))

    private fun show(name: String) =
        Entry.Collection(
            key = "SHOW/$name",
            kind = CollectionKind.SHOW,
            name = name,
            posterPath = null,
            posterKey = null,
            count = 1,
            chapters = 1,
            divisions = emptyList(),
        )
}
