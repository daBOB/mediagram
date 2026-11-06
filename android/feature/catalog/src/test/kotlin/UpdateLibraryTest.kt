package catalog

import uniffi.mediagram_core.FetchProgress
import uniffi.mediagram_core.FetchStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateLibraryTest {
    @Test
    fun readingAndArtworkHaveTheirExistingMenuMessages() {
        assertEquals("Reading the channel…", updateDisabledReason(CatalogUiState.Loading, false))
        assertEquals("Reading the channel…", updateDisabledReason(CatalogUiState.Ready(emptyList(), refreshing = true), false))
        assertEquals("Fetching details and artwork…", updateDisabledReason(CatalogUiState.Ready(emptyList()), true))
    }

    @Test
    fun aSettledEmptyOrFailedCatalogCanBeUpdatedAgain() {
        assertNull(updateDisabledReason(CatalogUiState.Empty, false))
        assertNull(updateDisabledReason(CatalogUiState.Failed("offline"), false))
        assertNull(updateDisabledReason(CatalogUiState.Ready(emptyList()), false))
    }

    @Test
    fun aRunThatHasBegunCountsItsStepAndEstimatesFromItsOwnPace() {
        // 200 lookups in 60 s leaves 440 at the same pace: 132 s, said as 3 minutes.
        val looking = FetchProgress(FetchStep.LOOKING_UP, 200u, 640u, 60_000uL)
        assertEquals(
            "Looking up titles · 200 of 640 · about 3 min left",
            updateDisabledReason(CatalogUiState.Ready(emptyList()), true, looking),
        )
        assertEquals(200f / 640f, fetchFraction(looking))
    }

    @Test
    fun noEstimateUntilTheStepHasAPace() {
        assertEquals("Downloading artwork · 3 of 900", fetchProgressSentence(FetchProgress(FetchStep.DOWNLOADING, 3u, 900u, 9_000uL)))
        assertEquals("Saving descriptions · 40 of 640", fetchProgressSentence(FetchProgress(FetchStep.DESCRIBING, 40u, 640u, 400uL)))
    }

    @Test
    fun theLastMinuteIsNotCountedDown() {
        assertEquals("less than a minute left", fetchTimeLeft(FetchProgress(FetchStep.DOWNLOADING, 600u, 640u, 300_000uL)))
    }

    @Test
    fun aStepWithNothingToDoDrawsNoBar() {
        assertNull(fetchFraction(FetchProgress(FetchStep.DOWNLOADING, 0u, 0u, 0uL)))
    }
}
