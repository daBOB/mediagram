package ui.tv.player

import model.Kind
import org.junit.Test
import ui.tv.catalog.set
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals

/**
 * The player's own words and steps, apart from the screen: the end time
 * beside the clock, and how far a held arrow moves the film. What the top
 * bar calls a title is the shared `player.titleLine`, tested where it lives.
 */
class TvPlayerLinesTest {
    @Test
    fun theEndTimeReadsTheCataloguesRuntimeOverThePlayersLength() {
        withZone("UTC") {
            val film = set("h", Kind.MOVIE, "Heat", addedAt = 0, durationSecs = 3_600)
            // Ten minutes in, fifty to go, whatever length the player reports.
            assertEquals("ends 20:50", endsLine(film, 600_000, 9_999_000, 1f, Instant.parse("2026-09-25T20:00:00Z")))
        }
    }

    @Test
    fun theEndTimeFallsBackToThePlayersLengthAndHonoursTheSpeed() {
        withZone("UTC") {
            assertEquals("ends 20:30", endsLine(null, 0, 3_600_000, 2f, Instant.parse("2026-09-25T20:00:00Z")))
        }
    }

    @Test
    fun noKnownLengthMeansNoEndTime() {
        assertEquals("", endsLine(null, 0, 0, 1f))
    }

    @Test
    fun aTapOrShortHoldSkipsTenSeconds() {
        assertEquals(-10, seekStepSeconds(-10, repeatCount = 0))
        assertEquals(10, seekStepSeconds(10, repeatCount = 19))
    }

    @Test
    fun aLongerHoldSkipsFurtherEachRepeat() {
        assertEquals(30, seekStepSeconds(10, repeatCount = 20))
        assertEquals(-60, seekStepSeconds(-10, repeatCount = 60))
    }

    private fun withZone(
        id: String,
        block: () -> Unit,
    ) {
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(id)))
        try {
            block()
        } finally {
            TimeZone.setDefault(before)
        }
    }
}
