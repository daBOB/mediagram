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
    fun aLibraryAddressCarriesItsScheme() {
        assertEquals("https://example.com/latest.json", packageUrlOrNull("https://example.com/latest.json"))
        assertNull(packageUrlOrNull("example.com/latest.json"))
        assertNull(packageUrlOrNull("https://"))
    }

    @Test
    fun aLibraryAddressIsStoredExactlyAsGivenBeyondTheTrim() {
        // The core resolves the package files relative to this address, so a
        // slash removed here would change which files it asks for.
        assertEquals("https://example.com/library/", packageUrlOrNull("  https://example.com/library/  "))
    }

    @Test
    fun aLibraryKeyIsThirtyTwoBytesOfBase64() {
        assertEquals(WELL_FORMED_KEY, packageKeyOrNull(WELL_FORMED_KEY))
        assertNull(packageKeyOrNull(WELL_FORMED_KEY.dropLast(1)))
        assertNull(packageKeyOrNull("not a key"))
    }

    @Test
    fun surroundingWhitespaceFromAPasteIsTrimmedRatherThanRejected() {
        assertEquals(WELL_FORMED_HASH, apiHashOrNull("  $WELL_FORMED_HASH\n"))
        assertEquals(WELL_FORMED_KEY, packageKeyOrNull(" $WELL_FORMED_KEY "))
        assertEquals(1234, apiIdOrNull(" 1234 "))
    }
}
