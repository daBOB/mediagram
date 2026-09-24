package data

import uniffi.mediagram_core.Core
import uniffi.mediagram_core.NoHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Generated bindings explicitly support NoHandle stand-ins for raw native IO. */
private class ClosingCore : Core(NoHandle) {
    val calls = mutableListOf<String>()
    var refuseRetirement = false

    override fun retireLocalState() {
        check(!uniffiIsDestroyed)
        calls += "retire"
        if (refuseRetirement) error("retirement refused")
    }

    override fun close() {
        calls += "close"
        super.close()
    }
}

class DefaultCoreClientCloseTest {
    @Test
    fun closingRetiresStateBeforeReleasingTheNativeHandleAndCanBeRepeated() {
        val native = ClosingCore()
        val client = DefaultCoreClient(native)
        client.close()
        client.close()
        assertEquals(listOf("retire", "close"), native.calls)
        assertTrue(native.uniffiIsDestroyed)
    }

    @Test
    fun aFailedRetirementKeepsTheNativeHandleAvailableForRetry() {
        val native = ClosingCore().apply { refuseRetirement = true }
        val client = DefaultCoreClient(native)
        assertFailsWith<IllegalStateException> { client.close() }
        assertFalse(native.uniffiIsDestroyed)
        assertEquals(listOf("retire"), native.calls)
        native.refuseRetirement = false
        client.close()
        assertEquals(listOf("retire", "retire", "close"), native.calls)
        assertTrue(native.uniffiIsDestroyed)
    }
}
