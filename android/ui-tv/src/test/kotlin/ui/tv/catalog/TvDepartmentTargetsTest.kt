package ui.tv.catalog

import catalog.CategoryRow
import catalog.CollectionKind
import catalog.Entry
import catalog.GenreIndexEntry
import catalog.MoviesDepartment
import catalog.SetCard
import catalog.ShowsDepartment
import catalog.Underway
import model.Kind
import model.MediaSet
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [moviesDeptTargetOf], [showsDeptTargetOf], [animeDeptTargetOf] and
 * [documentariesDeptTargetOf] as plain functions, pure and fast: a genre or
 * the "All N films" link resolving from a restore key, a header row winning
 * over [TvWall]'s own plate-0 default, and [lastSection] breaking a tie
 * between two rows that both carry the same key — [restoreTargetOf]'s own
 * rule, shared by every one of them.
 */
class TvDepartmentTargetsTest {
    private val dept =
        MoviesDepartment(
            filmCount = 10,
            hours = 5,
            lead = null,
            featured = listOf(film("f0"), film("f1")),
            genres = listOf(GenreIndexEntry("Action", 3, null), GenreIndexEntry("Drama", 2, null)),
            acclaimed = listOf(film("a0")),
            recentlyAdded = listOf(film("r0"), film("f0")),
        )

    @Test
    fun theAllFilmsSentinelKeyLandsOnTheAllLink() {
        assertEquals(SectionStop(MoviesSection.ALL, 0), moviesDeptTargetOf(dept, TvMoviesPageEntryKey))
    }

    @Test
    fun aGenreNameRestoreKeyLandsOnItsOwnTileNotTheFirstRow() {
        assertEquals(SectionStop(MoviesSection.GENRES, 1), moviesDeptTargetOf(dept, "Drama"))
    }

    @Test
    fun aFilmIdRestoreKeyStillLandsOnTheRowThatHoldsIt() {
        assertEquals(SectionStop(MoviesSection.ACCLAIMED, 0), moviesDeptTargetOf(dept, "a0"))
    }

    @Test
    fun noOrUnmatchedRestoreKeyFallsBackToTheFirstNonEmptyRow() {
        assertEquals(SectionStop(MoviesSection.FEATURED, 0), moviesDeptTargetOf(dept, null))
        assertEquals(SectionStop(MoviesSection.FEATURED, 0), moviesDeptTargetOf(dept, "no-such-id"))
    }

    /** "f0" sits in both Featured and Recently added; [lastSection] breaks the tie. */
    @Test
    fun aKeyCarriedByTwoRowsGoesBackToTheRowTheRemoteWasLastIn() {
        assertEquals(SectionStop(MoviesSection.FEATURED, 0), moviesDeptTargetOf(dept, "f0", lastSection = null))
        assertEquals(SectionStop(MoviesSection.RECENTLY_ADDED, 1), moviesDeptTargetOf(dept, "f0", lastSection = MoviesSection.RECENTLY_ADDED))
    }

    @Test
    fun withNoHeroTheFirstNonEmptyHeaderRowWins() {
        val popular = listOf(collection("SHOW/A", "A"))
        val dept = showsDeptOf(popular = popular)

        assertEquals(SectionStop("popular", 0), showsDeptTargetOf(dept, underway = emptyList(), restoreKey = null))
    }

    @Test
    fun aRestoreKeyMatchingAPopularEntryLandsOnItRatherThanTheFirstRow() {
        val popular = listOf(collection("SHOW/A", "A"), collection("SHOW/B", "B"))
        val dept = showsDeptOf(popular = popular)

        assertEquals(SectionStop("popular", 1), showsDeptTargetOf(dept, underway = emptyList(), restoreKey = "SHOW/B"))
    }

    @Test
    fun aRestoreKeyNamingNoneOfTheHeaderRowsDefersToTheWallItself() {
        val popular = listOf(collection("SHOW/A", "A"))
        val dept = showsDeptOf(popular = popular)

        assertNull(showsDeptTargetOf(dept, underway = emptyList(), restoreKey = "SHOW/Somewhere Else"))
    }

    /** A category row wins over Popular, but a title already underway still wins over it. */
    @Test
    fun withNothingUnderwayTheFirstCategoryRowWinsOverPopular() {
        val popular = listOf(collection("SHOW/A", "A"))
        val categories = listOf(CategoryRow("Trading", listOf(collection("COURSE/B", "B"))))
        val dept = showsDeptOf(popular = popular, categories = categories)

        assertEquals(SectionStop("category:0", 0), showsDeptTargetOf(dept, underway = emptyList(), restoreKey = null))
    }

    @Test
    fun aRestoreKeyMatchingACategoryRowEntryLandsOnItRatherThanPopular() {
        val popular = listOf(collection("SHOW/A", "A"))
        val categories =
            listOf(
                CategoryRow("Trading", listOf(collection("COURSE/B", "B"))),
                CategoryRow("Health", listOf(collection("COURSE/C", "C"))),
            )
        val dept = showsDeptOf(popular = popular, categories = categories)

        assertEquals(SectionStop("category:1", 0), showsDeptTargetOf(dept, underway = emptyList(), restoreKey = "COURSE/C"))
    }

    @Test
    fun aResumeCardRestoreKeyLandsOnTheUnderwayRow() {
        val cards = listOf(card("s0"), card("s1"))
        assertEquals(SectionStop("underway", 1), showsDeptTargetOf(showsDeptOf(), underway = cards, restoreKey = "s1"))
    }

    @Test
    fun animeContinueWinsArrivalWhenItHasCards() {
        assertEquals(SectionStop("continue", 0), animeDeptTargetOf(listOf(card("s0")), restoreKey = null))
    }

    @Test
    fun animeWithNothingUnderwayDefersToTheWall() {
        assertNull(animeDeptTargetOf(emptyList(), restoreKey = null))
        assertNull(animeDeptTargetOf(listOf(card("s0")), restoreKey = "not-underway"))
    }

    @Test
    fun documentariesFallsBackToTheFirstNonEmptySectionInOrder() {
        val sections = listOf(DeptSection("continue", emptyList()), DeptSection("recentlyAdded", listOf("d0")))
        assertEquals(SectionStop("recentlyAdded", 0), documentariesDeptTargetOf(sections, restoreKey = null))
    }

    @Test
    fun documentariesRestoreKeyWinsOverTheFallbackSection() {
        val sections = listOf(DeptSection("continue", listOf("d1")), DeptSection("recentlyAdded", listOf("d0", "d1")))
        assertEquals(SectionStop("continue", 0), documentariesDeptTargetOf(sections, restoreKey = "d1"))
    }

    private fun showsDeptOf(
        popular: List<Entry.Collection> = emptyList(),
        categories: List<CategoryRow<Entry.Collection>> = emptyList(),
    ) = ShowsDepartment(
        showCount = popular.size,
        itemCount = popular.size,
        lead = null,
        underway = Underway(continues = emptyList(), nextUp = emptyList(), continuesTotal = 0, nextUpTotal = 0),
        categories = categories,
        popular = popular,
        newEpisodes = emptyList(),
        all = popular,
    )

    private fun film(id: String) = set(id, Kind.MOVIE, id, addedAt = 0)

    private fun card(setId: String) = SetCard(film(setId), "", null, false)

    private fun collection(
        key: String,
        name: String,
    ) = Entry.Collection(key = key, kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null, count = 1, chapters = 1, divisions = emptyList())
}
