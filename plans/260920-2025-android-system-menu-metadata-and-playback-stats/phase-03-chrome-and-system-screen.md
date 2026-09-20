# Phase 3: Chrome, and the system screen

**Deliverable:** the app's first app bar and overflow menu, and a System screen
that says what it is doing. Everything after this phase hangs off the menu it
introduces.

## Context

- Spec §3 (chrome), §5 (system information), §9 (why there is no Conversion block)
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt` — the `when` tree and its per-destination `BackHandler`
- `android/ui-mobile/src/main/kotlin/MobileApp.kt:53-56` — `WithStartOver`, and the inset comment that explains the app draws edge to edge
- `web/public/lib/status-view.js` — the reference surface, and why it is a definition list rather than a telemetry grid

## Key insight

The app has no `Scaffold`, no `TopAppBar`, no `NavHost` and no back stack —
`CatalogAndPlayer` is a three-branch `when` with a `BackHandler` per branch.
That is a smaller thing to extend than it looks: a fourth branch with its own
`BackHandler` is the shape the file already uses three times.

The counters the System screen needs are also the counters the stats overlay
needs, so they are built once, here, and phase 6 reads the same object.
media3 already offers the cache half through `CacheDataSource.EventListener`;
`PlayerFactory` simply never attaches one.

---

### Task 1: Counting what the byte path did

**Files:**
- Create: `android/core/playback/src/main/kotlin/PlaybackCounters.kt`
- Modify: `android/core/playback/src/main/kotlin/MlibDataSource.kt`, `android/core/playback/src/main/kotlin/PlayerFactory.kt`, `android/feature/player/src/main/kotlin/di/PlaybackModule.kt`
- Test: `android/core/playback/src/test/kotlin/PlaybackCountersTest.kt`

**Interfaces — Produces:**
`class PlaybackCounters` with `fun totals(): PlaybackTotals`, and
`data class PlaybackTotals(val fromCacheBytes: Long, val fromUpstreamBytes: Long, val fetches: Int, val failedReads: Int)`.
Provided as a `@Singleton` by Hilt. Phase 3 Task 3 and phase 6 both read it.

- [ ] **Step 1: Write the failing test**

```kotlin
package playback

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the byte path has done since the process started, which is the honest
 * scope: these are read to answer "what is this app doing", and a counter
 * reset per title would answer a different question.
 */
class PlaybackCountersTest {

    @Test
    fun afreshProcessHasDoneNothing() {
        assertEquals(PlaybackTotals(0, 0, 0, 0), PlaybackCounters().totals())
    }

    @Test
    fun aFetchIsCountedOnceAndCarriesItsBytes() {
        val counters = PlaybackCounters()

        counters.fetched(1_048_576)
        counters.fetched(524_288)

        val totals = counters.totals()
        assertEquals(2, totals.fetches)
        assertEquals(1_572_864, totals.fromUpstreamBytes)
    }

    /**
     * A read served from disk never reached Telegram, and counting it as
     * upstream would make a cache that is working look like a link that is
     * busy — the exact opposite of what the number is read for.
     */
    @Test
    fun bytesFromDiskAreNotBytesFromTheNetwork() {
        val counters = PlaybackCounters()

        counters.servedFromCache(2_000)
        counters.fetched(1_000)

        val totals = counters.totals()
        assertEquals(2_000, totals.fromCacheBytes)
        assertEquals(1_000, totals.fromUpstreamBytes)
        assertEquals(1, totals.fetches)
    }

    /**
     * A failure brought no bytes. Counting it as a fetch would quietly
     * improve the average size of one.
     */
    @Test
    fun aFailedReadCountsAsAFailureAndNothingElse() {
        val counters = PlaybackCounters()

        counters.readFailed()

        val totals = counters.totals()
        assertEquals(1, totals.failedReads)
        assertEquals(0, totals.fetches)
        assertEquals(0, totals.fromUpstreamBytes)
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd /home/andre/Workspace/mediagram-android/android
./gradlew :core:playback:testDebugUnitTest --tests '*PlaybackCountersTest*'
```

Expected: FAIL to compile — `unresolved reference: PlaybackCounters`.

- [ ] **Step 3: Implement**

```kotlin
package playback

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** What the byte path has done, for the surfaces that report it. */
data class PlaybackTotals(
    val fromCacheBytes: Long,
    val fromUpstreamBytes: Long,
    val fetches: Int,
    val failedReads: Int,
)

/**
 * Counts reads for the System screen and the playback overlay.
 *
 * Atomic rather than plain fields because they are written on ExoPlayer's
 * loader thread and read on the main one, and a long is not written
 * atomically on every device this runs on.
 *
 * Process-lifetime, like the player and the cache it counts for. A counter
 * reset per title would answer "what did this film cost", which is a
 * different and narrower question than the one either surface asks.
 */
class PlaybackCounters {

    private val fromCache = AtomicLong()
    private val fromUpstream = AtomicLong()
    private val fetchCount = AtomicInteger()
    private val failures = AtomicInteger()

    fun totals(): PlaybackTotals = PlaybackTotals(
        fromCacheBytes = fromCache.get(),
        fromUpstreamBytes = fromUpstream.get(),
        fetches = fetchCount.get(),
        failedReads = failures.get(),
    )

    /** One round trip to Telegram that returned bytes. */
    fun fetched(bytes: Int) {
        fetchCount.incrementAndGet()
        fromUpstream.addAndGet(bytes.toLong())
    }

    /** Bytes media3 served from its own disk cache, which never reached the core. */
    fun servedFromCache(bytes: Long) {
        fromCache.addAndGet(bytes)
    }

    /** A read that raised rather than returning. It brought no bytes. */
    fun readFailed() {
        failures.incrementAndGet()
    }
}
```

- [ ] **Step 4: Run the test**

```bash
./gradlew :core:playback:testDebugUnitTest --tests '*PlaybackCountersTest*'
```

Expected: PASS, all four.

- [ ] **Step 5: Wire it into the byte path**

`MlibDataSource` takes the counters and reports. Change its constructor to
`class MlibDataSource(private val core: CoreClient?, private val counters: PlaybackCounters)`,
and in `fetch`:

```kotlin
    private fun fetch(want: Int): ByteArray =
        try {
            runBlocking { core!!.read(setId!!, position, want) }.also { counters.fetched(it.size) }
        } catch (e: CoreException) {
            counters.readFailed()
            throw IOException("could not read from the set", e)
        }
```

`MlibDataSourceFactory` takes the counters too and passes them through.

In `PlayerFactory.cacheDataSourceFactory`, attach the listener media3 already
offers:

```kotlin
suspend fun cacheDataSourceFactory(
    context: Context,
    counters: PlaybackCounters,
    currentCore: () -> CoreClient?,
): DataSource.Factory = CacheDataSource.Factory()
    .setCache(CacheProvider.get(context))
    .setUpstreamDataSourceFactory(MlibDataSourceFactory(counters, currentCore))
    // media3 offers this and nothing has ever attached one. Without it there
    // is no way to tell a cache that is carrying playback from one that is
    // being bypassed, which is the first thing worth knowing about a read.
    .setEventListener { _, cachedBytesRead -> counters.servedFromCache(cachedBytesRead) }
```

`buildPlayer` gains the same parameter and passes it on. In `PlaybackModule`,
provide the counters:

```kotlin
    @Provides
    @Singleton
    fun playbackCounters(): PlaybackCounters = PlaybackCounters()
```

and pass them wherever `buildPlayer` is called.

- [ ] **Step 6: Run the module's tests**

```bash
./gradlew :core:playback:testDebugUnitTest :feature:player:testDebugUnitTest
```

Expected: PASS. `MlibDataSourceTest` and `PlayerFactoryTest` both construct
these types and need the new argument; pass a fresh `PlaybackCounters()`.

- [ ] **Step 7: Commit**

```bash
git add android/core/playback android/feature/player
git commit -m "feat(android): count what the byte path actually did"
```

---

### Task 2: An app bar, and a menu to hang things on

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/AppChrome.kt`
- Modify: `android/ui-mobile/src/main/kotlin/MobileApp.kt`, `android/ui-mobile/src/main/kotlin/LibraryFlow.kt`
- Test: `android/ui-mobile/src/test/kotlin/AppChromeTest.kt`

**Interfaces — Consumes:** nothing from Task 1.
**Produces:** `@Composable fun LibraryScaffold(title: String, onBack: (() -> Unit)?, menu: MenuActions, content: @Composable () -> Unit)`
and `data class MenuActions(val onSystem: () -> Unit, val onFetchPosters: () -> Unit, val onTmdbKey: () -> Unit, val onStartOver: () -> Unit)`.
Tasks 3, and phases 4 and 5, all render inside it.

- [ ] **Step 1: Write the failing test**

Only the decision is testable in this module — there is still no Compose test
rule, and `PlayerScreenTest` says so.

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the bar says it is showing. The title is the one piece of chrome that
 * has to change per destination, and getting it from a function rather than
 * from each screen is what stops four screens inventing four spellings.
 */
class AppChromeTest {

    @Test
    fun theCatalogIsTheAppItself() {
        assertEquals("Mediagram", barTitleFor(Destination.Catalog))
    }

    @Test
    fun aCollectionIsNamedAfterWhatItHolds() {
        assertEquals("Spartacus", barTitleFor(Destination.Collection("Spartacus")))
    }

    @Test
    fun theSystemScreenSaysWhatItIs() {
        assertEquals("System", barTitleFor(Destination.System))
    }

    /**
     * The catalog is the top of the tree; a back arrow there would either do
     * nothing or leave the app, and both are worse than no arrow.
     */
    @Test
    fun onlyASubScreenOffersAWayBack() {
        assertNull(backLabelFor(Destination.Catalog))
        assertEquals("Back", backLabelFor(Destination.System))
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*AppChromeTest*'
```

Expected: FAIL to compile — `unresolved reference: Destination`.

- [ ] **Step 3: Implement**

`AppChrome.kt` holds the `Destination` sealed interface, the two pure
functions above, `MenuActions`, and `LibraryScaffold` — a `Scaffold` whose
`topBar` is a `TopAppBar` with the title, an optional back `IconButton`
drawing `Text("←")` exactly as `PlayerScreen` does, and an overflow
`IconButton` drawing `Text("⋮")` that opens a `DropdownMenu` of four
`DropdownMenuItem`s: System, Fetch posters…, TMDB key…, Start over.

No Material icons: this module has no such dependency and the rest of the app
draws its controls as glyphs.

The ellipsis on two items is doing work — it says the item opens something
rather than doing something, which is the difference between the menu and a
button that starts a network run without warning.

- [ ] **Step 4: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*AppChromeTest*'
```

Expected: PASS, all four.

- [ ] **Step 5: Retire `WithStartOver` and add the destination**

`StartOverAction`'s dialog moves behind the menu's Start over item, unchanged —
same wording, same confirmation. `WithStartOver` is deleted from `MobileApp.kt`;
its one job was hosting that button. The setup screens keep their own
`Box(... windowInsetsPadding(WindowInsets.safeDrawing))`, which is separate and
still needed.

In `LibraryFlow.kt`, add a fourth branch to the `when`, before `collection`:

```kotlin
        showingSystem -> {
            BackHandler { showingSystem = false }
            LibraryScaffold(
                title = "System",
                onBack = { showingSystem = false },
                menu = menuActions,
            ) { SystemScreen() }
        }
```

with `var showingSystem by rememberSaveable { mutableStateOf(false) }` beside
the two existing positions, for the same reason the comment there already
gives: the Activity is destroyed and recreated on rotation.

The catalog and collection branches wrap their content in `LibraryScaffold`
instead of `WithStartOver`. The player branch is untouched — it gets the whole
window, and its own comment says why.

- [ ] **Step 6: Build and check the line budget**

```bash
./gradlew :ui-mobile:testDebugUnitTest
wc -l ui-mobile/src/main/kotlin/*.kt
```

Expected: PASS, and every file under 200. If `LibraryFlow.kt` or `AppChrome.kt`
has gone over, move `MenuActions` and the two pure functions into a new
`ui-mobile/src/main/kotlin/AppDestinations.kt` — they are the part with no tie
to composition.

- [ ] **Step 7: Commit**

```bash
git add android/ui-mobile
git commit -m "feat(android): give the app a bar, and somewhere to put a setting"
```

---

### Task 3: The system screen

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/SystemScreen.kt`, `android/ui-mobile/src/main/kotlin/SystemRows.kt`
- Create: `android/feature/system/` (module: `SystemViewModel.kt`, `SystemUiState.kt`, `build.gradle.kts`)
- Modify: `android/settings.gradle.kts`, `android/ui-mobile/build.gradle.kts`
- Test: `android/ui-mobile/src/test/kotlin/SystemRowsTest.kt`

**Interfaces — Consumes:** `PlaybackCounters.totals()` (Task 1), `LibraryScaffold` (Task 2), `CoreClient.catalogFacts()` (phase 2 task 3).
**Produces:** `@Composable fun SystemScreen()`.

- [ ] **Step 1: Write the failing test**

The formatting decisions are what can be proved here; the layout is device work.

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How the System screen says a number. Mirrors the web player's status panel:
 * a row whose value is not known is left out entirely rather than shown
 * blank, because a blank row reads as a broken value rather than an absent one.
 */
class SystemRowsTest {

    @Test
    fun heldSpaceIsShownAgainstItsBudget() {
        assertEquals("7.0 GB of 20.0 GB (35%)", heldOfBudget(held = 7_516_192_768, budget = 21_474_836_480))
    }

    @Test
    fun anEmptyCacheStillSaysWhatItMayHold() {
        assertEquals("nothing yet of 2.0 GB", heldOfBudget(held = 0, budget = 2_147_483_648))
    }

    @Test
    fun readsAreAShareAndTheCountsBehindIt() {
        assertEquals("81% from disk (34 hits, 8 misses)", cacheReadsLine(fromCache = 81, fromUpstream = 19, hits = 34, misses = 8))
    }

    /** Before anything has played there is no share to take. */
    @Test
    fun nothingReadYetIsSaidPlainly() {
        assertEquals("nothing read yet", cacheReadsLine(fromCache = 0, fromUpstream = 0, hits = 0, misses = 0))
    }

    /** An absent fact is an omitted row, not an empty one. */
    @Test
    fun aValueNobodyKnowsHasNoRow() {
        assertNull(telegramLine(connected = null))
        assertEquals("connected", telegramLine(connected = true))
        assertEquals("disconnected", telegramLine(connected = false))
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*SystemRowsTest*'
```

Expected: FAIL to compile — `unresolved reference: heldOfBudget`.

- [ ] **Step 3: Implement**

`SystemRows.kt` holds `heldOfBudget`, `cacheReadsLine`, `telegramLine` and a
`humanSize` mirroring `format.js`'s (binary units, one decimal below ten). All
`internal`, all pure, none of them composable.

`feature/system` holds a `SystemViewModel` that reads `CoreClient.catalogFacts()`
and `PlaybackCounters.totals()` and exposes a `SystemUiState`. It follows the
module convention the others set: ViewModels and UiState only, no composables,
with the rationale comment at the top of its `build.gradle.kts`.

`SystemScreen.kt` renders four blocks as heading plus label/value rows, in the
app's own type. **A row whose value is null is not rendered.** There is no
Conversion block — spec §9 says why, and the file's doc comment should say it
too, because its absence is the kind of thing a later reader assumes is an
oversight.

- [ ] **Step 4: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Prove it on the phone**

```bash
./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

Open ⋮ → System and confirm by eye, using `adb shell input swipe X Y X Y 150`
to tap and `adb shell uiautomator dump` to read the screen:

1. The bar shows "Mediagram" on the catalog and "System" on the system screen.
2. Back returns to the catalog; the hardware back button does the same.
3. Catalogue reports a set count matching the library.
4. Cache reports zero held on a cleared cache, and a non-zero figure after playing something.
5. Play a set, return to System, and confirm Upstream has moved.
6. No Conversion block appears.

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): let the app say what it is doing"
```

## Todo list

- [ ] Counters record cache, upstream, fetches and failures, proved on the JVM
- [ ] `CacheDataSource.EventListener` attached
- [ ] App bar with title, conditional back, and the four-item overflow
- [ ] `WithStartOver` retired, Start over unchanged behind the menu
- [ ] System screen with four blocks and no Conversion block
- [ ] Every `ui-mobile` file under 200 lines
- [ ] The six device confirmations made

## Success criteria

A viewer can reach System from anywhere in the catalog, and what it says about
the cache changes after playing a film. `./scripts/check.sh` passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The new module graph entry is forgotten and Hilt fails at runtime | Task 3 step 1 lists `settings.gradle.kts`; the build fails at compile time, not at runtime, because the ViewModel is injected. |
| Counters are written on the loader thread and read on main | `AtomicLong`/`AtomicInteger`, with the reason in the class doc. |
| Retiring `WithStartOver` loses Start over from the setup screens | It never wrapped them — `SetupStep` has its own `Box`. Confirm by reading `MobileApp.kt` before deleting. |
| `LibraryFlow.kt` grows past 200 lines with a fourth branch | Step 6 measures and names the extraction. |
| The bar appears over the player | The player branch is explicitly untouched; device step 5 would show it. |

## Next steps

Phases 4, 5 and 6 all render inside `LibraryScaffold` or read `PlaybackCounters`.
