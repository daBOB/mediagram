# Phase 7: Refreshing the library, safely, from the menu

**Deliverable:** a `Refresh library` item in the overflow menu that re-reads the
pinned index and re-groups the shelves, a row on the System screen saying how
old the catalogue is and what the last refresh did — and, first, a refresh that
stops deleting the artwork the previous phase fetched.

## Context

- CLAUDE.md § Surface Parity — the web player is the reference for a decision already made
- `web/public/lib/status-lines.js:43` — `refreshLine`, and `web/public/lib/colophon.js:26` — `catalogueAge`, the two this mirrors
- `web/test/status-lines.test.ts:44-84` — what the reference is pinned to
- `crates/mediagram-core/src/api/channel.rs:69` — `refresh_library`, which already does the work
- `crates/mediagram-core/src/api/catalog.rs:112` — `facts`, which reports everything but this
- `crates/mediagram-core/src/api/refresh.rs:101-126` — `install_staged` and `swap_current`: why the timestamp is a directory name, and why that directory is not a safe place to keep anything
- `crates/mediagram-core/src/api/artwork.rs:60-88` — `fetch_posters`, which keeps things there anyway
- `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt` — the cold flow that has to re-run

## Key insight

The app refreshes the library exactly once — when the catalog screen first
subscribes — and has no way to ask again short of being restarted. Every piece
needed already exists: `refreshLibrary` is on `CoreClient`,
`CatalogRepository.refresh()` calls it, and the overflow menu already knows how
to carry an action with a reason for being disabled. What is missing is a
trigger the catalog's flow restarts on.

The timestamp the System screen wants is not stored anywhere new either. A
refresh installs its snapshot as `<data_dir>/catalog/v-<pushed_at>/` and points
the `current` symlink at it (`refresh.rs:106-126`), so **the age of the
catalogue is the name of the directory `current` resolves to**. Reading it is a
`read_link`, not a query.

**Task 1 is a defect fix and comes first.** The previous phase's device run
measured fetched posters going 0 → 236 → 0 across an app restart: a refresh
removes the version directory the posters are stored in, and a refresh happens
on every launch. Making refresh a button a viewer can press would turn a bug
that already destroys artwork into one they can aim. So the storage moves before
the button ships.

**This is not the uploader's `rescan`.** `mediagram rescan`
(`crates/mediagram/src/commands/rescan.rs`) rebuilds `library.db` from channel
captions on the machine that holds the uploader session; a phone has neither
that database nor that session. The client half is re-reading the pinned
snapshot, which is what the web player calls Refresh — so this calls it Refresh
too, for the reason Surface Parity gives: the naming was decided once already.

---

### Task 1: Artwork that survives a refresh

**Files:**
- Modify: `crates/mediagram-core/src/api/artwork.rs:60-88`
- Modify: `crates/mediagram-core/src/api/catalog.rs:69-77` (`poster_path`) and its `count_posters`
- Test: the module the artwork tests already live in — find it before writing a new one

**Interfaces — Produces:** no new public surface. `poster_path` and `fetch_posters`
keep their signatures; only where the bytes live changes.

**This is a defect fix, and it was measured, not inferred.** A device run watched
the poster count go 0 → 236 → 0 across an app restart.

**The cause.** `fetch_posters` writes into
`std::fs::canonicalize(catalog::current_dir(core))` (`artwork.rs:65`), which
resolves the `current` symlink to `<data_dir>/catalog/v-<pushed_at>/`, and puts
the images in its `posters/` subdirectory (`:80`). `install_staged`
(`refresh.rs:106-109`) calls `remove_dir_all` on exactly that directory before
renaming the newly downloaded one into place, and `version_name` is
`v-<pushed_at from the pinned caption>` — **the same name every time**, as long as
the same index stays pinned. A refresh runs on every catalog load. So every
launch deletes every poster that was ever fetched.

The TMDB provider-id cache goes with it: `TmdbClient::with_cache(client, &key,
&dir, &language)` (`artwork.rs:83`) is handed the same directory, so each refresh
also throws away the lookups that cost the API requests.

**The fix.** Artwork is a fact about a title, not about a snapshot of the index.
Fetched posters and the provider cache move to `<data_dir>/artwork/`, which no
refresh touches — `install_staged` and `remove_other_versions` both operate
strictly inside `<data_dir>/catalog/`.

- [ ] **Step 1: Write the failing tests**

Name them for the behaviour, not for this defect:

```rust
    /// Artwork outlives the catalogue it was fetched for. A refresh replaces
    /// the version directory wholesale, and a poster stored inside one would
    /// be thrown away every time the app asked the channel for the index.
    #[test]
    fn a_fetched_poster_is_found_after_the_catalogue_is_replaced() { /* ... */ }

    /// A published package carries its publisher's own chosen art, so it
    /// stays authoritative for the keys it covers — a fetch only ever ran
    /// for a title the package had nothing for.
    #[test]
    fn a_packages_own_poster_wins_over_a_fetched_one() { /* ... */ }

    /// One title held in both places is one poster, not two. The System
    /// screen reports this count beside the set count, and a number that
    /// double-counts reads as artwork that is not there.
    #[test]
    fn a_key_held_in_both_places_is_counted_once() { /* ... */ }

    /// The key is still validated before it reaches the filesystem. A second
    /// lookup location must not become a second way past that check.
    #[test]
    fn an_invalid_key_resolves_to_nothing_in_either_location() { /* ... */ }
```

Write the bodies against whatever temp-directory helper the neighbouring tests
already use. The fourth matters most: `poster_path` guards with
`mlib_spec::package::poster_key_is_valid` at `catalog.rs:70` **before** building
any path, and the new branch must sit behind that same guard, not beside it.

- [ ] **Step 2: Run them**

```bash
cd /home/andre/Workspace/mediagram-android
cargo test -p mediagram-core
```

Expected: the first three FAIL.

- [ ] **Step 3: Implement**

Add an `artwork_dir(core) -> PathBuf` returning `core.data_dir.join("artwork")`
and create it on demand, beside the existing `catalog::dir`. `fetch_posters`
writes there and hands `TmdbClient::with_cache` the same directory. It still
reads `library.db` through the resolved current directory — that part is correct
and stays.

`poster_path` checks the version directory first, then the artwork directory,
both behind the existing key validation. `count_posters` counts the union of the
two by stem, not the sum of two counts.

- [ ] **Step 4: Run everything**

```bash
cargo test -p mediagram-core
cargo build -p mediagram-core
ANDROID_HOME=/home/andre/android-sdk ./scripts/check.sh
```

`cargo build -p mediagram-core` is not redundant with `check.sh`. A
workspace-wide build unifies `crates/mediagram`'s feature flags into the graph;
only the single-crate build compiles this the way
`scripts/build-android-core.sh` does, and a defect that hid behind exactly that
difference has already cost this plan a round.

- [ ] **Step 5: No migration, on purpose**

Posters already on the device are gone — the refresh at the last launch removed
them, which is the defect. There is nothing to move, so there is no migration
step. The next fetch re-downloads them.

- [ ] **Step 6: Prove it on the phone**

```bash
cd android && ./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

1. Fetch posters from the menu; note the count the System screen reports.
2. Force-stop the app and relaunch it.
3. The System screen reports the same count, and the shelves still show artwork rather than initials.
4. Fetch again: the report says everything was already held, and no images are downloaded a second time.

A real phone is attached and signed in to a real library. **Never "start over" on
it** — that discards the user's real Telegram session. `adb shell input tap` is
unreliable against Compose here; use `adb shell input swipe X Y X Y 150`, with
`uiautomator dump` and `adb exec-out screencap` to read the screen. Never end a
turn to wait for the device; loop inside one command.

- [ ] **Step 7: Commit**

```bash
git add crates/mediagram-core/
git commit -m "fix(core): keep fetched artwork out of the catalogue it was fetched for"
```

---

### Task 2: The core says when the catalogue was pushed

**Files:**
- Modify: `crates/mediagram-core/src/dto.rs:117-122`
- Modify: `crates/mediagram-core/src/api/catalog.rs:112-126`
- Test: `crates/mediagram-core/src/api/catalog.rs` (a `#[cfg(test)]` module at the foot, or the file the other api tests use — follow whichever the neighbouring modules already do)

**Interfaces — Produces:** `CatalogFacts.published_at: Option<i64>`, seconds since
the epoch, `None` when nothing is installed or the name cannot be read.

- [ ] **Step 1: Write the failing test**

The parsing is the part worth pinning, so give it a name of its own and test
that rather than the filesystem:

```rust
    /// The version directory is named for when the index was pushed, so the
    /// catalogue's age needs no separate record. A name that is not one of
    /// ours reads as unknown rather than as a wrong date.
    #[test]
    fn a_version_directory_name_carries_its_push_time() {
        assert_eq!(pushed_at_of("v-1758300000"), Some(1_758_300_000));
        assert_eq!(pushed_at_of("v-0"), Some(0));
        assert_eq!(pushed_at_of("current"), None);
        assert_eq!(pushed_at_of("v-"), None);
        assert_eq!(pushed_at_of("v-not-a-number"), None);
        assert_eq!(pushed_at_of(""), None);
    }
```

- [ ] **Step 2: Run the test**

```bash
cd /home/andre/Workspace/mediagram-android
cargo test -p mediagram-core a_version_directory_name
```

Expected: FAIL to compile — `cannot find function pushed_at_of`.

- [ ] **Step 3: Implement**

```rust
/// When the index in a version directory was pushed, from its name.
///
/// `refresh.rs` names every installed version `v-<pushed_at>` and points
/// `current` at it, so the catalogue's age is already written down and needs
/// no second record that could disagree with it.
fn pushed_at_of(name: &str) -> Option<i64> {
    name.strip_prefix("v-")?.parse().ok()
}
```

and in `facts`, beside the origin:

```rust
    let published_at = std::fs::read_link(&dir)
        .ok()
        .and_then(|target| target.file_name().map(|name| name.to_string_lossy().into_owned()))
        .as_deref()
        .and_then(pushed_at_of);
```

`read_link` is deliberate: `current` is the symlink, and resolving it is what
turns "the catalogue" into "the version it points at". A `canonicalize` would
work too but reaches the filesystem twice for a string already in hand.

Add `pub published_at: Option<i64>` to `CatalogFacts` (`dto.rs:117`), documented
as seconds since the epoch, `None` when nothing is installed.

- [ ] **Step 4: Run the tests**

```bash
cargo test -p mediagram-core
cargo build -p mediagram-core
```

The second command is not redundant. A workspace-wide build unifies
`crates/mediagram`'s feature flags into the graph; only the single-crate build
compiles this the way `scripts/build-android-core.sh` does.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/dto.rs crates/mediagram-core/src/api/catalog.rs
git commit -m "feat(core): report when the installed catalogue was pushed"
```

---

### Task 3: Refresh library, and shelves that change

**Files:**
- Modify: `android/core/data/src/main/kotlin/CatalogRepository.kt`
- Create: `android/core/data/src/main/kotlin/RefreshLog.kt`
- Modify: `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt`
- Modify: `android/ui-mobile/src/main/kotlin/AppChrome.kt`, `android/ui-mobile/src/main/kotlin/LibraryFlow.kt`
- Test: `android/core/data/src/test/kotlin/RefreshLogTest.kt`, and the existing `CatalogRepositoryTest.kt`

**Interfaces — Consumes:** `CatalogFacts.published_at` (Task 2), `MenuActions`
(`AppChrome.kt:71`), `CatalogRepository.refresh()`.
**Produces:** `RefreshLog` with `fun record(outcome: RefreshOutcome)` and
`fun last(): RefreshOutcome?`; `CatalogViewModel.reload()`.

- [ ] **Step 1: The outcome, and where it is kept**

`RefreshOutcome` is a sealed interface with three cases — `Updated`,
`AlreadyCurrent`, and `Refused(reason: String)` — mirroring the web's
`updated` / `unchanged` / `kept` (`web/test/status-lines.test.ts:73-77`).

`RefreshLog` is a `@Singleton` holding the last one and nothing else. It is the
same shape as `PlaybackCounters` and exists for the same reason: two screens ask
about one process-lifetime fact, and neither owns it. Keep it to the two methods
above; a log that accumulated history would be answering a question nobody asked.

The comparison that decides `Updated` from `AlreadyCurrent` is
`catalogFacts().publishedAt` read before and after the call. Both reads are
local — a row count and a directory listing — and the refresh between them is a
network round trip, so the second read costs nothing worth avoiding.

Write this in `DefaultCatalogRepository.refresh()`, which already wraps the call
in `runCatching`. A failure records `Refused` with the core's own sentence
(`coreSentence()`, as `CatalogViewModel` already uses) and still returns the
`Result` it returns today — the catalog's existing warning path is not replaced.

- [ ] **Step 2: Write the failing tests**

```kotlin
    /** A refresh that installs a newer snapshot is the case worth reporting. */
    @Test
    fun aNewerSnapshotReadsAsUpdated() = runTest {
        val core = FakeCore(publishedAt = listOf(1_758_300_000L, 1_758_900_000L))
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.Updated, log.last())
    }

    /**
     * Asking again when nothing has been pushed is the ordinary case, and it
     * is not a failure — the library is current, which is what was wanted.
     */
    @Test
    fun anUnchangedSnapshotReadsAsAlreadyCurrent() = runTest {
        val core = FakeCore(publishedAt = listOf(1_758_300_000L, 1_758_300_000L))
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.AlreadyCurrent, log.last())
    }

    /** A refusal keeps the sentence the core wrote, which says what to do about it. */
    @Test
    fun aFailedRefreshKeepsTheCoresOwnSentence() = runTest {
        val core = FakeCore(refreshFails = "the channel could not be reached")
        val log = RefreshLog()

        repositoryOver(core, log).refresh()

        assertEquals(RefreshOutcome.Refused("the channel could not be reached"), log.last())
    }

    /** Before anything has been asked, there is nothing to report. */
    @Test
    fun anUntouchedLogHasNothingToSay() {
        assertNull(RefreshLog().last())
    }
```

`FakeCore` already exists at `android/core/data/src/test/kotlin/FakeCore.kt` and
already implements `catalogFacts()`. Widen it rather than writing a second fake.
Read it first: its constructor shape decides how `publishedAt` is best handed in,
and the two-element list above is a suggestion, not a requirement.

- [ ] **Step 3: Run them**

```bash
cd /home/andre/Workspace/mediagram-android/android
./gradlew :core:data:testDebugUnitTest
```

Expected: FAIL to compile — `unresolved reference: RefreshLog`.

Note the target. `:core:data` is where these tests live; a
`compileDebugKotlin` would not run them.

- [ ] **Step 4: Implement, then make the catalog re-emit**

`CatalogViewModel.state` is built from a cold `flow { }` handed to `stateIn`,
so it runs once per subscription and never again. Put a trigger in front of it:

```kotlin
    private val reloads = MutableStateFlow(0)

    /** Re-reads the library from the channel and re-groups it. */
    fun reload() {
        reloads.update { it + 1 }
    }

    val state: StateFlow<CatalogUiState> = reloads
        .flatMapLatest {
            flow {
                emit(CatalogUiState.Loading)
                // ... the body that is there today, unchanged
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)
```

`flatMapLatest` rather than `flatMapConcat`: a second request while the first is
in flight should replace it, not queue behind it.

**Read `CatalogViewModel.kt` as it stands before editing.** The previous phase
modified it, so the body above is described rather than quoted — move what is
there, do not retype it from this file.

- [ ] **Step 5: Put it in the menu**

`MenuActions` (`AppChrome.kt:71`) gains `onRefresh: () -> Unit` and
`refreshDisabledReason: String? = null`, and the overflow gains a fifth item
reading **`Refresh library`** — no ellipsis, because it does the thing rather
than opening something, which is the distinction the existing four already draw.

Place it above `Start over`, so the two that reach the network sit together and
the one that discards the library stays last and alone.

Disabled while a reload is running, with the reason `"Refreshing…"` — the same
shape `fetchPostersDisabledReason` already uses in `LibraryFlow.kt`.

**No result dialog.** The poster fetch has one because its result is four counts
nobody can see; a library refresh's result is the shelves themselves. A failure
already reaches the viewer through the warning sentence `CatalogUiState.Ready`
carries and the catalog already renders.

Because the menu is the same everywhere it opens, `Refresh library` is reachable
from the System and TMDB key screens too, where a reloading catalog is invisible.
So the action clears the open positions and returns to the catalog — you asked to
refresh the library, and the library is what you are shown.

- [ ] **Step 6: Check the line budget**

```bash
./gradlew :ui-mobile:testDebugUnitTest :core:data:testDebugUnitTest :feature:catalog:testDebugUnitTest
wc -l ui-mobile/src/main/kotlin/*.kt core/data/src/main/kotlin/*.kt feature/catalog/src/main/kotlin/*.kt
```

`LibraryFlow.kt` was 188 lines before this task and gains a menu action, a
disabled reason and the return-to-catalog behaviour. It will cross. Extract the
positions and their `BackHandler`s into
`android/ui-mobile/src/main/kotlin/LibraryPositions.kt` — the extraction the
previous phase already named as the one to make when this happened.

Under 200 means under. 200 exactly is over.

- [ ] **Step 7: Commit**

```bash
git add android/
git commit -m "feat(android): ask the channel for the library again"
```

---

### Task 4: The row that says when

**Files:**
- Modify: `android/feature/system/src/main/kotlin/SystemUiState.kt`, `android/feature/system/src/main/kotlin/SystemViewModel.kt`
- Modify: `android/ui-mobile/src/main/kotlin/SystemRows.kt`, `android/ui-mobile/src/main/kotlin/SystemScreen.kt`
- Test: `android/ui-mobile/src/test/kotlin/SystemRowsTest.kt`

**Interfaces — Consumes:** `CatalogFacts.published_at` (Task 2), `RefreshLog`
(Task 3).
**Produces:** `internal fun refreshLine(publishedAt: Long?, outcome: RefreshOutcome?, now: Long): String?`

- [ ] **Step 1: Write the failing test**

The reference is `refreshLine` in `web/public/lib/status-lines.js:43` over
`catalogueAge` in `web/public/lib/colophon.js:26`, pinned by
`web/test/status-lines.test.ts:44-84`. Read all three before writing this.

```kotlin
package ui

import data.RefreshOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How old the catalogue is, and what the last attempt to replace it did.
 * Mirrors `refreshLine` in the web player's `status-lines.js`, over the same
 * day-granularity wording its `catalogueAge` uses.
 */
class RefreshLineTest {

    private val now = 1_758_900_000_000L
    private val threeDaysAgo = now - 3L * 86_400_000L

    @Test
    fun ageAloneIsWhatThereIsToSayBeforeAnythingIsAsked() {
        assertEquals("published 3 days ago", refreshLine(threeDaysAgo, null, now))
    }

    @Test
    fun aRefreshThatFoundSomethingSaysSoBesideTheAge() {
        assertEquals(
            "published 3 days ago · refreshed just now",
            refreshLine(threeDaysAgo, RefreshOutcome.Updated, now),
        )
    }

    /** Finding nothing new is the ordinary outcome, and not a failure. */
    @Test
    fun aRefreshThatFoundNothingSaysTheLibraryIsCurrent() {
        assertEquals(
            "published 3 days ago · already current",
            refreshLine(threeDaysAgo, RefreshOutcome.AlreadyCurrent, now),
        )
    }

    /**
     * The one reading on this screen that is a warning. A catalogue that
     * could not be replaced looks exactly like a current one, and nothing
     * else here would say otherwise.
     */
    @Test
    fun aRefusalSaysWhyAndWhatIsStillBeingServed() {
        assertEquals(
            "refresh refused — the channel could not be reached, still serving the one published 3 days ago",
            refreshLine(threeDaysAgo, RefreshOutcome.Refused("the channel could not be reached"), now),
        )
    }

    /** A clock that disagrees with the publisher's is likelier than a catalogue from the future. */
    @Test
    fun aCatalogueFromTheFutureIsNotDescribedAsSuch() {
        assertEquals("published just now", refreshLine(now + 86_400_000L, null, now))
        assertEquals("published today", refreshLine(now, null, now))
        assertEquals("published yesterday", refreshLine(now - 86_400_000L, null, now))
    }

    /** Weeks past a fortnight, months past two — the web's thresholds, not new ones. */
    @Test
    fun anOlderCatalogueIsSaidInCoarserUnits() {
        assertEquals("published 2 weeks ago", refreshLine(now - 20L * 86_400_000L, null, now))
        assertEquals("published 3 months ago", refreshLine(now - 100L * 86_400_000L, null, now))
    }

    /** Nothing installed is not a date; the row is left out rather than shown blank. */
    @Test
    fun aCatalogueWithNoPushTimeHasNoRow() {
        assertNull(refreshLine(null, null, now))
    }
}
```

- [ ] **Step 2: Run it**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*RefreshLineTest*'
```

Expected: FAIL to compile — `unresolved reference: refreshLine`.

- [ ] **Step 3: Implement**

Put it in `SystemRows.kt` beside the other sentences, or in its own file if that
would cross 200 — it was 86 lines before this task, so measure rather than guess.

`now` is a parameter with no default, for the reason the web's is: a function
that reads the clock itself cannot be tested against one.

**Deliberate difference from the reference, and record it in a comment:** the
web's `refreshLine` returns `"read from this machine"` whenever `origin` is not
`"package"`, because a locally-built index has no publisher to be older than.
Android's origin is always `"channel"` — its catalogue is pushed to Telegram and
pulled down — so that branch would say something false on every phone. It is
left out, and the age is what the row leads with instead.

- [ ] **Step 4: Render it**

`SystemUiState` gains `publishedAt: Long?` and `lastRefresh: RefreshOutcome?`;
`SystemViewModel` fills them from `catalogFacts()` and the injected `RefreshLog`.

The row goes in the `Catalogue` block between `Holds` and `Schema`, labelled
`Refresh`:

```
Catalogue
  Source    this machine
  Holds     538 playable sets, 162 posters
  Refresh   published 3 days ago · already current
  Schema    v3, expected by this build
```

A null value means no row at all, which is the rule the block already follows.

- [ ] **Step 5: Run everything**

```bash
cd /home/andre/Workspace/mediagram-android
ANDROID_HOME=/home/andre/android-sdk ./scripts/check.sh
```

- [ ] **Step 6: Prove it on the phone**

```bash
cd android && ./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

1. Open System: the Refresh row shows a real age, not a placeholder.
2. Open the menu, tap `Refresh library`: the app returns to the catalog and the shelves reload.
3. Open System again: the row now reads `… · already current`.
4. Turn off the network, tap `Refresh library`: the catalog keeps its shelves and shows a warning; the Refresh row reads `refresh refused — …, still serving the one …`.
5. Turn the network back on and refresh again: the row returns to `already current`.
6. Confirm `Refresh library` is greyed with `Refreshing…` while a reload is in flight.

`adb shell input tap` is unreliable against Compose here — use
`adb shell input swipe X Y X Y 150`, with `uiautomator dump` and
`adb exec-out screencap` to read the screen. Never end a turn to wait for the
device; loop inside one command.

- [ ] **Step 7: Commit**

```bash
git add android/
git commit -m "feat(android): say how old the library is and what the last refresh did"
```

## Todo list

- [ ] Fetched artwork and the provider cache live outside the catalogue snapshot
- [ ] A poster survives a refresh, proved on the phone across a force-stop
- [ ] A package's own art stays authoritative for the keys it covers
- [ ] `CatalogFacts` carries the installed snapshot's push time
- [ ] `RefreshLog` records updated / already current / refused
- [ ] The catalog's flow re-runs on request
- [ ] `Refresh library` in the overflow menu, disabled while running, returning to the catalog
- [ ] The Refresh row on the System screen, matching the web's wording and thresholds
- [ ] The deliberate difference from `refreshLine`'s origin branch written down in a comment
- [ ] Every `ui-mobile`, `core/data` and `feature/catalog` file under 200 lines
- [ ] The six device confirmations made

## Success criteria

Fetched artwork survives a refresh. A viewer can ask for the library again from
any screen and watch the shelves change, and the System screen says how old the
catalogue is and what the last attempt did. `./scripts/check.sh` passes, and
`cargo build -p mediagram-core` on its own does too.

## Risk assessment

| Risk | Mitigation |
|---|---|
| `flatMapLatest` restarts the load on every recomposition rather than on request | The trigger is a `MutableStateFlow<Int>` incremented only by `reload()`; the initial `0` is the one automatic load, which is today's behaviour. |
| Returning to the catalog from the System screen feels like being thrown out | Device step 2 is where it gets judged. If it reads badly, that is a finding for the user, not a silent redesign. |
| Comparing `publishedAt` before and after misreads a same-second push as unchanged | A push in the same second as the previous one would need two index pushes within a second; the count is also re-read, so the shelves are correct regardless — only the word in one row would be. |
| `LibraryFlow.kt` crosses 200 | Task 3 step 6 measures and names the extraction. |
| Moving artwork out of the snapshot breaks the published-package path, which carries its own posters | `poster_path` checks the version directory first, so a package stays authoritative for its own art. The second and fourth tests in Task 1 pin exactly this. |
| The core change is invisible to `scripts/check.sh` | Tasks 1 and 2 each compile `-p mediagram-core` alone, which is how the Android library is actually built. |

## Next steps

This is the last phase. After it: the version bump across all three manifests
(a feature, so minor — `0.4.0` → `0.5.0`), the whole-branch review, and the
merge decision.
