package player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Covers [scopeOf] — a port of `web/test/preference-scope.test.ts`. */
class PreferenceScopeTest {

    @Test
    fun anIdentifiedSeriesFilesEveryEpisodeUnderTheShow() {
        // The whole point: choosing English on episode 1 is choosing it for 22.
        val one = fakeMediaSet(setId = "01A", posterKey = "tmdb-tv-1399", show = "30 Rock")
        val two = fakeMediaSet(setId = "01B", posterKey = "tmdb-tv-1399", show = "30 Rock")

        assertEquals("key:tmdb-tv-1399", scopeOf(one))
        assertEquals(scopeOf(one), scopeOf(two))
    }

    @Test
    fun theKeySurvivesTheShowBeingRenamed() {
        val set = fakeMediaSet(setId = "01A", posterKey = "tmdb-tv-1399", show = "30 Rock (2006)")

        assertEquals("key:tmdb-tv-1399", scopeOf(set))
    }

    @Test
    fun aCourseWithNoKeyFallsBackToTheShowName() {
        // 170 lessons with no TMDB id between them. Filing per lesson would
        // mean choosing a playback speed 170 times.
        val a = fakeMediaSet(setId = "01A", show = "Geldhochschule")
        val b = fakeMediaSet(setId = "01B", show = "Geldhochschule")

        assertEquals("show:Geldhochschule", scopeOf(a))
        assertEquals(scopeOf(a), scopeOf(b))
    }

    @Test
    fun aNameThatIsOnlyWhitespaceIsNotAName() {
        val set = fakeMediaSet(setId = "01A", show = "   ")

        assertEquals("set:01A", scopeOf(set))
    }

    @Test
    fun aOneOffIsFiledUnderItself() {
        val set = fakeMediaSet(setId = "01FILM")

        assertEquals("set:01FILM", scopeOf(set))
    }

    @Test
    fun aOneOffDoesNotLeakItsChoiceToAnother() {
        assertNotEquals(scopeOf(fakeMediaSet(setId = "01A")), scopeOf(fakeMediaSet(setId = "01B")))
    }

    @Test
    fun theNamespacesCannotCollide() {
        // Without the prefix this would be filed with whatever tv-1399 is.
        assertEquals("show:tmdb-tv-1399", scopeOf(fakeMediaSet(setId = "01A", show = "tmdb-tv-1399")))
        assertEquals("key:tmdb-tv-1399", scopeOf(fakeMediaSet(setId = "01A", posterKey = "tmdb-tv-1399")))
        assertEquals("set:Geldhochschule", scopeOf(fakeMediaSet(setId = "Geldhochschule")))
    }

    @Test
    fun nothingToFileItUnderIsNothingNotAnEmptyKeyEverythingShares() {
        assertNull(scopeOf(null))
    }
}
