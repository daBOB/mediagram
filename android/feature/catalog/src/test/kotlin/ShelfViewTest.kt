package catalog

import model.Kind
import model.MediaSet
import settings.ShelfView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShelfViewTest {
    private fun course(name: String) = Entry.Collection(name, CollectionKind.COURSE, name, null, null, count = 0, chapters = 0, divisions = emptyList())

    private fun film(id: String) = Entry.Film(
        MediaSet(
            setId = id, kind = Kind.MOVIE, title = id, show = null, chapter = null, path = null, season = null,
            episodeFirst = null, episodeLast = null, year = null, durationSecs = null, posterPath = null, totalBytes = 0,
        ),
    )

    @Test
    fun filmsAndSeriesOfferTheChoiceAndFollowIt() {
        val films = Shelf("Movies", listOf(film("f1")))
        assertTrue(offersViewChoice(films))
        assertEquals(ShelfView.LIST, shelfViewFor(films, ShelfView.LIST))
        assertEquals(ShelfView.GRID, shelfViewFor(films, ShelfView.GRID))
    }

    @Test
    fun coursesAreAlwaysAList() {
        val courses = Shelf("Tutorials", listOf(course("Geldhochschule")))
        assertFalse(offersViewChoice(courses))
        assertEquals(ShelfView.LIST, shelfViewFor(courses, ShelfView.GRID))
    }

    @Test
    fun anEmptyShelfOffersNothing() {
        assertFalse(offersViewChoice(Shelf("Movies", emptyList())))
    }
}
