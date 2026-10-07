package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import catalog.SearchFilter
import catalog.SearchGroups
import catalog.SearchRow
import catalog.SearchUiState
import catalog.VisiblePerson
import data.PortraitRequestLog
import model.Kind
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Search's people as the web's `search-view.js` draws them — `personCard`s
 * side by side in a grid, a round portrait over the name and the count —
 * rather than one row each, and walked by the remote like any poster line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h1400dp")
class TvSearchPeopleCardsStateTest : TvScreenStateTest() {
    @Test
    fun peopleAreLaidOutAsCardsAPosterLineWide() {
        val groups =
            SearchGroups(
                films = emptyList(),
                matchedShows = emptyList(),
                episodes = emptyList(),
                animeFilms = emptyList(),
                matchedAnimeShows = emptyList(),
                animeEpisodes = emptyList(),
                documentaries = emptyList(),
                lessons = emptyList(),
                people = (0..6).map { person(it.toLong()) },
                collections = emptyList(),
                filters = emptyList(),
            )

        val sections = sectionsFor(groups, SearchFilter.PEOPLE)

        assertEquals(listOf(SearchLayout.PEOPLE), sections.map { it.layout })
        assertEquals(
            listOf(SearchLine.Heading("People"), SearchLine.Entries(SearchLayout.PEOPLE, 0..5), SearchLine.Entries(SearchLayout.PEOPLE, 6..6)),
            searchLinesOf(sections),
        )
    }

    /** Side by side, each portrait as wide as it is tall, the name and the spelled count under it. */
    @Test
    fun aPersonIsARoundPortraitWithTheNameAndCountUnderIt() {
        showResults(peopleSection(0L, 1L))

        val first = compose.onNode(hasText("Person 0") and hasClickAction()).getUnclippedBoundsInRoot()
        val second = compose.onNode(hasText("Person 1") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(second.left >= first.right, "two people should stand side by side, not one row under the other")
        assertEquals(first.top, second.top)
        // Six to a line, as posters are: a card never spans the width the way a row did.
        assertTrue(first.width < 200.dp, "a card should be a poster's width, got ${first.width}")
        val name = compose.onNode(hasText("Person 0"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val count = compose.onAllNodes(hasText("two titles"), useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue(name.top >= first.top + first.width * 0.9f, "the name should sit under the round portrait")
        assertTrue(count.top >= name.bottom, "the count should sit under the name")
    }

    /** Down from a poster line lands on a person; Right walks the people; OK opens that person; Up goes back to the posters. */
    @Test
    fun theRemoteWalksIntoThePeopleAndOpensOne() {
        val opened = mutableListOf<Long>()
        showResults(
            listOf(SearchSection("Movies", listOf(SearchEntry.Title(filmRow("f0"))), SearchLayout.POSTERS)) + peopleSection(7L, 8L),
            onOpenPerson = { opened += it },
        )

        compose.onNode(hasText("f0") and hasClickAction()).assertIsFocused()
        press(Key.DirectionDown)
        card("Person 7").assertIsFocused()
        press(Key.DirectionRight)
        card("Person 8").assertIsFocused()
        press(Key.Enter)
        assertEquals(listOf(8L), opened)
        press(Key.DirectionUp)
        compose.onNode(hasText("f0") and hasClickAction()).assertIsFocused()
    }

    private fun card(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun person(id: Long) = VisiblePerson(personId = id, name = "Person $id", portraitPath = null, titles = 2)

    private fun peopleSection(vararg ids: Long) = listOf(SearchSection("People", ids.map { SearchEntry.Person(person(it)) }, SearchLayout.PEOPLE))

    private fun filmRow(id: String) = SearchRow(set = set(id, Kind.MOVIE, id, addedAt = 0), matched = "title", excerpt = null)

    private fun showResults(
        sections: List<SearchSection>,
        onOpenPerson: (Long) -> Unit = {},
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
                ask = RowAsk(0),
                onAnswered = {},
                onPlay = {},
                onOpenTitle = {},
                onOpenCollection = {},
                onOpenPerson = onOpenPerson,
                onOpenDestination = {},
                portraits = PortraitRequestLog(),
                fetchPortrait = { null },
            )
        }
    }
}
