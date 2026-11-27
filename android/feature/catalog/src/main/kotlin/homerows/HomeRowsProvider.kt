package homerows

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
 * The TV home screen's window on this app, read-only, for apps signed with this app's key
 * ([PERMISSION]) — our saTV build:
 * - `content://com.mediagram.android.home/person`: who is watching here, one row or none;
 * - `/continue` and `/mylist`: that person's Continue and My List, as the library shows them;
 * - `/image/{set_id}/{poster|still}`: a title's picture, only while it is on those rows.
 *
 * Anything that goes wrong answers an empty cursor or no file.
 */
class HomeRowsProvider : ContentProvider() {
    /** A provider is created before `Application.onCreate`, so it reaches the singleton through Hilt by hand. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun source(): HomeRowsSource

        fun changes(): HomeRowsChanges
    }

    /** Looked up on the first ask; a test sets its own. */
    @Volatile
    internal var source: HomeRowsSource? = null

    /** Started on the first ask, so the home screen is told of every change after it; a test sets its own. */
    @Volatile
    internal var changes: HomeRowsChanges? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        guard()
        runCatching { changes().start() }
        val rows = runBlocking { runCatching { source().rows() } }.getOrDefault(HomeRows.None)
        return when (uri.pathSegments) {
            listOf("person") ->
                MatrixCursor(arrayOf(ID, NAME, KIDS)).apply {
                    rows.person?.let { addRow(arrayOf<Any>(it.id, it.name, if (it.kids) 1 else 0)) }
                }
            listOf("continue") ->
                MatrixCursor(CONTINUE_COLUMNS).apply {
                    rows.continuing.forEach {
                        addRow(arrayOf<Any?>(it.setId, it.kind, it.title, it.show, it.season, it.episode, it.positionSecs, it.durationSecs, it.updatedAt))
                    }
                }
            listOf("mylist") ->
                MatrixCursor(MY_LIST_COLUMNS).apply {
                    rows.myList.forEach { addRow(arrayOf<Any?>(it.setId, it.kind, it.title, it.show, it.year)) }
                }
            else -> null
        }
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        guard()
        if (mode != "r") throw FileNotFoundException("$uri is read-only")
        val path = uri.pathSegments
        val file =
            if (path.size == 3 && path[0] == "image") {
                runBlocking { runCatching { source().image(path[1], path[2]) } }.getOrNull()
            } else {
                null
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
    // declared without it still cannot hand anyone's rows out.
    private fun guard() = checkNotNull(context).enforceCallingPermission(PERMISSION, "reading the home rows needs $PERMISSION")

    private fun source(): HomeRowsSource =
        source ?: EntryPointAccessors
            .fromApplication(checkNotNull(context).applicationContext, Dependencies::class.java)
            .source()
            .also { source = it }

    private fun changes(): HomeRowsChanges =
        changes ?: EntryPointAccessors
            .fromApplication(checkNotNull(context).applicationContext, Dependencies::class.java)
            .changes()
            .also { changes = it }

    companion object {
        const val AUTHORITY = "com.mediagram.android.home"
        const val PERMISSION = "com.mediagram.android.permission.HOME_ROWS"
        const val ID = "id"
        const val NAME = "name"
        const val KIDS = "kids"
        private val CONTINUE_COLUMNS =
            arrayOf("set_id", "kind", "title", "show", "season", "episode", "position_secs", "duration_secs", "updated_at")
        private val MY_LIST_COLUMNS = arrayOf("set_id", "kind", "title", "show", "year")

        /** What saTV observes; told whenever the rows may have changed. */
        val CHANGES: Uri = Uri.parse("content://$AUTHORITY/")
    }
}
