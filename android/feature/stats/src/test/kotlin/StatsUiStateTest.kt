package stats

import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StatsUiStateTest {
    private fun page(summary: StatsSummary) = statsUiStateOf(StatsRead.Done(summary, Now))

    @Test
    fun theHistoryDecidesWhetherThePageIsEmpty() {
        assertEquals(StatsUiState.Empty, page(summary()))
    }

    @Test
    fun theReadIsLoadingUntilItLandsAndAFailureSaysWhy() {
        assertEquals(StatsUiState.Loading, statsUiStateOf(StatsRead.Loading))
        assertEquals(StatsUiState.Failed("Could not read your stats: database is locked"), statsUiStateOf(StatsRead.Failed("database is locked")))
        assertEquals(StatsUiState.Failed("Could not read your stats."), statsUiStateOf(StatsRead.Failed(null)))
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
}
