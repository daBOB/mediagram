package ui.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a fetch says it did. "Done" over a library of 540 titles tells a
 * viewer nothing about the eight that did not work, and the eight are the
 * only part worth reading.
 *
 * Six counts is more than one line can carry as a list, so the line answers
 * the two questions a viewer actually has — did it find anything, and is
 * anything still missing — and leaves the rest unsaid when it is zero.
 */
class FetchReportSentenceTest {
    /** A run that filled both gaps says so in one line. */
    @Test
    fun aRunThatFoundBothSaysBoth() {
        assertEquals(
            "12 described, 9 posters fetched.",
            fetchSentence(
                detailsRecorded = 12,
                postersFetched = 9,
                detailsAlreadyKnown = 0,
                postersAlreadyHeld = 0,
                noProviderId = 0,
                failed = 0,
            ),
        )
    }

    /** Nothing new is the ordinary second run, and is not a failure. */
    @Test
    fun aSecondRunSaysEverythingWasAlreadyThere() {
        assertEquals(
            "Nothing new — 12 already described, 9 posters already held.",
            fetchSentence(
                detailsRecorded = 0,
                postersFetched = 0,
                detailsAlreadyKnown = 12,
                postersAlreadyHeld = 9,
                noProviderId = 0,
                failed = 0,
            ),
        )
    }

    /** A course has no provider entry, which is ordinary and worth saying once. */
    @Test
    fun titlesWithNoProviderEntryAreCountedNotBlamed() {
        val line =
            fetchSentence(
                detailsRecorded = 3,
                postersFetched = 3,
                detailsAlreadyKnown = 0,
                postersAlreadyHeld = 0,
                noProviderId = 1,
                failed = 0,
            )

        assertTrue(line.contains("1 title has no provider entry"))
        assertFalse(line.contains("failed"))
    }

    /** A failure is named only when there was one, and never blamed on the key. */
    @Test
    fun failuresAreNamedOnlyWhenTheyHappened() {
        val line =
            fetchSentence(
                detailsRecorded = 3,
                postersFetched = 3,
                detailsAlreadyKnown = 0,
                postersAlreadyHeld = 0,
                noProviderId = 0,
                failed = 2,
            )

        assertTrue(line.contains("2 could not be fetched"))
    }

    /** Half a run is half a sentence: a count of zero is left out rather than printed. */
    @Test
    fun aHalfThatFoundNothingIsNotNamed() {
        assertEquals(
            "12 described. 9 posters already held.",
            fetchSentence(
                detailsRecorded = 12,
                postersFetched = 0,
                detailsAlreadyKnown = 0,
                postersAlreadyHeld = 9,
                noProviderId = 0,
                failed = 0,
            ),
        )
    }

    /** One of anything is singular, because "1 posters" makes a careful app look careless. */
    @Test
    fun oneOfAnythingReadsAsOne() {
        val line =
            fetchSentence(
                detailsRecorded = 0,
                postersFetched = 1,
                detailsAlreadyKnown = 0,
                postersAlreadyHeld = 0,
                noProviderId = 0,
                failed = 0,
            )

        assertEquals("1 poster fetched.", line)
    }

    /** An empty library has nothing to report and does not pretend otherwise. */
    @Test
    fun aRunWithNothingToDoDoesNotPretendOtherwise() {
        assertEquals(
            "Nothing to fetch.",
            fetchSentence(
                detailsRecorded = 0,
                postersFetched = 0,
                detailsAlreadyKnown = 0,
                postersAlreadyHeld = 0,
                noProviderId = 0,
                failed = 0,
            ),
        )
    }
}
