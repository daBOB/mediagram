package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a fetch says it did. "Done" over a library of 540 titles tells a
 * viewer nothing about the eight that did not work, and the eight are the
 * only part worth reading.
 */
class PosterFetchReportTest {

    @Test
    fun aRunThatFetchedEverythingSaysSo() {
        assertEquals("Fetched 9 posters.", posterReportLine(fetched = 9, alreadyHeld = 0, noProviderId = 0, failed = 0))
    }

    @Test
    fun artworkAlreadyHeldIsNotReportedAsFetched() {
        assertEquals(
            "Fetched 2 posters. 7 were already held.",
            posterReportLine(fetched = 2, alreadyHeld = 7, noProviderId = 0, failed = 0),
        )
    }

    /** A course has no provider entry; saying so stops it reading as a fault. */
    @Test
    fun titlesWithNoProviderEntryAreExplained() {
        assertEquals(
            "Fetched 2 posters. 171 titles have no provider entry.",
            posterReportLine(fetched = 2, alreadyHeld = 0, noProviderId = 171, failed = 0),
        )
    }

    @Test
    fun failuresAreNamedLast() {
        assertEquals(
            "Fetched 2 posters. 3 could not be fetched.",
            posterReportLine(fetched = 2, alreadyHeld = 0, noProviderId = 0, failed = 3),
        )
    }

    @Test
    fun aRunWithNothingToDoDoesNotPretendOtherwise() {
        assertEquals("No artwork to fetch.", posterReportLine(fetched = 0, alreadyHeld = 0, noProviderId = 0, failed = 0))
    }
}
