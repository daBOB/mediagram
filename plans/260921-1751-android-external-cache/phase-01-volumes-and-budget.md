# Phase 1: What the choices are

**Deliverable:** Two pure modules in `:core:playback` — the list of volumes a
cache may live on, and the budget it may grow to — each fully tested, with
nothing yet calling either.

## Context

- `android/core/playback/src/main/kotlin/CacheProvider.kt` — where
  `CACHE_DIR_NAME` and `CACHE_MAX_BYTES` live today; phase 2 rewires it, this
  phase does not touch it
- `android/core/playback/src/test/kotlin/MlibDataSourceTest.kt` — the testing
  style in this module: `kotlin.test.Test`, `assertEquals`, and
  `@RunWith(RobolectricTestRunner::class)` only where a real `Context` is
  needed
- `android/core/playback/build.gradle.kts` — Robolectric and `androidx.junit`
  are already `testImplementation`; no dependency changes are needed
- Spec §3 (the volume list) and §4 (the budget)

## Key insight

`getExternalCacheDirs()` is the whole design in one call: app-private,
permission-free since API 19, one entry per shared volume, and a real `File`.
Two of its properties are easy to get wrong and are exactly what the pure
function exists to pin.

**Index 0 is not a second disk.** It is the app's cache directory on the
primary *emulated* volume — the same physical storage as `context.cacheDir` on
every device in the spec's table. Offering both would give a viewer two rows,
two labels and two free-space figures for one disk, and the bytes would land
in the same place whichever they chose.

**A null entry is an ejected volume**, not an error. The array keeps the slot.

The budget's insight is separate: resolve against **capacity**, not free
space. A volume the cache already occupies reports free space that excludes
the cache's own contents, so a budget re-derived from free space alone shrinks
on every restart and evicts what the previous run cached. Capacity is
`free + alreadyHeld - floor`, and it is stable across restarts by
construction.

---

### Task 1: The volume list

**Files:**
- Create: `android/core/playback/src/main/kotlin/CacheVolumes.kt`
- Create: `android/core/playback/src/test/kotlin/CacheVolumesTest.kt`

**Interfaces — Produces:** `playback.CacheVolume`,
`playback.VolumeCandidate`, `playback.INTERNAL_VOLUME_ID`,
`playback.cacheVolumes(internalVolume: VolumeCandidate, external: List<VolumeCandidate?>): List<CacheVolume>`,
`playback.cacheVolumes(context: Context): List<CacheVolume>`.
**Consumes:** nothing from earlier tasks.

- [ ] **Step 1: Write the failing test**

Create `android/core/playback/src/test/kotlin/CacheVolumesTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheVolumesTest'
```

Expected: FAIL to compile — `VolumeCandidate`, `CacheVolume`,
`INTERNAL_VOLUME_ID` and `cacheVolumes` are all unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `android/core/playback/src/main/kotlin/CacheVolumes.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests and make sure they pass**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheVolumesTest'
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Check the line count and commit**

```bash
cd /home/andre/Workspace/mediagram-android-cache
wc -l android/core/playback/src/main/kotlin/CacheVolumes.kt   # must be < 200
git add android/core/playback/src/main/kotlin/CacheVolumes.kt \
        android/core/playback/src/test/kotlin/CacheVolumesTest.kt
git commit -m "feat(android): list the volumes a cache could live on"
```

---

### Task 2: The budget

**Files:**
- Create: `android/core/playback/src/main/kotlin/CacheBudget.kt`
- Create: `android/core/playback/src/test/kotlin/CacheBudgetTest.kt`

**Interfaces — Produces:** `playback.CacheBudget` (enum with entries
`Gib8`, `Gib32`, `Gib128`, `AsMuchAsFits`, each carrying `label: String` and
`bytes: Long?`), `playback.cacheCapacity(freeBytes: Long, heldBytes: Long): Long`,
`playback.resolveBudget(choice: CacheBudget?, capacityBytes: Long): Long`,
`playback.isOffered(choice: CacheBudget, capacityBytes: Long): Boolean`.
**Consumes:** nothing from Task 1 — the two files are independent.

- [ ] **Step 1: Write the failing test**

Create `android/core/playback/src/test/kotlin/CacheBudgetTest.kt`:

```kotlin
package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GIB = 1024L * 1024 * 1024

class CacheBudgetTest {

    @Test
    fun capacityIsWhatIsFreePlusWhatTheCacheAlreadyHoldsLessTheFloor() {
        // The cache's own 20 GiB is not free space, but it is space the
        // cache may go on using.
        assertEquals(29 * GIB, cacheCapacity(freeBytes = 10 * GIB, heldBytes = 20 * GIB))
    }

    @Test
    fun capacityIsNeverNegativeOnAFullVolume() {
        assertEquals(0L, cacheCapacity(freeBytes = 0, heldBytes = 0))
    }

    @Test
    fun aBudgetResolvedTwiceOverTheSameVolumeDoesNotShrink() {
        // First run: nothing held, 40 GiB free. Second run: the cache has
        // taken 30 GiB of it, so only 10 GiB reads as free.
        val first = resolveBudget(CacheBudget.AsMuchAsFits, cacheCapacity(40 * GIB, 0))
        val second = resolveBudget(CacheBudget.AsMuchAsFits, cacheCapacity(10 * GIB, 30 * GIB))
        assertEquals(first, second)
    }

    @Test
    fun anExplicitPresetIsHonouredWhenItFits() {
        assertEquals(32 * GIB, resolveBudget(CacheBudget.Gib32, cacheCapacity(100 * GIB, 0)))
    }

    @Test
    fun aPresetThatNoLongerFitsIsClampedToWhatDoes() {
        // Chosen when the card was empty; another app has since filled it.
        assertEquals(cacheCapacity(4 * GIB, 0), resolveBudget(CacheBudget.Gib128, cacheCapacity(4 * GIB, 0)))
    }

    @Test
    fun theDefaultIsEightGibibytesWhereThereIsRoomForIt() {
        assertEquals(8 * GIB, resolveBudget(choice = null, capacityBytes = cacheCapacity(80 * GIB, 0)))
    }

    @Test
    fun theDefaultIsHalfOfCapacityOnASmallDevice() {
        // A Fire Stick with 3 GiB free: 8 GiB would be larger than the disk.
        val capacity = cacheCapacity(freeBytes = 3 * GIB, heldBytes = 0)
        assertEquals(capacity / 2, resolveBudget(choice = null, capacityBytes = capacity))
    }

    @Test
    fun aPresetLargerThanTheVolumeIsNotOffered() {
        assertFalse(isOffered(CacheBudget.Gib128, cacheCapacity(30 * GIB, 0)))
        assertTrue(isOffered(CacheBudget.Gib8, cacheCapacity(30 * GIB, 0)))
    }

    @Test
    fun asMuchAsFitsIsAlwaysOffered() {
        assertTrue(isOffered(CacheBudget.AsMuchAsFits, cacheCapacity(0, 0)))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheBudgetTest'
```

Expected: FAIL to compile — `CacheBudget`, `cacheCapacity`, `resolveBudget`
and `isOffered` are unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `android/core/playback/src/main/kotlin/CacheBudget.kt`:

```kotlin
package playback

private const val GIB = 1024L * 1024 * 1024

/**
 * How much free space the cache leaves alone. A cache that fills a volume
 * completely takes the rest of the device down with it, and on a
 * television stick with five gigabytes there is no slack to lose.
 */
const val FREE_SPACE_FLOOR = 1 * GIB

/** What the default budget aims for where there is room for it. */
private const val DEFAULT_BUDGET = 8 * GIB

/**
 * What a viewer may choose. [bytes] is null for the open-ended option,
 * which has no fixed size to name and resolves against the volume instead.
 */
enum class CacheBudget(val label: String, val bytes: Long?) {
    Gib8("8 GB", 8 * GIB),
    Gib32("32 GB", 32 * GIB),
    Gib128("128 GB", 128 * GIB),
    AsMuchAsFits("As much as fits", null),
}

/**
 * What the cache may grow into on a volume.
 *
 * Not simply free space: space the cache already occupies reads as used,
 * but it is space the cache may go on using. Resolving a budget against
 * free space alone would therefore shrink it on every restart — each run
 * seeing only what the previous run had not yet taken — and each shrink
 * would evict what the last run cached.
 */
fun cacheCapacity(freeBytes: Long, heldBytes: Long): Long =
    maxOf(0L, freeBytes + heldBytes - FREE_SPACE_FLOOR)

/**
 * The number handed to the evictor: what was chosen, or what fits, whichever
 * is smaller.
 *
 * A null [choice] is a viewer who has never opened the screen. They get the
 * smaller of eight gibibytes and half the volume — two numbers because the
 * two failure modes are opposite. A flat eight is right on a phone with
 * seventy gigabytes spare and larger than the disk on a television stick; a
 * flat fraction is right on the stick and pointlessly timid on a half-terabyte
 * card.
 *
 * Every choice is clamped, not only the open-ended one: a preset that fitted
 * when it was chosen may not fit now, and a budget above what the volume can
 * hold means the evictor never fires and the volume fills instead.
 */
fun resolveBudget(choice: CacheBudget?, capacityBytes: Long): Long {
    val wanted = when (choice) {
        null -> minOf(DEFAULT_BUDGET, capacityBytes / 2)
        CacheBudget.AsMuchAsFits -> capacityBytes
        else -> choice.bytes ?: capacityBytes
    }
    return minOf(wanted, capacityBytes)
}

/**
 * Whether a preset is worth offering on a volume that size. The open-ended
 * option always is — it is defined as whatever fits, including very little.
 */
fun isOffered(choice: CacheBudget, capacityBytes: Long): Boolean =
    choice.bytes?.let { it <= capacityBytes } ?: true
```

- [ ] **Step 4: Run the tests and make sure they pass**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheBudgetTest'
```

Expected: PASS, 9 tests.

- [ ] **Step 5: Run the whole module's tests, then commit**

The module's existing tests must still pass — nothing here touches them, and
a failure means something unrelated broke.

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest
cd /home/andre/Workspace/mediagram-android-cache
wc -l android/core/playback/src/main/kotlin/CacheBudget.kt   # must be < 200
git add android/core/playback/src/main/kotlin/CacheBudget.kt \
        android/core/playback/src/test/kotlin/CacheBudgetTest.kt
git commit -m "feat(android): resolve a cache budget against what a volume can hold"
```

## Success criteria

- `cacheVolumes` drops the primary emulated volume, drops ejected slots, and
  always offers internal storage — proven by test, not by inspection.
- A budget resolved twice over the same volume returns the same number, with
  the cache's own contents counted as capacity rather than as used space.
- Both new files under 200 lines; `CacheProvider.kt` untouched.
- `./gradlew :core:playback:testDebugUnitTest` green.

## Risks

**The volume id is the persistence key, and `StorageVolume.uuid` can be
null** — on the primary volume, and on some OEM builds for a physical card.
The fallback is the absolute path, which is stable enough to match a volume
across restarts and unstable across a reformat. A reformat losing the setting
is acceptable; it is a cache, and phase 2 falls back to internal storage when
the recorded id matches nothing.

**Robolectric is not exercised in this phase** and that is deliberate: every
rule worth pinning is in the pure function, and the `Context` adapter is a
transcription of platform calls that a shadow would only re-assert. Phase 2
opens a real cache under Robolectric, which is where the adapter first runs.
