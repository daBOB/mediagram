package settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The credential a viewer pastes from themoviedb.org. Stored like the api
 * hash, because it is a secret of the same kind: it identifies this
 * installation to a third party and is worth taking off a stolen phone.
 */
class TmdbSettingsTest {
    @Test
    fun nothingIsStoredUntilSomethingIsWritten() =
        runTest {
            assertNull(InMemoryTmdbSettings().read())
        }

    @Test
    fun aWrittenKeyComesBack() =
        runTest {
            val settings = InMemoryTmdbSettings()

            settings.write("0123456789abcdef0123456789abcdef")

            assertEquals("0123456789abcdef0123456789abcdef", settings.read())
        }

    /**
     * Blank is what an empty text field submits, and a blank key would fail
     * at TMDB with an error about the key rather than here with the truth,
     * which is that none was given.
     */
    @Test
    fun aBlankKeyIsNoKey() =
        runTest {
            val settings = InMemoryTmdbSettings()

            settings.write("   ")

            assertNull(settings.read())
        }

    @Test
    fun startingOverForgetsIt() =
        runTest {
            val settings = InMemoryTmdbSettings()
            settings.write("0123456789abcdef0123456789abcdef")

            settings.clear()

            assertNull(settings.read())
        }
}
