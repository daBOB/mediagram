package com.mediagram.spieltisch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** The media app as a test sets it up: [night] answers the night row, [files] the images by URI path. */
class FakeMediaApp : ContentProvider() {
    var night: () -> Cursor? = { null }
    var files: Map<String, File> = emptyMap()
    val opened = mutableListOf<String>()

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = night()

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        opened += uri.path.orEmpty()
        val file = files[uri.path] ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}

/** Installs a [FakeMediaApp] under the media app's authority. */
fun mediaApp(): FakeMediaApp = Robolectric.buildContentProvider(FakeMediaApp::class.java).create(MediaApp.AUTHORITY).get()

/** What Spieltisch makes of the media app's answer, and of no answer: never an unlocked night by default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaLibraryBridgeTest {
    private val resolver = RuntimeEnvironment.getApplication().contentResolver

    private fun row(
        kidsLocked: Any?,
        titles: Any?,
    ) = MatrixCursor(arrayOf("kids_locked", "titles")).apply { addRow(arrayOf(kidsLocked, titles)) }

    private fun night() = runBlocking { MediaLibraryBridge(resolver, limit = 200.milliseconds).night() }

    /** Spelled out rather than [MediaNight.Unanswered], so a looser constant cannot pass for it. */
    private val locked = MediaNight(kidsLocked = true, titles = "[]", answered = false)

    @Test
    fun aGrownUpsAnswerIsAnUnlockedNightWithItsTitles() {
        mediaApp().night = { row(0, """[{"key":"A"}]""") }

        assertEquals(MediaNight(kidsLocked = false, titles = """[{"key":"A"}]"""), night())
    }

    @Test
    fun aKidsProfilesAnswerIsLocked() {
        mediaApp().night = { row(1, "[]") }

        assertEquals(MediaNight(kidsLocked = true, titles = "[]"), night())
    }

    @Test
    fun noMediaAppIsLockedWithNoFilms() {
        assertEquals(locked, night())
    }

    @Test
    fun aMediaAppThatDoesNotAnswerInTimeIsLockedWithNoFilms() {
        val held = CountDownLatch(1)
        // An unlocked answer, too late: a bridge that waited for it would return it.
        mediaApp().night = {
            held.await(2, TimeUnit.SECONDS)
            row(0, "[]")
        }
        try {
            val began = System.nanoTime()
            assertEquals(locked, night())
            assertTrue((System.nanoTime() - began).nanoseconds < 1.seconds, "the limit, not the late answer, ends the wait")
        } finally {
            held.countDown()
        }
    }

    @Test
    fun anAnswerThatIsNotANightIsLockedWithNoFilms() {
        val app = mediaApp()
        val notNights =
            listOf<() -> Cursor?>(
                { row(0, "not json") },
                { row(0, """{"key":"A"}""") },
                { row(0, null) },
                { MatrixCursor(arrayOf("titles")).apply { addRow(arrayOf("[]")) } },
                { MatrixCursor(arrayOf("kids_locked", "titles")) },
                { throw SecurityException("no permission") },
            )
        for (answer in notNights) {
            app.night = answer
            assertEquals(locked, night())
        }
    }

    @Test
    fun aWarmUpAsksOnceOffTheCallersThreadAndKeepsNothing() {
        val asked = CountDownLatch(1)
        var askedOn: Thread? = null
        mediaApp().night = {
            askedOn = Thread.currentThread()
            asked.countDown()
            row(0, "[]")
        }

        MediaLibraryBridge(resolver).warmUp()

        assertTrue(asked.await(2, TimeUnit.SECONDS), "the media app was asked")
        assertTrue(askedOn != Thread.currentThread(), "not on the caller's thread")
    }

    @Test
    fun onlyAnExplicitZeroUnlocks() {
        val app = mediaApp()
        for (locked in listOf(null, "0", 2, 1.5)) {
            app.night = { row(locked, "[]") }
            assertEquals(true, night().kidsLocked, "kids_locked = $locked")
        }
    }
}
