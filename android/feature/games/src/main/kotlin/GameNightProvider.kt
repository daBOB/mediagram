package games

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Spieltisch's window on this app's library, read-only:
 * - `content://com.mediagram.android.games/night`: one row, [KIDS_LOCKED] (1 or 0) and [TITLES] (JSON).
 * - `/image/{key}/{poster|still}` and `/textless/{key}`: that title's file, only while the active
 *   profile's night holds the title, so a key cannot fetch a picture the profile may not see.
 * - `/release`: one row ([VERSION_NAME], [VERSION_CODE], [BYTES], [SHA256]) for Spieltisch's newest
 *   release, no row when there is none, no cursor when finding out failed; and `/release.apk` its APK: the call waits while the core downloads it, and a caller that
 *   cancels ends the wait. Waiting keeps this process alive for the whole download, which handing back
 *   "not ready yet" would not.
 *
 * Only apps signed with this app's key hold [PERMISSION]. Anything that goes wrong answers locked,
 * with no titles, or no file.
 */
class GameNightProvider : ContentProvider() {
    /** A provider is created before `Application.onCreate`, so it reaches the singletons through Hilt by hand. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun export(): GameNightExport

        fun releases(): SpieltischReleaseExport
    }

    /** Built on the first ask; a test sets its own. */
    @Volatile
    internal var export: GameNightExport? = null

    @Volatile
    internal var releases: SpieltischReleaseExport? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        guard()
        return when (uri.pathSegments) {
            listOf("night") -> {
                val night = runBlocking { runCatching { export().night() } }.getOrDefault(ExportedNight.Locked)
                MatrixCursor(arrayOf(KIDS_LOCKED, TITLES)).apply { addRow(arrayOf<Any>(if (night.kidsLocked) 1 else 0, night.titles)) }
            }
            // No row says "no release" (no library chosen, nothing pinned); no cursor at all says the lookup failed,
            // so a caller never mistakes a failure for nothing pinned and drops a release it already holds.
            listOf("release") -> {
                val release = runBlocking { runCatching { releases().latest() } }.getOrElse { return null }
                MatrixCursor(arrayOf(VERSION_NAME, VERSION_CODE, BYTES, SHA256)).apply {
                    release?.let { addRow(arrayOf<Any>(it.versionName, it.versionCode, it.bytes.toLong(), it.sha256)) }
                }
            }
            else -> null
        }
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = openFile(uri, mode, null)

    override fun openFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        guard()
        if (mode != "r") throw FileNotFoundException("$uri is read-only")
        val path = uri.pathSegments
        val file =
            try {
                runBlocking {
                    // The caller giving up ends the wait, and with it the download.
                    signal?.setOnCancelListener { cancel() }
                    runCatching {
                        when {
                            path.size == 3 && path[0] == "image" -> export().image(path[1], path[2])
                            path.size == 2 && path[0] == "textless" -> export().textless(path[1])
                            path == listOf("release.apk") -> releases().apk()
                            else -> null
                        }
                    }.getOrNull()
                }
            } catch (e: CancellationException) {
                throw OperationCanceledException(e.message)
            } ?: throw FileNotFoundException("$uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")

    // The manifest's android:permission refuses other apps first; checked again here, so an entry
    // declared without it still cannot hand the library out.
    private fun guard() = checkNotNull(context).enforceCallingPermission(PERMISSION, "reading the game night needs $PERMISSION")

    private fun export(): GameNightExport =
        export ?: EntryPointAccessors
            .fromApplication(checkNotNull(context).applicationContext, Dependencies::class.java)
            .export()
            .also { export = it }

    private fun releases(): SpieltischReleaseExport =
        releases ?: EntryPointAccessors
            .fromApplication(checkNotNull(context).applicationContext, Dependencies::class.java)
            .releases()
            .also { releases = it }

    companion object {
        const val AUTHORITY = "com.mediagram.android.games"
        const val PERMISSION = "com.mediagram.android.permission.GAME_NIGHT"
        const val KIDS_LOCKED = "kids_locked"
        const val TITLES = "titles"
        const val VERSION_NAME = "version_name"
        const val VERSION_CODE = "version_code"
        const val BYTES = "bytes"
        const val SHA256 = "sha256"
    }
}
