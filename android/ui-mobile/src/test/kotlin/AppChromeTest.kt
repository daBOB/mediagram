package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the bar says it is showing. The title is the one piece of chrome that
 * has to change per destination, and getting it from a function rather than
 * from each screen is what stops four screens inventing four spellings.
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
    fun theSystemScreenSaysWhatItIs() {
        assertEquals("System", barTitleFor(Destination.System))
    }

    /**
     * The catalog is the top of the tree; a back arrow there would either do
     * nothing or leave the app, and both are worse than no arrow.
     */
    @Test
    fun onlyASubScreenOffersAWayBack() {
        assertNull(backLabelFor(Destination.Catalog))
        assertEquals("Back", backLabelFor(Destination.System))
    }
}
