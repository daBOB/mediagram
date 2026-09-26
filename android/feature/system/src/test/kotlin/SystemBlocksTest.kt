package system

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Catalogue and This app blocks, in the words and order both surfaces set. */
class SystemBlocksTest {
    private val state =
        SystemUiState(
            origin = "channel",
            sets = 540,
            posters = 12,
            schema = 3,
            publishedAt = null,
            lastRefresh = null,
            heldBytes = 0,
            budgetBytes = 0,
            volumeLabel = "Internal storage",
            fellBack = false,
            fromCacheBytes = 0,
            fromUpstreamBytes = 0,
            fetches = 0,
            failedReads = 0,
            connected = true,
            versionName = "0.4.0",
            uptimeSeconds = 0,
        )

    @Test
    fun theCatalogueBlockNamesItsSourceWhatItHoldsAndItsSchema() {
        val rows = catalogueRows(state, now = 0)

        assertEquals(listOf("Source", "Holds", "Refresh", "Schema"), rows.map { it.first })
        assertEquals("the library's channel", rows.toMap()["Source"])
        assertEquals("540 playable sets, 12 posters", rows.toMap()["Holds"])
        assertEquals("v3, expected by this build", rows.toMap()["Schema"])
    }

    @Test
    fun aCatalogueFromNowhereSaysNothingIsInstalled() {
        assertEquals("nothing installed yet", catalogueRows(state.copy(origin = ""), now = 0).toMap()["Source"])
    }

    @Test
    fun thisAppIsItsVersionItsSessionAndItsUptime() {
        val rows = thisAppRows(state)

        assertEquals(listOf("Version", "Telegram", "Uptime"), rows.map { it.first })
        assertEquals("0.4.0", rows.toMap()["Version"])
        assertEquals("connected", rows.toMap()["Telegram"])
    }
}
