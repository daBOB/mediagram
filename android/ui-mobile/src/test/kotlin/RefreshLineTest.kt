package ui

import data.RefreshOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How old the catalogue is, and what the last attempt to replace it did.
 * Mirrors `refreshLine` in the web player's `status-lines.js`, over the same
 * day-granularity wording its `catalogueAge` uses.
 */
class RefreshLineTest {

    private val now = 1_758_900_000_000L
    private val threeDaysAgo = now - 3L * 86_400_000L

    @Test
    fun ageAloneIsWhatThereIsToSayBeforeAnythingIsAsked() {
        assertEquals("published 3 days ago", refreshLine(threeDaysAgo, null, now))
    }

    @Test
    fun aRefreshThatFoundSomethingSaysSoBesideTheAge() {
        assertEquals(
            "published 3 days ago · refreshed just now",
            refreshLine(threeDaysAgo, RefreshOutcome.Updated, now),
        )
    }

    /** Finding nothing new is the ordinary outcome, and not a failure. */
    @Test
    fun aRefreshThatFoundNothingSaysTheLibraryIsCurrent() {
        assertEquals(
            "published 3 days ago · already current",
            refreshLine(threeDaysAgo, RefreshOutcome.AlreadyCurrent, now),
        )
    }

    /**
     * The one reading on this screen that is a warning. A catalogue that
     * could not be replaced looks exactly like a current one, and nothing
     * else here would say otherwise.
     */
    @Test
    fun aRefusalSaysWhyAndWhatIsStillBeingServed() {
        assertEquals(
            "refresh refused — the channel could not be reached, still serving the one published 3 days ago",
            refreshLine(threeDaysAgo, RefreshOutcome.Refused("the channel could not be reached"), now),
        )
    }

    /** A clock that disagrees with the publisher's is likelier than a catalogue from the future. */
    @Test
    fun aCatalogueFromTheFutureIsNotDescribedAsSuch() {
        assertEquals("published just now", refreshLine(now + 86_400_000L, null, now))
        assertEquals("published today", refreshLine(now, null, now))
        assertEquals("published yesterday", refreshLine(now - 86_400_000L, null, now))
    }

    /** Weeks past a fortnight, months past two — the web's thresholds, not new ones. */
    @Test
    fun anOlderCatalogueIsSaidInCoarserUnits() {
        assertEquals("published 2 weeks ago", refreshLine(now - 20L * 86_400_000L, null, now))
        assertEquals("published 3 months ago", refreshLine(now - 100L * 86_400_000L, null, now))
    }

    /**
     * The exact days the unit changes on. Sampling 20 and 100 days above
     * would pass just as well with either threshold a day out, and a
     * threshold that has drifted from the web's is the kind of difference
     * nobody notices until the two surfaces are read side by side.
     */
    @Test
    fun theUnitChangesOnTheSameDaysTheWebChangesItOn() {
        assertEquals("published 13 days ago", refreshLine(now - 13L * 86_400_000L, null, now))
        assertEquals("published 2 weeks ago", refreshLine(now - 14L * 86_400_000L, null, now))
        assertEquals("published 8 weeks ago", refreshLine(now - 59L * 86_400_000L, null, now))
        assertEquals("published 2 months ago", refreshLine(now - 60L * 86_400_000L, null, now))
    }

    /** Nothing installed is not a date; the row is left out rather than shown blank. */
    @Test
    fun aCatalogueWithNoPushTimeHasNoRow() {
        assertNull(refreshLine(null, null, now))
    }

    /**
     * A refusal on a device with nothing installed still has to be said —
     * there is no age to still be serving, but there is a reason the
     * library is not there.
     */
    @Test
    fun aRefusalWithNothingInstalledStillSaysWhy() {
        assertEquals(
            "refresh refused — no library has been chosen",
            refreshLine(null, RefreshOutcome.Refused("no library has been chosen"), now),
        )
    }
}
