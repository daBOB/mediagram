package playback

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/** The directory name the cache uses on whichever volume it lands. */
internal const val CACHE_DIR_NAME = "mlib"

/** The id recorded for the app's own internal cache directory. */
const val INTERNAL_VOLUME_ID = "internal"

/**
 * A volume the cache may live on, as offered to a viewer.
 *
 * [dir] is the `mlib` directory itself, not the volume's cache root: a
 * caller hands it straight to `SimpleCache`, and deciding where inside a
 * volume the cache sits is this file's business rather than every caller's.
 */
data class CacheVolume(
    val id: String,
    val label: String,
    val dir: File,
    val freeBytes: Long,
    val removable: Boolean,
)

/**
 * One directory as the platform reported it, before the rules below decide
 * whether it becomes a row. Separate from [CacheVolume] so that those rules
 * are a pure function of what Android said and can be tested without a
 * device — the `Context` walk that produces these is the part that cannot.
 */
data class VolumeCandidate(
    val id: String,
    val label: String,
    val dir: File,
    val freeBytes: Long,
    val removable: Boolean,
)

/**
 * Which volumes are offered, given the app's own cache directory and
 * whatever `getExternalCacheDirs()` reported.
 *
 * `external` is that array's shape, nulls included: the platform keeps a
 * slot for a volume that is currently ejected, and an absent volume is not
 * a choice.
 *
 * Index 0 of `external` is dropped. It is the app's cache directory on the
 * primary *emulated* volume, which is the same physical storage as the
 * internal one already in the list — two rows for one disk, where whichever
 * a viewer picked the bytes would land in the same place.
 */
fun cacheVolumes(internalVolume: VolumeCandidate, external: List<VolumeCandidate?>): List<CacheVolume> =
    buildList {
        add(internalVolume.toVolume())
        external.drop(1).filterNotNull().forEach { add(it.toVolume()) }
    }

private fun VolumeCandidate.toVolume() = CacheVolume(
    id = id,
    label = label,
    dir = File(dir, CACHE_DIR_NAME),
    freeBytes = freeBytes,
    removable = removable,
)

/**
 * The same list, read off a real device.
 *
 * Every API here clears `minSdk = 24`, two of them exactly:
 * `getExternalCacheDirs()` is 19 and `isExternalStorageRemovable(File)` is
 * 21, but `getStorageVolume(File)` and `StorageVolume.getDescription` are
 * both 24.
 */
fun cacheVolumes(context: Context): List<CacheVolume> = cacheVolumes(
    internalVolume = VolumeCandidate(
        id = INTERNAL_VOLUME_ID,
        label = "Internal storage",
        dir = context.cacheDir,
        freeBytes = context.cacheDir.usableSpace,
        removable = false,
    ),
    external = context.externalCacheDirs.map { dir -> dir?.let { candidateFor(context, it) } },
)

private fun candidateFor(context: Context, dir: File): VolumeCandidate {
    val storage = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    // getStorageVolume answers null for a path it cannot place, and
    // getDescription can itself return null; between them that is two ways
    // to have no name for a disk that is plainly there, so both fall back
    // rather than dropping the volume.
    val volume = runCatching { storage.getStorageVolume(dir) }.getOrNull()
    val removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(true)
    return VolumeCandidate(
        id = volume?.uuid ?: dir.absolutePath,
        label = volume?.getDescription(context) ?: if (removable) "Memory card" else "External storage",
        dir = dir,
        freeBytes = dir.usableSpace,
        removable = removable,
    )
}
