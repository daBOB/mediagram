package setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetupInputTest {
    @Test
    fun anApiIdIsAPositiveNumber() {
        assertEquals(1234, apiIdOrNull("1234"))
        assertNull(apiIdOrNull("my application"))
        assertNull(apiIdOrNull("-1"))
        assertNull(apiIdOrNull(""))
    }

    @Test
    fun anApiHashIsThirtyTwoHexadecimalCharacters() {
        assertEquals(WELL_FORMED_HASH, apiHashOrNull(WELL_FORMED_HASH))
        assertNull(apiHashOrNull(WELL_FORMED_HASH.dropLast(1)), "a hash one character short is a bad paste")
        assertNull(apiHashOrNull(WELL_FORMED_HASH + "0"))
        assertNull(apiHashOrNull("z".repeat(32)))
    }

    @Test
    fun anApiHashPastedInCapitalsIsTheSameHash() {
        assertEquals(WELL_FORMED_HASH, apiHashOrNull(WELL_FORMED_HASH.uppercase()))
    }

    @Test
    fun surroundingWhitespaceFromAPasteIsTrimmedRatherThanRejected() {
        assertEquals(WELL_FORMED_HASH, apiHashOrNull("  $WELL_FORMED_HASH\n"))
        assertEquals(1234, apiIdOrNull(" 1234 "))
    }
}
