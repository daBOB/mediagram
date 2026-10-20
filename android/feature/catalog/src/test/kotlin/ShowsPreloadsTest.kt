package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the menu offers Preloads: a queue, or a title held on this device that this profile can see. */
class ShowsPreloadsTest {
    @Test
    fun preloadsShowsWhenSomethingIsQueuedAndNothingIsHeld() {
        assertTrue(readyWith(film("Alien")).showsPreloads(queueCount = 1))
    }

    @Test
    fun preloadsShowsWhenSomethingIsHeldButNothingQueued() {
        val alien = film("Alien")
        assertTrue(readyWith(alien, held = alien).showsPreloads(queueCount = 0))
    }

    @Test
    fun preloadsHiddenWhenNothingQueuedOrHeld() {
        assertFalse(readyWith(film("Alien")).showsPreloads(queueCount = 0))
        assertFalse(CatalogUiState.Loading.showsPreloads(queueCount = 0))
    }

    /** A kids profile's catalogue lacks the grown-up's film, so a held copy of it earns no entry. */
    @Test
    fun aKidWithOnlyAHiddenHeldTitleGetsNoEntry() {
        val hidden = film("Grown-up Movie")
        val kids = CatalogUiState.Ready(shelvesOf(listOf(film("Kids Movie"))), heldIds = setOf(hidden.setId))

        assertFalse(kids.showsPreloads(queueCount = 0))
    }

    private fun readyWith(
        vararg sets: MediaSet,
        held: MediaSet? = null,
    ) = CatalogUiState.Ready(shelvesOf(sets.toList()), heldIds = setOfNotNull(held?.setId))

    private fun film(title: String) =
        MediaSet(
            setId = "film-$title", kind = Kind.MOVIE, title = title, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, posterKey = null,
        )
}
