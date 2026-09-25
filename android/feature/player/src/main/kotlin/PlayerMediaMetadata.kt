package player

import android.net.Uri
import androidx.media3.common.MediaMetadata
import java.io.File
import model.MediaSet

/**
 * What the lock screen, the notification and a headset's own display show
 * for whatever is open — [titleLine] for the text, [MediaSet.posterPath]
 * (already resolved to a file on disk; see `CatalogRepository.posterPath`)
 * for the artwork. Blank title and no artwork with nothing open, the same
 * terms [titleLine] itself answers on.
 */
fun mediaMetadataFor(set: MediaSet?): MediaMetadata {
    val builder = MediaMetadata.Builder().setTitle(titleLine(set))
    set?.posterPath?.let { path -> builder.setArtworkUri(Uri.fromFile(File(path))) }
    return builder.build()
}
