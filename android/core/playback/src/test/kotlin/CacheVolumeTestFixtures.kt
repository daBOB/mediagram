@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.assertTrue

/** A generous, fixed free-space figure so a test's expected [CacheOccupancy.capBytes] never depends on the host's real disk. */
internal const val FAKE_FREE_BYTES = 100L * 1024 * 1024 * 1024

internal fun internalVolume(context: Context, freeBytes: Long = FAKE_FREE_BYTES) =
    CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File(context.cacheDir, "mlib"), freeBytes, removable = false)

/** A volume rooted at a fresh temp directory, standing in for an SD card or USB drive under Robolectric. */
internal fun tempVolume(id: String, freeBytes: Long = FAKE_FREE_BYTES): CacheVolume {
    val root = Files.createTempDirectory("cache-volume-$id-").toFile()
    return CacheVolume(id, id, File(root, "mlib"), freeBytes, removable = true)
}

internal fun assertUnder(file: File, dir: File) =
    assertTrue(file.absolutePath.startsWith(dir.absolutePath + File.separator), "$file is not under $dir")

/** Writes and commits a real [MIN_CACHE_BYTES]-sized span under [key], returning the file it landed in. */
internal fun commitSpan(
    cache: SimpleCache,
    key: String,
): File {
    val hole = cache.startReadWrite(key, 0, MIN_CACHE_BYTES)
    try {
        val file = cache.startFile(key, 0, MIN_CACHE_BYTES)
        // Sparse files exercise real persisted spans at the production budget
        // floor without allocating or writing hundreds of MiB of fixture data.
        RandomAccessFile(file, "rw").use { payload ->
            payload.setLength(MIN_CACHE_BYTES)
            payload.writeByte(0x42)
            payload.seek(MIN_CACHE_BYTES - 1)
            payload.writeByte(0x7f)
        }
        cache.commitFile(file, MIN_CACHE_BYTES)
        assertTrue(cache.isCached(key, 0, MIN_CACHE_BYTES))
        return file
    } finally {
        cache.releaseHoleSpan(hole)
    }
}
