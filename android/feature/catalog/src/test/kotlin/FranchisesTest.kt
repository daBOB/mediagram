package catalog

import model.FranchiseInfo
import model.Kind
import model.ListOfSets
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

    @Test
    fun aListIsPicturedByItsFirstPicturedTitleBackdropBeforePoster() {
        val byId = listOf(
            film("bare", 2000, null),
            film("poster-only", 2001, null).copy(posterPath = "p-poster"),
            film("both", 2002, null, backdropPath = "b-bg").copy(posterPath = "b-poster"),
        ).associateBy(MediaSet::setId)

        assertEquals("p-poster", listArtOf(ListOfSets("l", "Mine", listOf("gone", "bare", "poster-only", "both")), byId))
        assertEquals("b-bg", listArtOf(ListOfSets("l", "Mine", listOf("both", "poster-only")), byId))
        assertNull(listArtOf(ListOfSets("l", "Mine", listOf("gone", "bare")), byId))
    }

    /** "25 films · 1962–2021": the count in words and the span of the years known, an undated film left out of it. */
    @Test
    fun aFranchisesLineCountsItsFilmsAndSpansTheirKnownYears() {
        fun saga(vararg years: Int) = Franchise(5, "Saga", years.mapIndexed { i, year -> film("f$i", year, 5) }, null)
        assertEquals("three films · 1962–2021", franchiseLineOf(saga(1979, 1962, 2021)))
        assertEquals("two films · 1999–1999", franchiseLineOf(saga(1999, 0)))
        assertEquals("two films", franchiseLineOf(saga(0, 0)))
    }
}
