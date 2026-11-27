package com.mediagram.spieltisch

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import uniffi.spieltisch_core.ShelfGame
import uniffi.spieltisch_core.playableGames
import java.io.FileNotFoundException

/**
 * The TV home screen's Spieltisch row, read-only, for apps signed with this app's key ([PERMISSION]):
 * - `content://com.mediagram.spieltisch.games/games`: every game a room can start, in lobby order (`kind`, `name`);
 * - `/poster/{kind}`: that game's poster from this app's own art.
 * A card opens [MainActivity] with the `game` extra. Anything that goes wrong answers an empty cursor or no file.
 */
class SpieltischGamesProvider : ContentProvider() {
    /** The catalogue as the core lists it; a test sets its own. */
    @Volatile
    internal var games: () -> List<ShelfGame> = ::playableGames

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        guard()
        if (uri.pathSegments != listOf("games")) return null
        return MatrixCursor(arrayOf(KIND, NAME)).apply {
            runCatching(games).getOrDefault(emptyList()).forEach { addRow(arrayOf(it.kind, it.name)) }
        }
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String,
    ): AssetFileDescriptor {
        guard()
        val path = uri.pathSegments
        val kind = path.getOrNull(1)?.takeIf { path.size == 2 && path[0] == "poster" && mode == "r" }
        if (kind == null || runCatching(games).getOrDefault(emptyList()).none { it.kind == kind }) throw FileNotFoundException("$uri")
        return runCatching { checkNotNull(context).assets.openFd(posterAsset(kind)) }.getOrElse { throw FileNotFoundException("$uri") }
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

    // The manifest's android:permission refuses other apps first; checked again here as the media app's providers do.
    private fun guard() = checkNotNull(context).enforceCallingPermission(PERMISSION, "reading the games needs $PERMISSION")

    companion object {
        const val AUTHORITY = "com.mediagram.spieltisch.games"
        const val PERMISSION = "com.mediagram.android.permission.HOME_ROWS"
        const val KIND = "kind"
        const val NAME = "name"

        /** The intent extra a home-screen card opens [MainActivity] with: a game's kind. */
        const val EXTRA_GAME = "game"
    }
}

/** A game's poster in this app's art: the kind as the wire spells it (`tien_len`), with dashes as the files are named. */
internal fun posterAsset(kind: String) = "game-art/poster-${kind.replace('_', '-')}.webp"
