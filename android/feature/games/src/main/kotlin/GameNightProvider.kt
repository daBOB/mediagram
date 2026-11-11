package games

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Spieltisch's window on this app's library, read-only:
 * - `content://com.mediagram.android.games/night`: one row, [KIDS_LOCKED] (1 or 0) and [TITLES] (JSON).
 * - `/image/{key}/{poster|still}` and `/textless/{key}`: that title's file, only while the active
 *   profile's night holds the title, so a key cannot fetch a picture the profile may not see.
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
    }

    /** Built on the first ask; a test sets its own. */
    @Volatile
    internal var export: GameNightExport? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        guard()
        if (uri.pathSegments != listOf("night")) return null
        val night = runBlocking { runCatching { export().night() } }.getOrDefault(ExportedNight.Locked)
        return MatrixCursor(arrayOf(KIDS_LOCKED, TITLES)).apply { addRow(arrayOf<Any>(if (night.kidsLocked) 1 else 0, night.titles)) }
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        guard()
        if (mode != "r") throw FileNotFoundException("$uri is read-only")
        val path = uri.pathSegments
        val file =
            runBlocking {
                runCatching {
                    when {
                        path.size == 3 && path[0] == "image" -> export().image(path[1], path[2])
                        path.size == 2 && path[0] == "textless" -> export().textless(path[1])
                        else -> null
                    }
                }.getOrNull()
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

    companion object {
        const val AUTHORITY = "com.mediagram.android.games"
        const val PERMISSION = "com.mediagram.android.permission.GAME_NIGHT"
        const val KIDS_LOCKED = "kids_locked"
        const val TITLES = "titles"
    }
}
