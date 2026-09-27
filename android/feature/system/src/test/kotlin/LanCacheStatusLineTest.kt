package system

import playback.CacheOccupancy
import kotlin.test.Test
import kotlin.test.assertEquals

private fun state(
    connection: LanCacheConnection,
    connectedHost: String? = null,
    heldBytes: Long? = null,
    budgetBytes: Long? = null,
    chunks: Long? = null,
) = LanCacheUiState(
    enabled = true,
    hasToken = false,
    manualAddress = "",
    connection = connection,
    connectedHost = connectedHost,
    heldBytes = heldBytes,
    tokenRejected = false,
    budgetBytes = budgetBytes,
    chunks = chunks,
)

class LanCacheStatusLineTest {
    @Test
    fun theFourStatusWordsMatchTheirConnectionState() {
        assertEquals("Searching", lanCacheRows(state(LanCacheConnection.SEARCHING))[0].second)
        assertEquals("Not found", lanCacheRows(state(LanCacheConnection.NOT_FOUND))[0].second)
        assertEquals("Needs local network permission", lanCacheRows(state(LanCacheConnection.NEEDS_PERMISSION))[0].second)
        assertEquals(
            "Connected to 10.0.0.5:7788",
            lanCacheRows(state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = 1_048_576))[0].second,
        )
    }

    @Test
    fun theStorageLedgerSplitsConnectionAndHoldingIntoTheirOwnRows() {
        val rows = lanCacheRows(state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = 1_048_576))
        assertEquals(listOf("Status" to "Connected to 10.0.0.5:7788", "Holding" to "1.0 MB", "Chunks" to null), rows)
    }

    @Test
    fun theStorageLedgerShowsTheServersOwnBudgetAndChunkCount() {
        val rows =
            lanCacheRows(
                state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = 1_048_576, budgetBytes = 10_485_760, chunks = 1),
            )
        assertEquals(listOf("Status" to "Connected to 10.0.0.5:7788", "Holding" to "1.0 MB of 10 MB (10%)", "Chunks" to "1"), rows)
    }

    @Test
    fun theStorageLedgerOmitsHoldingWhenNothingIsKnown() {
        val rows = lanCacheRows(state(LanCacheConnection.SEARCHING))
        assertEquals(listOf("Status" to "Searching", "Holding" to null, "Chunks" to null), rows)
    }

    private fun occupancy(
        held: Long,
        budget: Long,
    ) = CacheOccupancy(heldBytes = held, budgetBytes = budget, volumeLabel = "Internal storage", fellBack = false, capBytes = budget)

    @Test
    fun theIndexRowStripsTheRepeatedUnitAndAddsTheHomeServerWhenConnected() {
        val (text, held) = storageStatus(occupancy(11_811_160_064, 34_359_738_368), state(LanCacheConnection.CONNECTED))
        assertEquals("11 of 32 GB · home cache connected", text)
        assertEquals(true, held)
    }

    @Test
    fun theIndexRowDropsTheHomeServerHalfWhenNotConnected() {
        val (text, held) = storageStatus(occupancy(11_811_160_064, 34_359_738_368), state(LanCacheConnection.NOT_FOUND))
        assertEquals("11 of 32 GB", text)
        assertEquals(false, held)
    }

    @Test
    fun theIndexRowReadsBeforeTheFirstOccupancyArrives() {
        assertEquals("Reading…" to false, storageStatus(null, null))
    }
}
