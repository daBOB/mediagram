package ui.tv.catalog

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import catalog.SearchFilter
import catalog.SearchRow
import catalog.SearchUiState
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

        show {
            TvSearchResults(
                state = SearchUiState.Ready(hits = emptyList()),
                sections = sections,
                filters = emptyList(),
                filter = SearchFilter.ALL,
                onFilterChange = {},
                catalogReady = true,
                watch = WatchSnapshot.Empty,
                ask = RowAsk(3),
                onAnswered = {},
                onPlay = {},
                onOpenCollection = {},
                onOpenPerson = {},
                onOpenDestination = {},
                shouldRequestPortrait = { false },
                fetchPortrait = { null },
            )
        }

        compose.onNodeWithText("s0").assertIsFocused()
    }

    private fun titleRow(id: String) = SearchRow(set = set(id, Kind.MOVIE, id, addedAt = 0), matched = "title", excerpt = null)
}
