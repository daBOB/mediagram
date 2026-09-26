package system

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
}
