package com.mediagram.spieltisch

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uniffi.spieltisch_core.MediaImageKind
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A title's image is copied out of the media app once, and only what it hands out is copied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaImageCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val cache by lazy { MediaImageCache(RuntimeEnvironment.getApplication().contentResolver, File(folder.root, "copies")) }

    private fun mediaFile(
        name: String,
        text: String,
    ) = folder.newFile(name).apply { writeText(text) }

    @Test
    fun eachImageIsCopiedOnceAndServedFromTheCopy() {
        val app = mediaApp()
        app.files =
            mapOf(
                "/image/show:Dark/poster" to mediaFile("poster.jpg", "poster"),
                "/image/show:Dark/still" to mediaFile("still.jpg", "still"),
                "/textless/show:Dark" to mediaFile("textless.jpg", "textless"),
            )

        val poster = cache.fetch("show:Dark", MediaImageKind.POSTER)
        assertEquals("poster", File(checkNotNull(poster)).readText())
        assertEquals("still", File(checkNotNull(cache.fetch("show:Dark", MediaImageKind.STILL))).readText())
        assertEquals("textless", File(checkNotNull(cache.fetch("show:Dark", MediaImageKind.TEXTLESS))).readText())
        assertEquals(poster, cache.fetch("show:Dark", MediaImageKind.POSTER))

        assertEquals(listOf("/image/show:Dark/poster", "/image/show:Dark/still", "/textless/show:Dark"), app.opened)
        assertEquals(File(folder.root, "copies"), File(poster).parentFile, "a key cannot name a path outside the cache")
    }

    @Test
    fun anImageTheMediaAppDoesNotHandOutIsNone() {
        mediaApp()

        assertNull(cache.fetch("A", MediaImageKind.POSTER))
        assertEquals(emptyList(), File(folder.root, "copies").list().orEmpty().toList(), "no half copy is left behind")
    }

    @Test
    fun noMediaAppIsNoImage() {
        assertNull(cache.fetch("A", MediaImageKind.STILL))
    }

    @Test
    fun aLongKeyStillGetsACopy() {
        // Encoded, each of these letters was six characters: past the 255 bytes a file name may have.
        val key = "show:" + "Ä".repeat(120)
        mediaApp().files = mapOf("/image/$key/poster" to mediaFile("long.jpg", "long"))

        assertEquals("long", File(checkNotNull(cache.fetch(key, MediaImageKind.POSTER))).readText())
    }

    @Test
    fun aKeyWithASlashStaysInTheCache() {
        mediaApp().files = mapOf("/image/../../x/poster" to mediaFile("x.jpg", "x"))

        val copy = File(checkNotNull(cache.fetch("../../x", MediaImageKind.POSTER)))
        assertEquals(File(folder.root, "copies"), copy.parentFile)
    }
}
