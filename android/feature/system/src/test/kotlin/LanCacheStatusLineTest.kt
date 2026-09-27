package system

import playback.CacheOccupancy
import kotlin.test.Test
import kotlin.test.assertEquals

private fun state(
    connection: LanCacheConnection,
    connectedHost: String? = null,
    heldBytes: Long? = null,
) = LanCacheUiState(
    enabled = true,
    hasToken = false,
    manualAddress = "",
    connection = connection,
    connectedHost = connectedHost,
    heldBytes = heldBytes,
    tokenRejected = false,
)

class LanCacheStatusLineTest {
    @Test
    fun theFourStatusWordsMatchTheirConnectionState() {
        assertEquals("Searching", lanCacheStatusLine(state(LanCacheConnection.SEARCHING)))
        assertEquals("Not found", lanCacheStatusLine(state(LanCacheConnection.NOT_FOUND)))
        assertEquals("Needs local network permission", lanCacheStatusLine(state(LanCacheConnection.NEEDS_PERMISSION)))
        assertEquals(
            "Connected to 10.0.0.5:7788, holding 1.0 MB",
            lanCacheStatusLine(
                state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = 1_048_576),
            ),
        )
    }

    @Test
    fun aConnectedServerWithNoHeldFigureOmitsTheComma() {
        assertEquals(
            "Connected to 10.0.0.5:7788",
            lanCacheStatusLine(state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = null)),
        )
    }

    @Test
    fun theStorageLedgerSplitsConnectionAndHoldingIntoTheirOwnRows() {
        val rows = lanCacheRows(state(LanCacheConnection.CONNECTED, connectedHost = "10.0.0.5:7788", heldBytes = 1_048_576))
        assertEquals(listOf("Status" to "Connected to 10.0.0.5:7788", "Holding" to "1.0 MB"), rows)
    }

    @Test
    fun theStorageLedgerOmitsHoldingWhenNothingIsKnown() {
        val rows = lanCacheRows(state(LanCacheConnection.SEARCHING))
        assertEquals(listOf("Status" to "Searching", "Holding" to null), rows)
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
