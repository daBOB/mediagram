package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mirrors `web/test/similar.test.ts`, case for case. */
class SimilarTest {
    private fun title(
        setId: String,
        genres: List<String> = emptyList(),
        collectionId: Long? = null,
        popularity: Double? = null,
    ): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, genres = genres, collectionId = collectionId, popularity = popularity,
        )

    @Test
    fun franchiseFirstThenUnwatchedThenGenresSharedThenPopularity() {
        val dune = title("dune2", listOf("Sci-Fi", "Adventure"), collectionId = 7)
        val picks =
            similarTo(
                dune,
                listOf(
                    dune,
                    title("dune1", listOf("Drama"), collectionId = 7),
                    title("arrival", listOf("Sci-Fi"), popularity = 50.0),
                    title("interstellar", listOf("Sci-Fi", "Adventure"), popularity = 10.0),
                    title("seen", listOf("Sci-Fi", "Adventure"), popularity = 99.0),
                    title("romcom", listOf("Romance")),
                ),
                seen = { it.setId == "seen" },
            )
        assertEquals(listOf("dune1", "interstellar", "arrival", "seen"), picks.map(MediaSet::setId))
    }

    @Test
    fun aTitleWithNothingInCommonWithAnythingHasNoSimilarTitles() {
        assertTrue(similarTo(title("a"), listOf(title("b", listOf("Drama")))).isEmpty())
    }
}
