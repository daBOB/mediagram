package ui.tv.catalog.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvHomeTargetsTest {
    private val cover = TvHomeSection.COVER to listOf("film-a", "film-b")
    private val continueBand = TvHomeSection.CONTINUE to listOf("film-c")
    private val recent = TvHomeSection.RECENT to listOf("film-d", "film-e")
    private val series = TvHomeSection.SERIES to emptyList<String>()

    @Test
    fun withNoRestoreKeyTheDefaultIsTheFirstSectionsFirstStop() {
        val target = homeTargetOf(listOf(cover, continueBand, recent, series), restoreKey = null)

        assertEquals(TvHomeTarget(TvHomeSection.COVER, 0), target)
    }

    @Test
    fun anEmptySectionIsSkippedForTheDefaultToo() {
        val target = homeTargetOf(listOf(series, continueBand), restoreKey = null)

        assertEquals(TvHomeTarget(TvHomeSection.CONTINUE, 0), target)
    }

    @Test
    fun aRestoreKeyLandsOnItsOwnSectionAndStopWhereverItSits() {
        val target = homeTargetOf(listOf(cover, continueBand, recent), restoreKey = "film-e")

        assertEquals(TvHomeTarget(TvHomeSection.RECENT, 1), target)
    }

    @Test
    fun aRestoreKeyInTheCoverFindsThatFilmsOwnStop() {
        val target = homeTargetOf(listOf(cover, continueBand), restoreKey = "film-b")

        assertEquals(TvHomeTarget(TvHomeSection.COVER, 1), target)
    }

    @Test
    fun aMissingKeyFallsBackToTheDefault() {
        val target = homeTargetOf(listOf(cover, continueBand), restoreKey = "film-not-on-the-page")

        assertEquals(TvHomeTarget(TvHomeSection.COVER, 0), target)
    }

    @Test
    fun everySectionEmptyHasNoTarget() {
        val target = homeTargetOf(listOf(series, TvHomeSection.COURSES to emptyList()), restoreKey = null)

        assertNull(target)
    }
}
