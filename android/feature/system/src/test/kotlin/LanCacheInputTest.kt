package system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanCacheInputTest {
    @Test
    fun aBlankAddressClearsTheOverride() {
        assertEquals(Result.success(null), normalizeManualAddress("  "))
    }

    /** Exactly what `avahi-browse` and the server's own status line print. */
    @Test
    fun aSchemeLessHostAndPortGetsHttpPrepended() {
        val result = normalizeManualAddress("192.168.1.5:7788")

        assertEquals("http://192.168.1.5:7788", result.getOrNull())
    }

    @Test
    fun aTrailingSlashIsRemoved() {
        val result = normalizeManualAddress("http://192.168.1.5:7788/")

        assertEquals("http://192.168.1.5:7788", result.getOrNull())
    }

    @Test
    fun anAddressThatAlreadyHasAnHttpsSchemeIsLeftAlone() {
        val result = normalizeManualAddress("https://cache.local:7788")

        assertEquals("https://cache.local:7788", result.getOrNull())
    }

    @Test
    fun somethingUnparseableIsRefusedWithASentenceRatherThanSaved() {
        val result = normalizeManualAddress("htp://bad-scheme")

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }

    @Test
    fun aWellFormedTokenRoundTrips() {
        val token = "a".repeat(64)

        assertEquals(token, normalizePairingToken(token).getOrNull())
    }

    @Test
    fun surroundingWhitespaceIsTrimmedFromTheToken() {
        val token = "b".repeat(64)

        assertEquals(token, normalizePairingToken("  $token\n").getOrNull())
    }

    @Test
    fun aTokenWithUppercaseHexIsRefused() {
        assertTrue(normalizePairingToken("A".repeat(64)).isFailure)
    }

    @Test
    fun aTokenOfTheWrongLengthIsRefused() {
        assertTrue(normalizePairingToken("a".repeat(63)).isFailure)
        assertTrue(normalizePairingToken("a".repeat(65)).isFailure)
    }

    @Test
    fun aTokenWithNonHexCharactersIsRefused() {
        assertTrue(normalizePairingToken("g".repeat(64)).isFailure)
    }

    @Test
    fun belowApiThirtySevenPermissionIsNeverNeeded() {
        assertTrue(localNetworkPermissionGranted(sdkInt = 36, granted = false))
        assertTrue(localNetworkPermissionGranted(sdkInt = 24, granted = false))
    }

    @Test
    fun fromApiThirtySevenOnPermissionMustActuallyBeGranted() {
        assertTrue(localNetworkPermissionGranted(sdkInt = 37, granted = true))
        assertTrue(!localNetworkPermissionGranted(sdkInt = 37, granted = false))
    }
}
