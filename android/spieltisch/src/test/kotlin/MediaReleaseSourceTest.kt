package com.mediagram.spieltisch

import android.database.Cursor
import android.database.MatrixCursor
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import update.Lookup
import update.Release
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

/** The APK the media app hands over, and the row announcing it: Spieltisch installs only what matches the row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaReleaseSourceTest {
    @get:Rule val folder = TemporaryFolder()

    private val resolver = RuntimeEnvironment.getApplication().contentResolver
    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** A media app announcing [bytes] as 0.176.0, unless the row is told otherwise. */
    private fun announcing(
        size: Long = bytes.size.toLong(),
        sha: String = sha256,
        served: ByteArray = bytes,
    ): FakeMediaApp =
        mediaApp().apply {
            release = { row(size = size, sha = sha) }
            files = mapOf("/${MediaApp.RELEASE_APK}" to folder.newFile().apply { writeBytes(served) })
        }

    private fun row(
        size: Any? = bytes.size.toLong(),
        sha: Any? = sha256,
        name: Any? = "0.176.0",
        code: Any? = 176_000L,
    ): Cursor = MatrixCursor(arrayOf("version_name", "version_code", "bytes", "sha256")).apply { addRow(arrayOf(name, code, size, sha)) }

    private fun latest(source: MediaReleaseSource = MediaReleaseSource(resolver)): Lookup = runBlocking { source.latest() }

    private fun found(): Release = checkNotNull(assertIs<Lookup.Found>(latest()).release)

    @Test
    fun theAnnouncedReleaseIsCopiedWhole() {
        announcing()
        val release = found()
        val apk = File(folder.root, "176000.apk")

        runBlocking { release.download(apk) }

        assertEquals("0.176.0" to 176_000L, release.versionName to release.versionCode)
        assertEquals(bytes.toList(), apk.readBytes().toList())
        assertEquals(listOf(apk.name), folder.root.list().orEmpty().filter { it.endsWith(".apk") || it.endsWith(".part") })
    }

    @Test
    fun noRowIsNoReleaseToOffer() {
        mediaApp().release = { MatrixCursor(arrayOf("version_name", "version_code", "bytes", "sha256")) }

        assertNull(assertIs<Lookup.Found>(latest()).release)
    }

    @Test
    fun aCopyThatIsNotTheAnnouncedSizeOrHashIsDeletedAndNeverBecomesTheApk() {
        val tampered = bytes.copyOf().also { it[10] = (it[10] + 1).toByte() }
        val wrong =
            listOf(
                { announcing(size = bytes.size + 1L) },
                { announcing(served = bytes.copyOf(bytes.size - 1)) },
                { announcing(served = tampered) },
                { announcing(sha = "0".repeat(64)) },
            )
        for (announce in wrong) {
            val media = announce()
            val release = found()
            val apk = File(folder.root, "176000.apk")

            assertFailsWith<IOException> { runBlocking { release.download(apk) } }

            assertEquals(false, apk.exists(), "a download that fails its row is not the update")
            assertEquals(emptyList(), folder.root.list().orEmpty().filter { it.endsWith(".part") }, "and nothing of it is left")
            assertEquals(listOf("/release.apk"), media.opened.distinct())
        }
    }

    @Test
    fun noMediaAppOrOneThatDoesNotAnswerIsUnavailableNotAnError() {
        assertEquals(Lookup.Unavailable("the media app did not answer"), latest())

        val app = mediaApp()
        val notReleases =
            listOf<() -> Cursor?>(
                { null },
                { throw SecurityException("no permission") },
                { row(sha = "not a hash") },
                { row(size = 0L) },
                { row(code = 0L) },
                { row(name = "") },
                { MatrixCursor(arrayOf("version_name")).apply { addRow(arrayOf("0.176.0")) } },
            )
        for (answer in notReleases) {
            app.release = answer
            assertEquals(Lookup.Unavailable("the media app did not answer"), latest())
        }
    }

    @Test
    fun aMediaAppThatAnswersTooLateIsUnavailable() {
        mediaApp().release = {
            Thread.sleep(1_000)
            row()
        }

        assertEquals(Lookup.Unavailable("the media app did not answer"), latest(MediaReleaseSource(resolver, limit = 100.milliseconds)))
    }
}
