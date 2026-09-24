package catalog

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
}
