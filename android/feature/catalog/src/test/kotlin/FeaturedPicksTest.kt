package catalog

import model.Kind
import model.MediaSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The cases `featured-picks.test.ts` pins on the web. */
class FeaturedPicksTest {
    private fun film(
        id: String,
        poster: String? = "/art/$id.jpg",
    ) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = id, show = null, chapter = null, path = null, season = null,
        episodeFirst = null, episodeLast = null, year = null, durationSecs = null, posterPath = poster, totalBytes = 0,
    )

    /** Always the same fraction, so a shuffle is repeatable. */
    private fun fixed(value: Double) =
        object : Random() {
            override fun nextBits(bitCount: Int) = 0

            override fun nextDouble() = value
        }

    @Test
    fun leavesOutFilmsAlreadyWatchedAndFilmsWithNoPoster() {
        val films = listOf(film("a"), film("b"), film("c", poster = null), film("d"))
        val picks = pickFeatured(films, setOf("b"), fixed(0.0))
        assertEquals(listOf("a", "d"), picks.map(MediaSet::setId).sorted())
    }

    @Test
    fun stopsAtTheReelsLength() {
        val films = (0 until 40).map { film("f$it") }
        assertEquals(FEATURED_COUNT, pickFeatured(films, emptySet(), Random(1)).size)
        assertEquals(12, FEATURED_COUNT)
    }

    @Test
    fun theOrderFollowsTheRandomSourceAndTheShelfIsLeftAsItWas() {
        val films = listOf("a", "b", "c", "d").map { film(it) }
        val first = pickFeatured(films, emptySet(), fixed(0.0)).map(MediaSet::setId)
        val again = pickFeatured(films, emptySet(), fixed(0.0)).map(MediaSet::setId)
        assertEquals(first, again)
        assertNotEquals(listOf("a", "b", "c", "d"), first)
        assertEquals(listOf("a", "b", "c", "d"), films.map(MediaSet::setId))
    }

    @Test
    fun nothingToFeatureIsAnEmptyReel() {
        assertTrue(pickFeatured(listOf(film("a")), setOf("a"), Random(1)).isEmpty())
    }

    @Test
    fun stepsWrapAtBothEnds() {
        assertEquals(1, stepFrom(0, 1, 3))
        assertEquals(0, stepFrom(2, 1, 3))
        assertEquals(2, stepFrom(0, -1, 3))
    }
}
