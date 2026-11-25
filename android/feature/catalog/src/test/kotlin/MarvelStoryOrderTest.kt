package catalog

import model.Kind
import model.MediaSet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/** Mirrors the MCU cases in `web/test/franchises.test.ts`, and holds the id list to the web's. */
class MarvelStoryOrderTest {
    private fun film(
        setId: String,
        tmdb: Long,
        variant: String? = null,
        quality: String? = null,
        popularity: Double? = null,
        backdropPath: String? = null,
    ): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2000, durationSecs = null,
            posterPath = null, totalBytes = 0, posterKey = "tmdb-movie-$tmdb", variant = variant, quality = quality,
            popularity = popularity, backdropPath = backdropPath,
        )

    @Test
    fun theMcuIsOneMoreFranchiseItsFilmsInStoryOrderNotReleaseOrder() {
        val mcu =
            franchisesIn(
                listOf(
                    film("iron-man", 1726),
                    film("first-avenger", 1771),
                    film("captain-marvel", 299537, popularity = 9.0, backdropPath = "cm-bg"),
                    film("not-mcu", 557),
                ),
            ).single { it.id == MCU_ID }
        assertEquals(listOf("first-avenger", "captain-marvel", "iron-man"), mcu.films.map(MediaSet::setId))
        assertEquals("In story order", mcu.order)
        assertEquals("cm-bg", mcu.art)
    }

    @Test
    fun aFilmHeldTwiceIsInTheMcuOnceAndEndgameIsItsEncoreCut() {
        val mcu =
            mcuIn(
                listOf(
                    film("endgame", 299534, quality = "1080p"),
                    film("endgame-encore", 299534, variant = "Encore", quality = "2160p"),
                    film("infinity-war", 299536),
                ),
            )
        assertEquals(listOf("infinity-war", "endgame-encore"), mcu?.films?.map(MediaSet::setId))
        assertEquals(listOf("endgame", "endgame-encore"), mcu?.films?.get(1)?.versions?.map(MediaSet::setId))
    }

    @Test
    fun oneMcuFilmIsATitleNotACollection() {
        assertNull(mcuIn(listOf(film("iron-man", 1726))))
        assertEquals(emptyList(), franchisesIn(listOf(film("iron-man", 1726))))
    }

    @Test
    fun theStoryOrderIsTheWebs() {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val web =
            generateSequence(start) { it.parentFile }
                .map { File(it, "web/public/lib/catalog/mcu.js") }
                .firstOrNull { it.isFile } ?: fail("mcu.js not found above $start")
        val list = Regex("""MCU_STORY_ORDER = \[(.*?)];""", RegexOption.DOT_MATCHES_ALL).find(web.readText())?.groupValues?.get(1)
            ?: fail("MCU_STORY_ORDER not found in $web")
        // Ids only: each line's trailing `// title` comment is dropped first.
        val ids = list.lines().map { it.substringBefore("//") }.flatMap { Regex("""\d+""").findAll(it).map { m -> m.value.toLong() } }
        assertEquals(ids, MCU_STORY_ORDER)
    }
}
