package stats

import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.EarnedAchievement
import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.NextAchievement
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AchievementsUiTest {
    @Test
    fun anEarnedDayIsTheHistorysWordingWithoutItsClock() {
        assertEquals("today", earnedOn(ms(2026, 9, 26, 0, 0), Now))
        assertEquals("Wed", earnedOn(ms(2026, 9, 23, 0, 0), Now))
        assertEquals("19 Sep", earnedOn(ms(2026, 9, 19, 0, 0), Now))
        assertEquals("21 Sep 2025", earnedOn(ms(2025, 9, 21, 0, 0), Now))
    }

    @Test
    fun theOffsetIsTheOneInForceAtThatMomentAcrossAClockChange() {
        assertEquals(60, utcOffsetMinutes(Instant.parse("2026-03-29T00:59:00Z").atZone(Berlin)))
        assertEquals(120, utcOffsetMinutes(Instant.parse("2026-03-29T01:00:00Z").atZone(Berlin)))
        assertEquals(120, utcOffsetMinutes(Instant.parse("2026-10-25T00:59:00Z").atZone(Berlin)))
        assertEquals(60, utcOffsetMinutes(Instant.parse("2026-10-25T01:00:00Z").atZone(Berlin)))
    }

    @Test
    fun theSectionArrivesAsFinishedStrings() {
        val ui =
            achievementsUiOf(
                Achievements(
                    earned = listOf(EarnedAchievement(id = "films-1", earnedAt = ms(2026, 9, 26, 21, 0))),
                    next = listOf(NextAchievement(id = "films-10", have = 7u, need = 10u)),
                ),
                Now,
            )
        assertEquals(listOf(EarnedLine(id = "films-1", label = "First film", on = "today")), ui.earned)
        assertEquals(listOf(NextLine(id = "films-10", label = "10 films", progress = "7 of 10 films", fraction = 0.7f)), ui.next)
    }

    @Test
    fun nothingEarnedAndNothingToComeIsNone() {
        assertEquals(AchievementsUi.None, achievementsUiOf(NO_ACHIEVEMENTS, Now))
    }

    @Test
    fun aReadPageCarriesItsAchievementsFinished() {
        val read =
            StatsRead.Done(
                summary(all = 60.0, history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 26, 21, 0), 60.0))),
                Now,
                Achievements(earned = listOf(EarnedAchievement(id = "films-1", earnedAt = ms(2026, 9, 26, 21, 0))), next = emptyList()),
            )
        val ready = assertIs<StatsUiState.Ready>(statsUiStateOf(read, Catalogue))
        assertEquals(listOf(EarnedLine(id = "films-1", label = "First film", on = "today")), ready.achievements.earned)
    }
}
