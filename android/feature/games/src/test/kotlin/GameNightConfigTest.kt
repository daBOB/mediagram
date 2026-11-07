package games

import model.Kind
import model.MediaSet
import model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun film(
    id: String,
    fsk: String?,
) = MediaSet(
    setId = id,
    kind = Kind.MOVIE,
    title = id,
    show = null,
    chapter = null,
    path = null,
    season = null,
    episodeFirst = null,
    episodeLast = null,
    year = null,
    durationSecs = null,
    posterPath = null,
    totalBytes = 0,
    fsk = fsk,
)

class GameNightConfigTest {
    private val sets = listOf(film("f0", "0"), film("f6", "6"), film("f12", "12"), film("f16", "16"), film("marked", null), film("loose", null))

    @Test
    fun kidsProfileLocksAtItsOwnLimit() {
        val kid = Profile("k", "Kim", kids = true, kidsAge = 6)

        val config = gameNightConfig(sets, kid, mapOf("marked" to 6))

        assertEquals(listOf("f0", "f6", "marked"), config.kidsSetIds)
        assertTrue(config.kidsLocked)
        assertEquals(null, config.tmdbKey)
    }

    @Test
    fun grownUpProfileMarksFsk12() {
        val grownUp = Profile("g", "Gus")

        val config = gameNightConfig(sets, grownUp, mapOf("marked" to 12))

        assertEquals(listOf("f0", "f6", "f12", "marked"), config.kidsSetIds)
        assertFalse(config.kidsLocked)
    }

    @Test
    fun setsInHiddenSectionsAreNotPassed() {
        val episode = film("e1", "0").copy(kind = Kind.EPISODE, show = "Dark")
        val anime = film("a1", "0").copy(anime = true)
        val grownUp = Profile("g", "Gus", hiddenSections = listOf("series", "anime"))

        val config = gameNightConfig(listOf(film("f0", "0"), episode, anime), grownUp, emptyMap())

        assertEquals(listOf("f0"), config.visibleSetIds)
    }
}
