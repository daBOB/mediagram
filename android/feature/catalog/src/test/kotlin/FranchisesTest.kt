package catalog

import model.FranchiseInfo
import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mirrors `web/test/franchises.test.ts`, case for case. */
class FranchisesTest {
    private fun film(
        setId: String,
        year: Int,
        collectionId: Long?,
        popularity: Double? = null,
        backdropPath: String? = null,
    ): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = year, durationSecs = null,
            posterPath = null, totalBytes = 0, popularity = popularity, backdropPath = backdropPath,
            collectionId = collectionId, collectionName = collectionId?.let { "Franchise $it" },
        )

    @Test
    fun franchisesNeedTwoFilmsRunLargestFirstInReleaseOrderPicturedByTheirMostPopular() {
        val found =
            franchisesIn(
                listOf(
                    film("nemesis", 2002, 1, popularity = 5.0, backdropPath = "n-bg"),
                    film("first-contact", 1996, 1, popularity = 30.0, backdropPath = "fc-bg"),
                    film("tmp", 1979, 1),
                    film("dune2", 2024, 2, backdropPath = "d2-bg"),
                    film("dune", 2021, 2),
                    film("alone", 2000, 3),
                    film("none", 2000, null),
                ),
            )
        assertEquals(
            listOf(
                Triple(1L, listOf("tmp", "first-contact", "nemesis"), "fc-bg"),
                Triple(2L, listOf("dune", "dune2"), "d2-bg"),
            ),
            found.map { Triple(it.id, it.films.map(MediaSet::setId), it.art) },
        )
    }

    @Test
    fun aFranchisePageCarriesTmdbsOverviewByIdWhenThereIsOne() {
        val movies = listOf(film("dune", 2021, 2), film("dune2", 2024, 2))
        val overviews = listOf(FranchiseInfo(2, "Dune Collection", "A boy destined for greatness."))

        val page = franchisePageOf(2, movies, overviews)

        assertEquals(2L, page?.franchise?.id)
        assertEquals("A boy destined for greatness.", page?.overview)
    }

    @Test
    fun aFranchisePageAnswersNullBelowTwoHeldFilms() {
        assertNull(franchisePageOf(1, listOf(film("nemesis", 2002, 1)), emptyList()))
    }
}
