package player

import model.Kind
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers [titleLine] — a port of `player.js`'s `titleLine`: show · episode · title. */
class PlayerTitleLineTest {

    @Test
    fun aFilmIsJustItsTitle() {
        val film = fakeMediaSet(setId = "01FILM", title = "Blade Runner 2049")

        assertEquals("Blade Runner 2049", titleLine(film))
    }

    @Test
    fun anEpisodeIsShowThenNumberThenTitle() {
        val episode = fakeMediaSet(
            setId = "01A",
            kind = Kind.EPISODE,
            show = "30 Rock",
            season = 1,
            episodeFirst = 2,
            title = "The Aftermath",
        )

        assertEquals("30 Rock · S1E2 · The Aftermath", titleLine(episode))
    }

    @Test
    fun aLessonWithNoSeasonHasNoS() {
        val lesson = fakeMediaSet(
            setId = "01A",
            kind = Kind.TUTORIAL,
            show = "Geldhochschule",
            episodeFirst = 4,
            title = "Zinsen",
        )

        assertEquals("Geldhochschule · 4 · Zinsen", titleLine(lesson))
    }

    @Test
    fun anUnnumberedSetSkipsTheEpisodeSegment() {
        val set = fakeMediaSet(setId = "01A", show = "Geldhochschule", title = "Intro")

        assertEquals("Geldhochschule · Intro", titleLine(set))
    }

    @Test
    fun nothingOpenIsBlank() {
        assertEquals("", titleLine(null))
    }

    /**
     * `title` still falls back to the show's name for anything that needs
     * *some* string to display (a shelf card); `rawTitle`, which
     * [titleLine] reads, does not — this is what stops the title line from
     * repeating the show it already just said.
     */
    @Test
    fun anUntitledEpisodeDropsTheTitleSegmentRatherThanRepeatingTheShow() {
        val episode = fakeMediaSet(
            setId = "01A",
            kind = Kind.EPISODE,
            title = "30 Rock", // the fallback a shelf card would show
            rawTitle = null, // what the index actually said: nothing
            show = "30 Rock",
            season = 1,
            episodeFirst = 2,
        )

        assertEquals("30 Rock · S1E2", titleLine(episode))
    }

    /** As above, for a one-off film with no show to fall back to — `title` would otherwise be the raw set id. */
    @Test
    fun aTitleLessFilmDropsTheTitleSegmentRatherThanPrintingTheRawSetId() {
        val film = fakeMediaSet(setId = "01FILM", title = "01FILM", rawTitle = null)

        assertEquals("", titleLine(film))
    }
}
