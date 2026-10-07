package ui.tv.catalog.home

import ui.tv.catalog.DeptSection
import ui.tv.catalog.SectionStop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvHomeTargetsTest {
    private val cover = DeptSection(TvHomeSection.COVER, listOf("film-a", "film-b"))
    private val continueBand = DeptSection(TvHomeSection.CONTINUE, listOf("film-c"))
    private val recent = DeptSection(TvHomeSection.RECENT, listOf("film-d", "film-e"))
    private val series = DeptSection(TvHomeSection.SERIES, emptyList<String>())

    @Test
    fun withNoRestoreKeyTheDefaultIsTheFirstSectionsFirstStop() {
        val target = homeTargetOf(listOf(cover, continueBand, recent, series), restoreKey = null)

        assertEquals(SectionStop(TvHomeSection.COVER, 0), target)
    }

    @Test
    fun anEmptySectionIsSkippedForTheDefaultToo() {
        val target = homeTargetOf(listOf(series, continueBand), restoreKey = null)

        assertEquals(SectionStop(TvHomeSection.CONTINUE, 0), target)
    }

    @Test
    fun aRestoreKeyLandsOnItsOwnSectionAndStopWhereverItSits() {
        val target = homeTargetOf(listOf(cover, continueBand, recent), restoreKey = "film-e")

        assertEquals(SectionStop(TvHomeSection.RECENT, 1), target)
    }

    /** A new upload that is also trending sits in two bands; Back returns to the one it was opened from. */
    @Test
    fun aKeyTwoBandsCarryGoesBackToTheBandTheRemoteWasIn() {
        val features = DeptSection(TvHomeSection.FEATURES, listOf("film-e"))
        val sections = listOf(cover, features, recent)

        assertEquals(SectionStop(TvHomeSection.RECENT, 1), homeTargetOf(sections, "film-e", lastSection = TvHomeSection.RECENT))
        assertEquals(SectionStop(TvHomeSection.FEATURES, 0), homeTargetOf(sections, "film-e", lastSection = null))
        assertEquals(SectionStop(TvHomeSection.FEATURES, 0), homeTargetOf(sections, "film-e", lastSection = TvHomeSection.COVER))
    }

    @Test
    fun aRestoreKeyInTheCoverFindsThatFilmsOwnStop() {
        val target = homeTargetOf(listOf(cover, continueBand), restoreKey = "film-b")

        assertEquals(SectionStop(TvHomeSection.COVER, 1), target)
    }

    @Test
    fun aMissingKeyFallsBackToTheDefault() {
        val target = homeTargetOf(listOf(cover, continueBand), restoreKey = "film-not-on-the-page")

        assertEquals(SectionStop(TvHomeSection.COVER, 0), target)
    }

    @Test
    fun everySectionEmptyHasNoTarget() {
        val target = homeTargetOf(listOf(series, DeptSection(TvHomeSection.COURSES, emptyList())), restoreKey = null)

        assertNull(target)
    }
}
