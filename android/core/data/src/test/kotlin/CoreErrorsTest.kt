package data

import uniffi.mediagram_core.CoreException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every sentence the core raises was written to be read by whoever has to
 * fix the thing it names. The generated exception's own `message` is the
 * bindings' field name and a value — `v1=...` — so unwrapping is what
 * stands between those sentences and a screen showing internals.
 */
class CoreErrorsTest {
    @Test
    fun aCoreFailureGivesUpTheSentenceItWasWrittenWith() {
        val said = "That channel has nothing pinned."

        assertEquals(said, CoreException.Library(said).coreSentence())
        assertEquals(said, CoreException.Network(said).coreSentence())
        assertEquals(said, CoreException.NotFound(said).coreSentence())
        assertEquals(said, CoreException.NotAuthorized(said).coreSentence())
        assertEquals(said, CoreException.Cipher(said).coreSentence())
        assertEquals(said, CoreException.Io(said).coreSentence())
    }

    @Test
    fun theUnwrappedSentenceCarriesNoneOfTheBindingsOwnShape() {
        assertEquals("nothing pinned", CoreException.Library("nothing pinned").coreSentence())
        assertEquals("v1=nothing pinned", CoreException.Library("nothing pinned").message)
    }

    /**
     * The useful half: a keystore that will not open or a file that will
     * not delete is not something a person fixes by choosing differently,
     * and this is how a caller tells the two apart.
     */
    @Test
    fun aFailureFromAnywhereElseIsNotOneOfTheseSentences() {
        assertNull(IOException("the file could not be deleted").coreSentence())
        assertNull(SecurityException("keystore unavailable").coreSentence())
    }
}
