package com.mediagram.spieltisch

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uniffi.spieltisch_core.MediaImageKind
import update.Lookup
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Every read of the media app goes through an unstable client: a stable reference to its provider kills
 * Spieltisch when the media app dies or updates itself mid-read, and with it a running night.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaProviderAccessTest {
    @get:Rule val folder = TemporaryFolder()

    private val resolver: ContentResolver = spyk(RuntimeEnvironment.getApplication().contentResolver)
    private val bytes = ByteArray(1_000) { it.toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun media() =
        mediaApp().apply {
            night = { android.database.MatrixCursor(arrayOf("kids_locked", "titles")).apply { addRow(arrayOf<Any>(0, "[]")) } }
            release = { android.database.MatrixCursor(arrayOf("version_name", "version_code", "bytes", "sha256")).apply { addRow(arrayOf<Any>("0.176.0", 176_000L, bytes.size.toLong(), sha256)) } }
            files = mapOf(
                "/release.apk" to folder.newFile().apply { writeBytes(bytes) },
                "/image/A/poster" to folder.newFile().apply { writeText("poster") },
            )
        }

    private fun assertNoStableReference() {
        verify(exactly = 0) { resolver.query(any<Uri>(), any(), any<String>(), any(), any<String>()) }
        verify(exactly = 0) { resolver.query(any<Uri>(), any(), any<String>(), any(), any<String>(), any<CancellationSignal>()) }
        verify(exactly = 0) { resolver.openFileDescriptor(any<Uri>(), any<String>()) }
        verify(exactly = 0) { resolver.openFileDescriptor(any<Uri>(), any<String>(), any<CancellationSignal>()) }
        verify(exactly = 0) { resolver.openInputStream(any<Uri>()) }
    }

    @Test
    fun theNightIsReadThroughAnUnstableClient() {
        media()

        assertEquals(false, runBlocking { MediaLibraryBridge(resolver).night() }.kidsLocked)

        verify { resolver.acquireUnstableContentProviderClient(MediaApp.AUTHORITY) }
        assertNoStableReference()
    }

    @Test
    fun anImageIsCopiedThroughAnUnstableClient() {
        media()

        assertEquals("poster", File(checkNotNull(MediaImageCache(resolver, File(folder.root, "c")).fetch("A", MediaImageKind.POSTER))).readText())

        verify { resolver.acquireUnstableContentProviderClient(MediaApp.AUTHORITY) }
        assertNoStableReference()
    }

    @Test
    fun theReleaseAndItsApkAreReadThroughAnUnstableClient() {
        media()
        val source = MediaReleaseSource(resolver)
        val release = checkNotNull(assertIs<Lookup.Found>(runBlocking { source.latest() }).release)
        runBlocking { release.download(File(folder.root, "176000.apk")) }

        verify(atLeast = 2) { resolver.acquireUnstableContentProviderClient(MediaApp.AUTHORITY) }
        assertNoStableReference()
    }
}
