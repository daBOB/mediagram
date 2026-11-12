package com.mediagram.spieltisch

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.spieltisch_core.MediaImageKind
import uniffi.spieltisch_core.MediaImages
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

const val ARTICLE_LIST = """{"version":1,"fetched":"2026-11-12T19:14:54.484Z","articles":[{"asin":"B0GF25D3LF","name":"Halter","title":"Halter","cents":899,"reviews":21}]}"""
private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

/** Robolectric for `android.util.Log`, which the fetch writes its outcome to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheServerArticlesTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = TestCacheServer()

    @AfterTest fun stop() = server.close()

    @Test
    fun theListComesFromTheServerTheMediaAppNamed() =
        runTest {
            server.files["/v1/game/preisfrage/articles.json"] = ARTICLE_LIST.toByteArray()

            assertEquals(ARTICLE_LIST, CacheServerArticles("${server.url}/").list())
        }

    @Test
    fun noServerNoListOrASlowServerMeansNoList() =
        runTest {
            assertEquals("", CacheServerArticles("").list())
            assertEquals("", CacheServerArticles(server.url).list(), "nobody has fetched articles yet")

            server.files["/v1/game/preisfrage/articles.json"] = ARTICLE_LIST.toByteArray()
            server.delayMs = 1_000
            assertEquals("", CacheServerArticles(server.url, timeoutMs = 200).list())
        }

    @Test
    fun aPhotoIsFetchedOnceAndKept() {
        server.files["/v1/game/preisfrage/photos-B0GF25D3LF.jpg"] = JPEG
        val articles = CacheServerArticles(server.url)

        val path = articles.photo("B0GF25D3LF", folder.root)
        assertContentEquals(JPEG, File(checkNotNull(path)).readBytes())
        assertEquals(path, articles.photo("B0GF25D3LF", folder.root))
        assertEquals(1, server.asked.count { it.endsWith(".jpg") }, "the second ask is answered from the copy")
    }

    @Test
    fun noPhotoOrAnAsinThatIsNotOneIsNone() {
        val articles = CacheServerArticles(server.url)

        assertNull(articles.photo("B0GF25D3LF", folder.root))
        assertNull(articles.photo("../../x", folder.root))
        assertNull(CacheServerArticles("").photo("B0GF25D3LF", folder.root))
        assertTrue(server.asked.none { ".." in it }, "a key that is not an ASIN never reaches the server")
        assertTrue(folder.root.listFiles().orEmpty().isEmpty(), "nothing half-written is left behind")
    }

    @Test
    fun articlePhotosComeFromTheCacheServerAndEveryOtherImageFromTheMediaApp() {
        server.files["/v1/game/preisfrage/photos-B0GF25D3LF.jpg"] = JPEG
        val media =
            object : MediaImages {
                override fun fetch(
                    key: String,
                    kind: MediaImageKind,
                ) = "media:$key:$kind"
            }
        val images = NightImages(media, CacheServerArticles(server.url), folder.root)

        assertEquals("media:A:POSTER", images.fetch("A", MediaImageKind.POSTER))
        assertContentEquals(JPEG, File(checkNotNull(images.fetch("B0GF25D3LF", MediaImageKind.ARTICLE))).readBytes())
    }
}
