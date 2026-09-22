# Phase 2: A cache that reads the choice

**Deliverable:** The cache opens where a stored setting says and grows to a
budget resolved from that volume, falling back to internal storage when the
recorded volume is absent. With nothing yet able to write the setting, every
device gets the new default — which on the test phone is four times today's
ceiling.

## Context

- `android/core/playback/src/main/kotlin/CacheProvider.kt` — 95 lines; the
  single-instance guard, the off-thread open and `occupancy()` all stay, the
  hardcoded directory and constant go
- `android/core/playback/src/main/kotlin/PlayerFactory.kt:20-38` —
  `cacheDataSourceFactory`, which gains one flag and one parameter
- `android/core/data/src/main/kotlin/settings/PackageSettings.kt` — the
  interface / `InMemory…` / real-implementation shape to copy
- `android/core/data/src/main/kotlin/di/DataModule.kt:40-52` — where settings
  are provided to Hilt
- `android/feature/player/src/main/kotlin/di/PlaybackModule.kt:56-70` —
  `provideExoPlayerDeferred`, the one caller of `buildPlayer`
- `android/feature/system/src/main/kotlin/SystemViewModel.kt:60` — the other
  caller of `CacheProvider`, via `occupancy`
- Phase 1's `CacheVolumes.kt` and `CacheBudget.kt`
- Spec §5 (persistence) and §6 (the three behaviours)

## Key insight

`:core:data` must not learn what a `CacheBudget` is. The dependency runs
`:core:playback → :core:data`, so a settings type that named the enum would
invert it. The settings therefore store **two nullable strings** — a volume
id and a budget's enum name — and `:core:playback` does the mapping. An
unrecognised name reads as null, which is the same as never having chosen,
which is the right answer for a downgrade or a renamed entry.

The second insight is about ordering. The old directory is deleted **after**
the new one opens, never before: a delete-then-open would turn a failure to
open the new location into the loss of the old contents as well.

---

### Task 1: The stored choice

**Files:**
- Create: `android/core/data/src/main/kotlin/settings/CacheSettings.kt`
- Create: `android/core/data/src/test/kotlin/settings/CacheSettingsTest.kt`
- Modify: `android/core/data/src/main/kotlin/di/DataModule.kt`

**Interfaces — Produces:** `settings.CacheChoice(volumeId: String?, budgetName: String?)`,
`settings.CacheSettings` with `suspend fun read(): CacheChoice`,
`suspend fun write(volumeId: String?, budgetName: String?)`;
`settings.InMemoryCacheSettings`; `settings.PreferenceCacheSettings(context)`.
**Consumes:** nothing from phase 1.

- [ ] **Step 1: Write the failing test**

Create `android/core/data/src/test/kotlin/settings/CacheSettingsTest.kt`:

```kotlin
package settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CacheSettingsTest {

    @Test
    fun nothingHasBeenChosenBeforeAnythingIsWritten() = runTest {
        val settings = InMemoryCacheSettings()
        assertNull(settings.read().volumeId)
        assertNull(settings.read().budgetName)
    }

    @Test
    fun whatIsWrittenIsWhatIsReadBack() = runTest {
        val settings = InMemoryCacheSettings()
        settings.write(volumeId = "1A2B-3C4D", budgetName = "Gib32")
        assertEquals(CacheChoice("1A2B-3C4D", "Gib32"), settings.read())
    }

    @Test
    fun aVolumeCanBeChosenWithoutABudget() = runTest {
        val settings = InMemoryCacheSettings()
        settings.write(volumeId = "1A2B-3C4D", budgetName = null)
        assertEquals(CacheChoice("1A2B-3C4D", null), settings.read())
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:data:testDebugUnitTest --tests 'settings.CacheSettingsTest'
```

Expected: FAIL to compile — `CacheChoice`, `CacheSettings` and
`InMemoryCacheSettings` are unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `android/core/data/src/main/kotlin/settings/CacheSettings.kt`:

```kotlin
package settings

import android.content.Context

/**
 * Where the cache was asked to live and how large it was asked to grow.
 *
 * Both are strings and both are nullable, and this module deliberately
 * knows nothing about what either means. The budget is an enum in
 * `:core:playback`, which depends on this module rather than the other way
 * round; naming the type here would invert that. An unrecognised name reads
 * back as if nothing had been chosen, which is the right answer after a
 * downgrade or a renamed entry.
 */
data class CacheChoice(val volumeId: String?, val budgetName: String?)

interface CacheSettings {
    suspend fun read(): CacheChoice
    suspend fun write(volumeId: String?, budgetName: String?)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryCacheSettings : CacheSettings {

    @Volatile
    private var stored = CacheChoice(null, null)

    override suspend fun read(): CacheChoice = stored

    override suspend fun write(volumeId: String?, budgetName: String?) {
        stored = CacheChoice(volumeId, budgetName)
    }
}

/**
 * Plain `SharedPreferences`, not the encrypted ones the Telegram and package
 * settings beside this file use. A volume id and the name of a size are not
 * secrets. `EncryptedPreferences` exists in this module because an auth key
 * and a package decryption key are, not because encryption is the house
 * default for a setting, and reaching for it here would blur a distinction
 * worth keeping sharp.
 */
class PreferenceCacheSettings(private val context: Context) : CacheSettings {

    private val preferences by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun read(): CacheChoice = CacheChoice(
        volumeId = preferences.getString(KEY_VOLUME_ID, null),
        budgetName = preferences.getString(KEY_BUDGET_NAME, null),
    )

    override suspend fun write(volumeId: String?, budgetName: String?) {
        preferences.edit()
            .putString(KEY_VOLUME_ID, volumeId)
            .putString(KEY_BUDGET_NAME, budgetName)
            .apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "cache_settings"
        const val KEY_VOLUME_ID = "volume_id"
        const val KEY_BUDGET_NAME = "budget_name"
    }
}
```

- [ ] **Step 4: Bind it in Hilt**

In `android/core/data/src/main/kotlin/di/DataModule.kt`, beside
`provideTmdbSettings`, add:

```kotlin
    @Provides
    @Singleton
    fun provideCacheSettings(@ApplicationContext context: Context): CacheSettings =
        PreferenceCacheSettings(context)
```

Add `import settings.CacheSettings` and `import settings.PreferenceCacheSettings`
if the file imports settings types individually; follow whatever the
neighbouring providers already do.

- [ ] **Step 5: Run the tests and make sure they pass**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:data:testDebugUnitTest --tests 'settings.CacheSettingsTest'
```

Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
cd /home/andre/Workspace/mediagram-android-cache
git add android/core/data/src/main/kotlin/settings/CacheSettings.kt \
        android/core/data/src/test/kotlin/settings/CacheSettingsTest.kt \
        android/core/data/src/main/kotlin/di/DataModule.kt
git commit -m "feat(android): remember which volume and budget the cache was given"
```

---

### Task 2: `CacheProvider` opens where the choice says

**Files:**
- Modify: `android/core/playback/src/main/kotlin/CacheProvider.kt`
- Create: `android/core/playback/src/main/kotlin/CacheLocation.kt`
- Create: `android/core/playback/src/test/kotlin/CacheLocationTest.kt`
- Modify: `android/core/playback/src/test/kotlin/…` — a new
  `CacheProviderTest.kt` under Robolectric

**Interfaces — Produces:**
`playback.ResolvedCache(volume: CacheVolume, budgetBytes: Long, asked: String?, isFallback: Boolean)`,
`playback.resolveCache(volumes: List<CacheVolume>, choice: CacheChoice, heldBytes: Long): ResolvedCache`,
and `CacheProvider.get(context, settings, dispatcher)` /
`CacheProvider.occupancy(context, settings, dispatcher)` — both gain a
`CacheSettings` parameter. `CacheOccupancy` gains `volumeLabel: String` and
`isFallback: Boolean`.
**Consumes:** `CacheVolume`, `cacheVolumes`, `CacheBudget`, `cacheCapacity`,
`resolveBudget` (phase 1 Tasks 1–2); `CacheChoice`, `CacheSettings`
(this phase, Task 1).

- [ ] **Step 1: Write the failing test for the pure resolution**

Create `android/core/playback/src/test/kotlin/CacheLocationTest.kt`:

```kotlin
package playback

import settings.CacheChoice
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GIB = 1024L * 1024 * 1024

private val internalVolume =
    CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File("/data/cache/mlib"), 80 * GIB, removable = false)
private val card =
    CacheVolume("1A2B-3C4D", "Memory card", File("/storage/1A2B-3C4D/cache/mlib"), 200 * GIB, removable = true)

class CacheLocationTest {

    @Test
    fun withNothingChosenTheCacheLandsOnInternalStorage() {
        val resolved = resolveCache(listOf(internalVolume, card), CacheChoice(null, null), heldBytes = 0)
        assertEquals(INTERNAL_VOLUME_ID, resolved.volume.id)
        assertFalse(resolved.isFallback)
    }

    @Test
    fun theChosenVolumeIsUsedWhenItIsThere() {
        val resolved = resolveCache(listOf(internalVolume, card), CacheChoice("1A2B-3C4D", "Gib32"), heldBytes = 0)
        assertEquals("1A2B-3C4D", resolved.volume.id)
        assertEquals(32 * GIB, resolved.budgetBytes)
        assertFalse(resolved.isFallback)
    }

    @Test
    fun aMissingVolumeFallsBackToInternalStorageAndSaysSo() {
        // The card was taken out between runs.
        val resolved = resolveCache(listOf(internalVolume), CacheChoice("1A2B-3C4D", "Gib32"), heldBytes = 0)
        assertEquals(INTERNAL_VOLUME_ID, resolved.volume.id)
        assertTrue(resolved.isFallback)
        // The choice is remembered, not cleared: putting the card back must
        // restore it rather than leave the viewer to set it again.
        assertEquals("1A2B-3C4D", resolved.asked)
    }

    @Test
    fun anUnrecognisedBudgetNameIsTreatedAsNoChoiceAtAll() {
        // A downgrade, or an entry renamed since the setting was written.
        val resolved = resolveCache(listOf(internalVolume), CacheChoice(null, "Gib999"), heldBytes = 0)
        assertEquals(resolveBudget(null, cacheCapacity(80 * GIB, 0)), resolved.budgetBytes)
    }

    @Test
    fun theBudgetIsResolvedAgainstTheVolumeTheCacheActuallyLandsOn() {
        // Asked for 128 GB on a card that is gone; internal storage must not
        // be handed the card's number.
        val small = CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File("/data/cache/mlib"), 10 * GIB, false)
        val resolved = resolveCache(listOf(small), CacheChoice("1A2B-3C4D", "Gib128"), heldBytes = 0)
        assertEquals(cacheCapacity(10 * GIB, 0), resolved.budgetBytes)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheLocationTest'
```

Expected: FAIL to compile — `resolveCache` and `ResolvedCache` are unresolved.

- [ ] **Step 3: Write the resolution**

Create `android/core/playback/src/main/kotlin/CacheLocation.kt`:

```kotlin
package playback

import settings.CacheChoice

/**
 * Where this run's cache goes and how large it may grow, having reconciled
 * what was asked for against what is actually mounted.
 *
 * [asked] is the volume id that was recorded, kept even when it could not be
 * honoured: a viewer who takes a card out to copy a file onto it must find
 * their setting intact when they put it back, and a screen that reports the
 * fallback needs to name what it fell back *from*.
 */
data class ResolvedCache(
    val volume: CacheVolume,
    val budgetBytes: Long,
    val asked: String?,
    val isFallback: Boolean,
)

/**
 * The recorded choice reconciled with the volumes that exist right now.
 *
 * A recorded volume that matches nothing falls back to the first in the
 * list, which `cacheVolumes` guarantees is internal storage. The budget is
 * then resolved against the volume the cache actually lands on, never
 * against the one that was asked for — a 128 GB choice made on a card must
 * not become a 128 GB budget on a phone.
 */
fun resolveCache(volumes: List<CacheVolume>, choice: CacheChoice, heldBytes: Long): ResolvedCache {
    val fallback = volumes.first()
    val chosen = volumes.firstOrNull { it.id == choice.volumeId }
    val volume = chosen ?: fallback
    val budget = CacheBudget.entries.firstOrNull { it.name == choice.budgetName }
    return ResolvedCache(
        volume = volume,
        budgetBytes = resolveBudget(budget, cacheCapacity(volume.freeBytes, heldBytes)),
        asked = choice.volumeId,
        // Nothing recorded is not a fallback; it is a first run.
        isFallback = choice.volumeId != null && chosen == null,
    )
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheLocationTest'
```

Expected: PASS, 5 tests.

- [ ] **Step 5: Rewire `CacheProvider`**

Rewrite the bottom half of
`android/core/playback/src/main/kotlin/CacheProvider.kt`. `CACHE_DIR_NAME`
moves out (it is now in `CacheVolumes.kt`) and `CACHE_MAX_BYTES` goes
entirely. The existing doc comments on `get` and the `object` stay —
everything they say about single instances, off-thread opening and the
database-backed index is still true.

```kotlin
/** What the disk cache is holding, against what it may hold, and where. */
data class CacheOccupancy(
    val heldBytes: Long,
    val budgetBytes: Long,
    val volumeLabel: String,
    /** True when the volume asked for was not mounted and internal storage took over. */
    val isFallback: Boolean,
)

object CacheProvider {

    @Volatile
    private var instance: SimpleCache? = null

    @Volatile
    private var resolved: ResolvedCache? = null

    suspend fun get(
        context: Context,
        settings: CacheSettings,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SimpleCache {
        instance?.let { return it }
        val choice = settings.read()
        return withContext(dispatcher) {
            synchronized(this@CacheProvider) {
                instance ?: open(context, choice).also { instance = it }
            }
        }
    }

    suspend fun occupancy(
        context: Context,
        settings: CacheSettings,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): CacheOccupancy {
        val cache = get(context, settings, dispatcher)
        val where = resolved
        return CacheOccupancy(
            heldBytes = cache.cacheSpace,
            budgetBytes = where?.budgetBytes ?: 0L,
            volumeLabel = where?.volume?.label.orEmpty(),
            isFallback = where?.isFallback ?: false,
        )
    }

    /** Test-only: clears the cached instance so a test can observe a fresh construction. */
    internal fun resetForTest() {
        instance = null
        resolved = null
    }

    private fun open(context: Context, choice: CacheChoice): SimpleCache {
        val volumes = cacheVolumes(context)
        // Measured before resolving, because the budget depends on it: what
        // the cache already holds on the volume it is about to open counts
        // as capacity, not as space someone else has taken.
        val target = volumes.firstOrNull { it.id == choice.volumeId } ?: volumes.first()
        val where = resolveCache(volumes, choice, heldBytes = sizeOf(target.dir))
        resolved = where
        val cache = SimpleCache(
            where.volume.dir,
            LeastRecentlyUsedCacheEvictor(where.budgetBytes),
            StandaloneDatabaseProvider(context),
        )
        // Only once the new location has opened. A delete first would turn a
        // failure to open into the loss of what the old location held as well.
        volumes.filter { it.dir != where.volume.dir }.forEach { it.dir.deleteRecursively() }
        return cache
    }

    /**
     * What the cache already holds on a volume, read off the directory
     * rather than the index: the index is inside the `SimpleCache` that has
     * not been built yet, and the number is needed to build it. A walk over
     * a few thousand span files costs milliseconds, and this runs once per
     * process on a dispatcher that is already doing disk work.
     */
    private fun sizeOf(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
```

- [ ] **Step 6: Write the Robolectric test for a real open**

Create `android/core/playback/src/test/kotlin/CacheProviderTest.kt`:

```kotlin
package playback

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import settings.InMemoryCacheSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CacheProviderTest {

    @AfterTest
    fun tearDown() = CacheProvider.resetForTest()

    @Test
    fun aCacheOpensOnInternalStorageWhenNothingHasBeenChosen() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val occupancy = CacheProvider.occupancy(context, InMemoryCacheSettings())
        assertEquals("Internal storage", occupancy.volumeLabel)
        assertFalse(occupancy.isFallback)
    }

    @Test
    fun theBudgetIsNoLongerTwoGibibytes() = runTest {
        // The constant this work exists to remove. Whatever the emulated
        // volume reports, the default is derived from it rather than fixed.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val occupancy = CacheProvider.occupancy(context, InMemoryCacheSettings())
        assertTrue(occupancy.budgetBytes > 0)
        assertEquals(
            resolveBudget(null, cacheCapacity(context.cacheDir.usableSpace, occupancy.heldBytes)),
            occupancy.budgetBytes,
        )
    }

    @Test
    fun aVolumeThatIsNotMountedFallsBackAndReportsIt() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = InMemoryCacheSettings()
        settings.write(volumeId = "no-such-card", budgetName = "Gib32")
        val occupancy = CacheProvider.occupancy(context, settings)
        assertEquals("Internal storage", occupancy.volumeLabel)
        assertTrue(occupancy.isFallback)
    }
}
```

- [ ] **Step 7: Run both test classes**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest --tests 'playback.CacheProviderTest' --tests 'playback.CacheLocationTest'
```

Expected: PASS, 8 tests. `:core:playback` will not compile until Task 3
updates the two callers, so expect the compile step to fail first and fix it
by doing Task 3 before re-running — the tasks are split for review, not for
independent compilation.

- [ ] **Step 8: Commit**

```bash
cd /home/andre/Workspace/mediagram-android-cache
wc -l android/core/playback/src/main/kotlin/CacheProvider.kt \
      android/core/playback/src/main/kotlin/CacheLocation.kt   # both < 200
git add android/core/playback/src/main/kotlin/CacheProvider.kt \
        android/core/playback/src/main/kotlin/CacheLocation.kt \
        android/core/playback/src/test/kotlin/CacheLocationTest.kt \
        android/core/playback/src/test/kotlin/CacheProviderTest.kt
git commit -m "feat(android): open the cache where the stored choice says"
```

---

### Task 3: The callers, and a cache failure that does not stop the film

**Files:**
- Modify: `android/core/playback/src/main/kotlin/PlayerFactory.kt`
- Modify: `android/feature/player/src/main/kotlin/di/PlaybackModule.kt`
- Modify: `android/feature/system/src/main/kotlin/SystemViewModel.kt`
- Modify: `android/feature/system/src/main/kotlin/SystemUiState.kt`
- Modify: `android/ui-mobile/src/main/kotlin/SystemRows.kt` and
  `SystemScreen.kt`
- Modify: `android/feature/system/src/test/kotlin/FakeCore.kt` if it needs a
  `CacheSettings`

**Interfaces — Produces:** `buildPlayer(context, counters, settings, currentCore)`
and `cacheDataSourceFactory(context, counters, settings, currentCore)`, both
taking `settings: CacheSettings` as the third parameter.
`SystemUiState` gains `cacheVolumeLabel: String` and `cacheIsFallback: Boolean`.
**Consumes:** `CacheProvider.get` / `occupancy` (Task 2), `CacheSettings`
(Task 1).

- [ ] **Step 1: Thread the settings through `PlayerFactory`**

In `cacheDataSourceFactory`, add the parameter and the flag:

```kotlin
suspend fun cacheDataSourceFactory(
    context: Context,
    counters: PlaybackCounters,
    settings: CacheSettings,
    currentCore: () -> CoreClient?,
): DataSource.Factory = CacheDataSource.Factory()
    .setCache(CacheProvider.get(context, settings))
    .setUpstreamDataSourceFactory(MlibDataSourceFactory(counters, currentCore))
    // A write that fails must not stop the film. A card pulled mid-playback
    // and a volume that has filled up produce the same failure, and in both
    // cases falling through to upstream is the answer a viewer wants over a
    // player that stops. Without this flag the failure propagates and
    // playback ends.
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    .setEventListener(object : CacheDataSource.EventListener {
        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
            counters.servedFromCache(cachedBytesRead)
        }

        override fun onCacheIgnored(reason: Int) = Unit
    })
```

Give `buildPlayer` the same new parameter and pass it straight down. Add
`import settings.CacheSettings`.

- [ ] **Step 2: Inject it at the one place a player is built**

In `PlaybackModule.provideExoPlayerDeferred`, add `settings: CacheSettings`
to the parameter list and pass it to `buildPlayer`:

```kotlin
    fun provideExoPlayerDeferred(
        @ApplicationContext context: Context,
        coreProvider: CoreProvider,
        counters: PlaybackCounters,
        settings: CacheSettings,
        scope: CoroutineScope,
    ): @JvmSuppressWildcards Deferred<ExoPlayer> = scope.async {
        coreProvider.awaitCore()
        buildPlayer(context, counters, settings) { coreProvider.core.value }
    }
```

- [ ] **Step 3: Give the System screen the volume**

`SystemViewModel` gains `private val cacheSettings: CacheSettings` as a
constructor parameter, passes it to `CacheProvider.occupancy(context,
cacheSettings)`, and fills two new `SystemUiState` fields:

```kotlin
                cacheVolumeLabel = occupancy.volumeLabel,
                cacheIsFallback = occupancy.isFallback,
```

Add to `SystemUiState`:

```kotlin
    /** Which volume the cache is actually on, for the Cache block's Where row. */
    val cacheVolumeLabel: String,
    /** True when that is not the volume that was asked for. */
    val cacheIsFallback: Boolean,
```

- [ ] **Step 4: Write the failing test for the row's wording**

Add to `android/ui-mobile/src/test/kotlin/SystemRowsTest.kt`:

```kotlin
    @Test
    fun theWhereRowNamesTheVolumeTheCacheIsOn() {
        assertEquals("Internal storage", cacheWhereLine("Internal storage", isFallback = false))
    }

    @Test
    fun theWhereRowSaysWhenItIsNotTheVolumeThatWasAskedFor() {
        assertEquals(
            "Internal storage — the chosen volume is not present",
            cacheWhereLine("Internal storage", isFallback = true),
        )
    }
```

- [ ] **Step 5: Run it to make sure it fails**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :ui-mobile:testDebugUnitTest --tests '*SystemRowsTest'
```

Expected: FAIL to compile — `cacheWhereLine` is unresolved.

- [ ] **Step 6: Add the row**

In `android/ui-mobile/src/main/kotlin/SystemRows.kt`:

```kotlin
/**
 * Which volume the cache is on, and — when the one that was asked for is not
 * mounted — that this is not it. Silence there would read as the setting
 * having been forgotten, which is the one thing that has not happened.
 */
internal fun cacheWhereLine(volumeLabel: String, isFallback: Boolean): String =
    if (isFallback) "$volumeLabel — the chosen volume is not present" else volumeLabel
```

Add `"Where" to cacheWhereLine(state.cacheVolumeLabel, state.cacheIsFallback)`
to the `CacheBlock` rows in `SystemScreen.kt`, above the existing Held row.

- [ ] **Step 7: Run every affected module's tests**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :core:playback:testDebugUnitTest :core:data:testDebugUnitTest \
          :feature:system:testDebugUnitTest :ui-mobile:testDebugUnitTest
```

Expected: PASS. Any `SystemViewModel` or `FakeCore` construction in
`:feature:system`'s tests now needs an `InMemoryCacheSettings()`; fix those
call sites rather than reintroducing a default parameter, so a future caller
has to think about which settings it means.

- [ ] **Step 8: Prove it on the device**

```bash
cd /home/andre/Workspace/mediagram-android-cache/android
./gradlew :app:installDebug
adb shell am start -n com.mediagram.android/.MainActivity
```

Open the overflow menu → System. The Cache block must show a **Where** row
reading "Internal storage", and the **Held** row's budget must be the new
default rather than 2.00 GB. Play a title for thirty seconds and return: Held
must have grown.

- [ ] **Step 9: Commit**

```bash
cd /home/andre/Workspace/mediagram-android-cache
git add -A
git commit -m "feat(android): keep playing when a cache write fails, and say where the cache is"
```

## Success criteria

- On the test phone the budget is no longer 2 GiB and the System screen names
  the volume.
- A recorded volume that is not mounted falls back to internal storage,
  reports the fallback, and **keeps the recorded choice**.
- A budget is resolved against the volume the cache lands on, not the one
  that was asked for.
- The old directory is deleted only after the new one opens.
- `CacheProvider.kt` and `CacheLocation.kt` both under 200 lines.
- `scripts/check.sh` green with `ANDROID_HOME` set.

## Risks

**`deleteRecursively()` runs over every volume that is not the chosen one,
on every open.** On a first run after this phase there is nothing to delete
and it costs a `listFiles` per volume. The hazard is a bug in
`cacheVolumes` pointing `dir` somewhere that is not an `mlib` directory —
which is why `CacheVolume.dir` is built by `toVolume()` in one place and
never assembled by a caller.

**`sizeOf` walks the cache directory before the cache opens.** On a 200 GB
cache with large spans this is thousands of `stat` calls, not millions, and
it runs once per process on `Dispatchers.IO`. If it ever shows up in the
time-to-first-frame measurement, the fix is to cache the number in the
settings rather than to guess it.

**Robolectric's emulated volume is not a real card.** These tests prove the
fallback path and the default budget; they cannot prove that a physical card
is enumerated. That needs hardware nobody in this project has yet, and
Step 8 is the closest available substitute.
