package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the bar says it is showing. The title is the one piece of chrome that
 * has to change per destination, and getting it from a function rather than
 * from each screen is what stops five screens inventing five spellings.
 */
class AppChromeTest {
    @Test
    fun theCatalogIsTheAppItself() {
        assertEquals("Mediagram", barTitleFor(Destination.Catalog))
    }

    @Test
    fun aCollectionIsNamedAfterWhatItHolds() {
        assertEquals("Spartacus", barTitleFor(Destination.Collection("Spartacus")))
    }

    @Test
    fun aSeasonIsNamedAfterItself() {
        assertEquals("Season 2", barTitleFor(Destination.Season("Season 2")))
        assertEquals("Back", backLabelFor(Destination.Season("Season 2")))
    }

    @Test
    fun aTitleIsNamedAfterItself() {
        assertEquals("Blade: Trinity", barTitleFor(Destination.Title("Blade: Trinity")))
    }

    @Test
    fun searchIsNamedForWhatItIsRatherThanTheQuery() {
        assertEquals("Search", barTitleFor(Destination.Search))
        assertEquals("Back", backLabelFor(Destination.Search))
    }

    @Test
    fun aGenrePageIsNamedAfterTheGenre() {
        assertEquals("Krimi", barTitleFor(Destination.Genre("Krimi")))
        assertEquals("Back", backLabelFor(Destination.Genre("Krimi")))
    }

    /** An icon that reopens the screen already on screen has nothing left to do. */
    @Test
    fun theSearchActionIsHiddenOnTheSearchScreenAlone() {
        assertFalse(showsSearchAction(Destination.Search))
        assertTrue(showsSearchAction(Destination.Catalog))
        assertTrue(showsSearchAction(Destination.System))
        assertTrue(showsSearchAction(Destination.Genre("Krimi")))
    }

    @Test
    fun theSystemScreenSaysWhatItIs() {
        assertEquals("System", barTitleFor(Destination.System))
    }

    @Test
    fun theKeyScreenSaysWhatItIs() {
        assertEquals("TMDB key", barTitleFor(Destination.TmdbKey))
    }

    @Test
    fun theSettingsScreenSaysWhatItIs() {
        assertEquals("Settings", barTitleFor(Destination.Settings))
    }

    /**
     * Every screen the menu opens is a screen like any other: it is named,
     * and it can be left. A destination reachable only through the menu is
     * the one nobody thinks to check, which is how the key screen went the
     * whole of this branch with no test naming it at all.
     */
    @Test
    fun everyMenuScreenIsNamedAndCanBeLeft() {
        for (screen in MenuScreen.entries) {
            assertTrue(barTitleFor(screen.destination).isNotEmpty(), "${screen.name} has no name in the bar")
            assertEquals("Back", backLabelFor(screen.destination))
        }
    }

    /**
     * The catalog is the top of the tree; a back arrow there would either do
     * nothing or leave the app, and both are worse than no arrow.
     */
    @Test
    fun onlyASubScreenOffersAWayBack() {
        assertNull(backLabelFor(Destination.Catalog))
        assertEquals("Back", backLabelFor(Destination.System))
        assertEquals("Back", backLabelFor(Destination.TmdbKey))
        assertEquals("Back", backLabelFor(Destination.Settings))
        assertEquals("Back", backLabelFor(Destination.Title("Blade: Trinity")))
    }
}
