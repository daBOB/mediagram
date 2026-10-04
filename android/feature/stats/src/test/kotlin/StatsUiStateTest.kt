package stats

import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StatsUiStateTest {
    private fun page(summary: StatsSummary) = statsUiStateOf(StatsRead.Done(summary, Now), Catalogue)

    @Test
    fun theHistoryDecidesWhetherThePageIsEmpty() {
        assertEquals(StatsUiState.Empty, page(summary()))
        assertEquals(StatsUiState.Empty, statsUiStateOf(StatsRead.Done(summary(), Now), sets = null), "nothing to name, so nothing waits for the catalogue")
    }

    @Test
    fun theReadIsLoadingUntilTheCatalogueIsAndAFailureSaysWhy() {
        val watched = StatsRead.Done(summary(all = 60.0, history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 26, 21, 0), 60.0))), Now)
        assertEquals(StatsUiState.Loading, statsUiStateOf(StatsRead.Loading, Catalogue))
        assertEquals(StatsUiState.Loading, statsUiStateOf(watched, sets = null), "never call every title gone while the catalogue loads")
        assertEquals(StatsUiState.Failed("Could not read your stats: database is locked"), statsUiStateOf(StatsRead.Failed("database is locked"), Catalogue))
        assertEquals(StatsUiState.Failed("Could not read your stats."), statsUiStateOf(StatsRead.Failed(null), Catalogue))
    }

    @Test
    fun aFinishFromBeforeStatsExistedHasNoDuration() {
        val state = page(summary(history = listOf(entry(HistoryKind.FINISHED, "f1", ms(2026, 9, 25, 20, 5), 0.0))))
        assertEquals(listOf("Finished · Der Pate · Fri 20:05"), assertIs<StatsUiState.Ready>(state).history.map { it.text })
    }

    @Test
    fun theTotalsAreThisWeekThisMonthAndAllTime() {
        val state =
            page(
                summary(
                    week = 0.0,
                    month = 3 * 3_600 + 12 * 60.0,
                    all = 12 * 3_600.0,
                    history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 1, 20, 0), 12 * 3_600.0)),
                ),
            )
        assertEquals(
            listOf("This week" to "0 min", "This month" to "3 h 12 min", "All time" to "12 h"),
            assertIs<StatsUiState.Ready>(state).totals,
        )
        assertNull(assertIs<StatsUiState.Ready>(state).countingSince, "a minute counted, so nothing to explain under the totals")
    }

    @Test
    fun whileNothingIsCountedALineUnderTheTotalsSaysSinceWhen() {
        val history =
            listOf(
                entry(HistoryKind.STARTED, "f1", ms(2026, 9, 26, 21, 0), 40.0),
                entry(HistoryKind.FINISHED, "e1", ms(2026, 3, 1, 20, 0), 0.0),
                entry(HistoryKind.STARTED, "l3", ms(2026, 9, 24, 10, 0), 0.0),
            )
        val state = assertIs<StatsUiState.Ready>(page(summary(week = 40.0, month = 40.0, all = 40.0, history = history)))
        assertEquals(listOf("This week" to "0 min", "This month" to "0 min", "All time" to "0 min"), state.totals)
        assertEquals("Counting since 24 Sep", state.countingSince)
    }

    @Test
    fun eachLineSaysWhatHappenedToWhichTitleWhenAndForHowLongInTheCoresOrder() {
        val history =
            listOf(
                entry(HistoryKind.AGAIN, "f1", ms(2026, 9, 26, 21, 14), 42 * 60.0),
                entry(HistoryKind.FINISHED, "e1", ms(2026, 9, 25, 20, 5), 3 * 3_600 + 12 * 60.0),
                entry(HistoryKind.STARTED, "l3", ms(2026, 9, 19, 10, 0), 59.0),
                entry(HistoryKind.STARTED, "gone", ms(2025, 9, 21, 18, 30), 600.0),
            )
        val state = assertIs<StatsUiState.Ready>(page(summary(all = 1.0, history = history)))
        assertEquals(
            listOf(
                "Watched again · Der Pate · today 21:14 · 42 min",
                "Finished · Crime 101 S1E4 · Fri 20:05 · 3 h 12 min",
                "Started · Geldhochschule 3 · 19 Sep · 0 min",
                "Started · No longer in the library · 21 Sep 2025 · 10 min",
            ),
            state.history.map { it.text },
        )
        assertEquals(state.history.size, state.history.map { it.key }.toSet().size, "a lazy list needs every key distinct")
    }

    @Test
    fun eachBarIsItsDaysShareOfTheBusiestOldestFirst() {
        val history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 25, 20, 0), 5_400.0))
        val state = assertIs<StatsUiState.Ready>(page(summary(all = 5_400.0, days = last30(today = 3_600.0, yesterday = 1_800.0), history = history)))
        assertEquals(30, state.bars.size)
        assertEquals(listOf(0f, 0.5f, 1f), listOf(state.bars[0], state.bars[28], state.bars[29]).map { it.fraction })
        assertEquals("S", state.bars.last().initial, "today, a Saturday, is the rightmost bar")
        assertEquals("F", state.bars[28].initial)
        assertEquals("26 Sep · 1 h", state.bars.last().description)
        assertEquals("28 Aug · 0 min", state.bars.first().description)
    }

    @Test
    fun aSetIsNamedTheWayContinueWatchingNamesIt() {
        assertEquals("Der Pate", statsTitle(Catalogue.getValue("f1")))
        assertEquals("Crime 101 S1E4", statsTitle(Catalogue.getValue("e1")))
        assertEquals("Geldhochschule 3", statsTitle(Catalogue.getValue("l3")))
        assertEquals("No longer in the library", statsTitle(null), "gone from the library, or not on this profile's shelves: not named")
    }
}
