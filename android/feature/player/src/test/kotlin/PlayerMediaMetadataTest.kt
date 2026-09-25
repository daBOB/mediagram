package player

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers [mediaMetadataFor] — what `PlaybackService`'s `MediaSession`
 * publishes to the lock screen, the notification and a headset's own
 * display. Runs under Robolectric: a resolved poster path builds a real
 * `android.net.Uri` (via `Uri.fromFile`), the same reason
 * `DefaultPlayerHandleTest` needs it for `setUri`.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerMediaMetadataTest {

    @Test
    fun nothingOpenIsBlankWithNoArtwork() {
        val metadata = mediaMetadataFor(null)

        assertEquals("", metadata.title?.toString() ?: "")
        assertNull(metadata.artworkUri)
    }

    @Test
    fun aResolvedSetCarriesItsTitleLineAndPoster() {
        val set = fakeMediaSet(setId = "01FILM", title = "Blade Runner 2049", posterPath = "/cache/posters/01FILM.jpg")

        val metadata = mediaMetadataFor(set)

        assertEquals("Blade Runner 2049", metadata.title?.toString())
        assertEquals("file:///cache/posters/01FILM.jpg", metadata.artworkUri?.toString())
    }

    @Test
    fun aSetWithNoResolvedPosterHasNoArtwork() {
        val set = fakeMediaSet(setId = "01FILM", title = "Blade Runner 2049", posterPath = null)

        val metadata = mediaMetadataFor(set)

        assertNull(metadata.artworkUri)
    }
}
