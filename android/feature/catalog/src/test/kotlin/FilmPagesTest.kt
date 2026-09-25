package catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/** The cases `pager.test.ts` pins on the web, so the two pagers agree. */
class FilmPagesTest {
    private val films = (0 until 100).toList()
    private val gap = PageLink.Gap

    private fun to(vararg pages: Any): List<PageLink> = pages.map { if (it is Int) PageLink.To(it) else gap }

    @Test
    fun cutsTheRequestedPageAndReportsWhereItSits() {
        assertEquals(Page(films.subList(0, 48), 1, 3), pageOf(films, 1, 48))
        assertEquals(films.subList(48, 96), pageOf(films, 2, 48).items)
    }

    @Test
    fun aPartialLastPageHoldsOnlyWhatIsLeft() {
        assertEquals(Page(films.subList(96, 100), 3, 3), pageOf(films, 3, 48))
    }

    @Test
    fun aPageOutOfRangeLandsOnTheNearestEnd() {
        assertEquals(3, pageOf(films, 9, 48).page)
        assertEquals(1, pageOf(films, 0, 48).page)
        assertEquals(1, pageOf(films, -2, 48).page)
    }

    @Test
    fun anEmptyShelfIsOneEmptyPage() {
        assertEquals(Page(emptyList<Int>(), 1, 1), pageOf(emptyList<Int>(), 4, 48))
    }

    @Test
    fun aSinglePageNeedsNoPager() {
        assertEquals(emptyList(), pageLinks(1, 1))
    }

    @Test
    fun fewPagesAreAllListed() {
        assertEquals(to(1, 2, 3, 4, 5, 6, 7), pageLinks(3, 7))
    }

    @Test
    fun manyPagesKeepTheEndsAndTheNeighboursWithGapsBetween() {
        assertEquals(to(1, gap, 14, 15, 16, gap, 30), pageLinks(15, 30))
        assertEquals(to(1, 2, gap, 30), pageLinks(1, 30))
        assertEquals(to(1, gap, 29, 30), pageLinks(30, 30))
    }

    @Test
    fun aGapOfASinglePageIsWrittenAsThatPage() {
        assertEquals(to(1, 2, 3, 4, 5, gap, 30), pageLinks(4, 30))
        assertEquals(to(1, gap, 26, 27, 28, 29, 30), pageLinks(27, 30))
    }
}
