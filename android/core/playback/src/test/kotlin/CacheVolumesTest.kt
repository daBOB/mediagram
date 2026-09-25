package playback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun candidate(id: String, path: String, free: Long, removable: Boolean = false) =
    VolumeCandidate(id = id, label = id, dir = File(path), freeBytes = free, removable = removable)

private val internalCandidate = candidate(INTERNAL_VOLUME_ID, "/data/cache", 80_000_000_000L)

class CacheVolumesTest {

    @Test
    fun aDeviceWithNoRemovableVolumeOffersOnlyInternalStorage() {
        val volumes = cacheVolumes(internalCandidate, listOf(candidate("emulated", "/sdcard/cache", 80_000_000_000L)))
        assertEquals(listOf(INTERNAL_VOLUME_ID), volumes.map { it.id })
    }

    @Test
    fun thePrimaryEmulatedVolumeIsNotOfferedAsASecondDisk() {
        // getExternalCacheDirs()[0] is the same storage as context.cacheDir.
        val volumes = cacheVolumes(
            internalCandidate,
            listOf(
                candidate("emulated", "/sdcard/cache", 80_000_000_000L),
                candidate("1A2B-3C4D", "/storage/1A2B-3C4D/cache", 30_000_000_000L, removable = true),
            ),
        )
        assertEquals(listOf(INTERNAL_VOLUME_ID, "1A2B-3C4D"), volumes.map { it.id })
    }

    @Test
    fun anEjectedVolumeIsNotOffered() {
        val volumes = cacheVolumes(internalCandidate, listOf(candidate("emulated", "/sdcard/cache", 1L), null))
        assertEquals(listOf(INTERNAL_VOLUME_ID), volumes.map { it.id })
    }

    @Test
    fun aDeviceThatReportsNoExternalVolumesAtAllStillOffersInternalStorage() {
        assertEquals(listOf(INTERNAL_VOLUME_ID), cacheVolumes(internalCandidate, emptyList()).map { it.id })
    }

    @Test
    fun theOfferedDirectoryIsTheMlibDirectoryInsideTheVolumesCacheDirectory() {
        val volumes = cacheVolumes(internalCandidate, emptyList())
        assertEquals(File("/data/cache/mlib"), volumes.single().dir)
    }

    @Test
    fun aRemovableVolumeIsMarkedAsOne() {
        val volumes = cacheVolumes(
            internalCandidate,
            listOf(candidate("emulated", "/sdcard/cache", 1L), candidate("card", "/storage/card/cache", 1L, removable = true)),
        )
        assertTrue(volumes.last().removable)
        assertTrue(!volumes.first().removable)
    }
}
