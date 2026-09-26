package ui.tv.catalog

import catalog.CollectionKind
import catalog.Entry
import catalog.GenreIndexEntry
import catalog.MoviesDepartment
import catalog.ShowsDepartment
import catalog.Underway
import model.Kind
import org.junit.Test
import ui.tv.TvMoviesPageEntryKey
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [moviesDeptTargetOf] and [showsDeptTargetOf] as plain functions, pure and
 * fast — the arithmetic N7 and M3 actually depend on, checked without
 * standing up a screen: a genre or the "All N films" link resolving from
 * [restoreKey], and a header row or the hero winning arrival over
 * [TvWall]'s own plate-0 default.
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
            recentlyAdded = listOf(film("r0")),
        )

    @Test
    fun theAllFilmsSentinelKeyLandsOnTheAllLink() {
        assertEquals("all" to 0, moviesDeptTargetOf(dept, TvMoviesPageEntryKey))
    }

    @Test
    fun aGenreNameRestoreKeyLandsOnItsOwnTileNotTheFirstRow() {
        assertEquals("genres" to 1, moviesDeptTargetOf(dept, "Drama"))
    }

    @Test
    fun aFilmIdRestoreKeyStillLandsOnTheRowThatHoldsIt() {
        assertEquals("acclaimed" to 0, moviesDeptTargetOf(dept, "a0"))
    }

    @Test
    fun noOrUnmatchedRestoreKeyFallsBackToTheFirstNonEmptyRow() {
        assertEquals("featured" to 0, moviesDeptTargetOf(dept, null))
        assertEquals("featured" to 0, moviesDeptTargetOf(dept, "no-such-id"))
    }

    @Test
    fun theMoviesHeroTakesArrivalButNeverOutranksATitleToReturnTo() {
        assertEquals("hero" to 0, moviesDeptTargetOf(dept, null, heroFocusable = true))
        assertEquals("hero" to 0, moviesDeptTargetOf(dept, "no-such-id", heroFocusable = true))
        assertEquals("acclaimed" to 0, moviesDeptTargetOf(dept, "a0", heroFocusable = true))
    }

    @Test
    fun aFocusableHeroOutranksANonEmptyHeaderRowWithNoRestoreKey() {
        val popular = listOf(collection("SHOW/A", "A"))
        val dept = showsDeptOf(popular = popular)

        assertEquals("hero" to 0, showsDeptTargetOf(dept, underway = emptyList(), heroFocusable = true, restoreKey = null))
    }

    @Test
    fun withNoHeroTheFirstNonEmptyHeaderRowWins() {
        val popular = listOf(collection("SHOW/A", "A"))
        val dept = showsDeptOf(popular = popular)

        assertEquals("popular" to 0, showsDeptTargetOf(dept, underway = emptyList(), heroFocusable = false, restoreKey = null))
    }

    @Test
    fun aRestoreKeyMatchingAPopularEntryLandsOnItRatherThanTheHero() {
        val popular = listOf(collection("SHOW/A", "A"), collection("SHOW/B", "B"))
        val dept = showsDeptOf(popular = popular)

        assertEquals("popular" to 1, showsDeptTargetOf(dept, underway = emptyList(), heroFocusable = true, restoreKey = "SHOW/B"))
    }

    @Test
    fun aRestoreKeyNamingNoneOfTheHeaderRowsDefersToTheWallItself() {
        val popular = listOf(collection("SHOW/A", "A"))
        val dept = showsDeptOf(popular = popular)

        assertNull(showsDeptTargetOf(dept, underway = emptyList(), heroFocusable = true, restoreKey = "SHOW/Somewhere Else"))
    }

    private fun showsDeptOf(popular: List<Entry.Collection>) =
        ShowsDepartment(
            showCount = popular.size,
            itemCount = popular.size,
            lead = null,
            underway = Underway(continues = emptyList(), nextUp = emptyList(), continuesTotal = 0, nextUpTotal = 0),
            popular = popular,
            newEpisodes = emptyList(),
            all = popular,
        )

    private fun film(id: String) = set(id, Kind.MOVIE, id, addedAt = 0)

    private fun collection(
        key: String,
        name: String,
    ) = Entry.Collection(key = key, kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null, count = 1, chapters = 1, divisions = emptyList())
}
