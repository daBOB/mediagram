# Code review: the media app answers Spieltisch (provider, snapshot, bridge, image hook)

Worktree `mediagram-spieltisch-app`, branch `feat/spieltisch-app`, uncommitted changes against HEAD `1cc843a9`.
Spec: `phase-02-media-app-provider.md`, including "Design details" and "Result".

## Scope

- 36 modified and 13 new source and test files: about 1.2k lines added, 566 of them generated uniffi bindings.
- Media app: `GameNightProvider`, `GameNightExport`, `GameNightSnapshots`, `NightSnapshotStore`, `GameNightModule`, `MediagramApp`, `LibraryUpdateCoordinator`, manifest.
- Spieltisch: `MediaLibraryBridge`, `MediaImageCache`, `NightSession`, `SpieltischApp`, `GameNightViewModel`, shelf note, manifest.
- Rust: `mediagram-game` (`image_files.rs`, `server.rs`, `host.rs`), `spieltisch-core` (`media_images.rs`, `night.rs`).
- Code I read beyond the diff, to check how the change behaves:
  - `WatchStateRepository` (`reload`, `chooseProfile`, `refreshSnapshot`)
  - `PlayerSession` (the 10 s save tick)
  - `CatalogViewModel` (refresh triggers)
  - `CatalogRepository`, `CoreProvider`
  - the core's `game_night_export.rs`, `game_posters.rs`
  - `TitlePool`, `TvGamesFrame` (lifecycle)

### Tests run

- `cargo test -p mediagram-game -p spieltisch-core -p mediagram-core`:
  - First run: 1 failure, `api::game_night::tests::start_twice_replaces_the_first` (an older test with an ephemeral-port race, see L7). Cargo stopped there.
  - Rerun with `--no-fail-fast`: all green. Core lib 862, game lib 421, spieltisch-core 10, and the fixture contract.
- `./gradlew :feature:games:testDebugUnitTest :spieltisch:testDebugUnitTest`: BUILD SUCCESSFUL. Both tasks were UP-TO-DATE, so these exact inputs had already passed.

## Overall

The fail-closed chain holds on every path I traced, except one class: the snapshot. It is trusted whenever it was built for the same profile id with a compatible kids flag. Its other inputs are kept fresh only by an asynchronous observer, and that observer has a bug that makes it rebuild every 10 s during playback. The tests are behavioural and well aimed. They miss the playback trigger and the same-profile staleness cases.

---

## Critical

### C1. The night is rebuilt every 10 s while a film plays

`android/feature/games/src/main/kotlin/GameNightSnapshots.kt:36`

**How it happens:**
- `watchState.snapshot.map { it.kidsMarks }` has no `distinctUntilChanged()`, and `combine` emits on every upstream emission.
- `PlayerSession` saves progress every `TICK_MS = 10_000` (`feature/player/.../PlayerSession.kt:74,82`).
- Each save goes `setProgress` → `writingAs` → `refreshSnapshot` → `_snapshot.value = …`, a new `WatchSnapshot` whose `kidsMarks` is equal but newly emitted (`WatchStateRepository.kt:444,476-482`).
- Once `changes > 0`, which is after any successful catalog read, every tick runs `export.rewrite()`:
  - `catalog.sets()` over 28k sets
  - `gameNightConfig`
  - `core.gameNightTitles`
  - a 606 KB write

  That is about 2.5 s of work on the emulator, every 10 s of playback.
- Watched marks, watchlist and list edits trigger it too.
- `collectLatest` cancels only the Kotlin side. The core's `blocking(...)` closure and the synchronous `listSets` keep running. On a box where a build takes longer than 10 s, builds overlap.
- `rewrite()` does not log, so the emulator runs could not have shown this.

**Fix:** `watchState.snapshot.map { it.kidsMarks }.distinctUntilChanged()`. Add a test: a progress write does not rebuild (`nightTitlesAsked.size` unchanged). Add a log line in `rewrite()`. See also H1: with invalidation instead of rebuilding, this class of bug costs a file delete, not 2.5 s.

### C2. A stale snapshot can still serve titles a kids profile may no longer see

**Where:**
- `NightSnapshotStore.kt:26-29` (`fits`)
- `GameNightExport.kt:59-60,73,79`
- `GameNightSnapshots.kt:37,48`

**Why it happens:** `fits()` checks only `profileId` and `kidsLocked || !activeIsKids`. The night also depends on:
- `kidsAge` (the limit)
- `hiddenSections`
- the kids marks (an allowlist for unrated sets, `AgeRating.kt:59`)
- the catalog's ratings

None of these are checked. They reach the snapshot only when the observer finishes a rewrite.

**When a stale snapshot that still fits gets served:**

a. **During the rewrite.** A parent lowers the kids profile from 12 to 6, hides a section or removes a Kids mark. Then they press Home and open Spieltisch. Spieltisch asks at once: `SpieltischApp.onCreate` warms up, and `TvGamesFrame` starts the night at composition. The rewrite takes 2.5 s or more and is still running, so the old file is answered and FSK-12 titles go to a profile now limited to 6.

b. **After a failed rewrite.** The old file stays. `rewrite()` writes nothing when `live()` returns null or throws, and the catch at `GameNightSnapshots.kt:48` says "a missing snapshot costs time, never safety". That is false: the snapshot is not missing, it is stale and still fits.

c. **When the observer never starts.** It waits for `updates.changes.filter { it > 0 }` (`:37`). In a process whose catalog refresh never succeeds (channel failure, `No library has been chosen`), no profile or mark edit ever reaches the snapshot.

d. **After a process kill during the rewrite.** The media app is the background app exactly when Spieltisch launches, which is when a low-memory TV box kills it.

e. **When an older build is written last.** A provider build that started before the change can write its pre-change night after the observer's rewrite (`GameNightExport.kt:79`). The last writer wins.

**Fix:** smallest first. All are cheap.
1. Put the kid rules in the header (`kids`, `kidsAge`, `hiddenSections`, `owner`) and compare them in `fits`. `current()` already calls `core.profiles()` (`GameNightExport.kt:72`), so this costs nothing. It closes (a)–(d) for limit and section changes, across process death.
2. For marks and catalog changes, delete the snapshot synchronously before any rebuild, or only delete (see H1). Do not gate on `changes > 0`. Deduplicate the inputs instead, and skip the start-up emission by comparing a marks hash kept in the header.
3. Add a generation counter in `NightSnapshotStore`: `invalidate()` bumps it, and a build keeps its result only if the generation is unchanged since the build began. This fixes (e).
4. Add tests:
   - same profile, limit lowered → the next ask does not serve the old titles
   - the same after a failed rewrite
   - a mark removed → invalidated

## High

### H1. The catalog trigger (question 4): invalidate, do not rebuild

`LibraryUpdateCoordinator.kt:49,55` and `GameNightSnapshots.kt:37-42`

**What counts as a change today:**
- every successful refresh:
  - `Read` on each catalog resubscribe after the 5 s `WhileSubscribed` gap, for example coming back from the player
  - `Published` on every INDEX event, which can arrive during playback
  - `Manual`
- every artwork fetch

Each of these costs a full build, even when nothing changed. `CatalogRepository.refresh` already knows `AlreadyCurrent` from `Updated` (`CatalogRepository.kt:137`) but does not pass it on.

**Assessment:** invalidating is both simpler and safer.
- The observer only deletes the file. The provider becomes the only writer, which removes the observer-versus-provider write race.
- No work runs during playback.
- The stale window shrinks from "length of a build" to the delivery of a flow value.
- The cost is one build, 2.5 s warm or 3.9 s cold on the emulator, on the first ask after a real change. Spieltisch's launch warm-up starts that build about 0.5–1 s before the night query, and with H2 the query joins it. That is well inside the 8 s limit on the emulator. It is unknown on the box.
- Count only real changes (`publishedAt` moved, or artwork fetched), so a no-op read keeps the snapshot.

### H2. No single-flight: the first ask builds twice, and concurrent asks build N times

`GameNightExport.kt:68-82`

Every ask whose snapshot does not fit builds on its own binder thread inside `runBlocking`:
- warm-up plus night query: 3869 ms measured, "built twice at once"
- the observer plus a provider ask
- several image requests while no snapshot fits

Each build holds about 28k `MediaSet` objects and the 606 KB JSON. The binder pool has 16 threads. On a box that is 2–3× slower than the emulator, the cold double build can pass Spieltisch's 8 s limit. The first night is then locked and says the films are unavailable: it fails closed, but on the first night anyone sees.

**Fix:** put a `Mutex` around read → build → write in `current()`. A second asker waits, then finds the fresh file.

## Medium

### M1. The mtime-keyed cache can pin an older night

`NightSnapshotStore.kt:66-67`, and `:49-56` for the read-miss path

**Scenario:**
1. Writer P renames its file into place.
2. Writer O renames its newer file into place and sets `cached = (tO, O)`.
3. P sets `cached = (file.lastModified() = tO, P)`.

From then on `read()` returns P's older content, because the stamp matches, for the life of the process (hours on a TV). This does not depend on mtime granularity. If the clock rounds to whole seconds (older libcore, some filesystems), the read-miss path can pin old content the same way.

**Fix:** `@Synchronized` on `read` and `write`. Take the stamp from `part.lastModified()` before the rename, which preserves it.

### M2. Textless misses cross processes on every poster request and list the whole catalog

**Where:**
- `MediaImageCache.kt:25-26`
- `server.rs:135-138`
- `crates/mediagram-core/src/api/game_night_export.rs:36-45` (committed, but this change is what puts it on a hot path)

**Why:**
- The image route asks for a textless poster first on every `/img/{i}/poster`, and the page sends `no-store`.
- Spieltisch caches only hits. Each miss crosses to the media app, where `textless_poster`:
  - runs `store::list_sets` over the whole catalog
  - runs `poster_keys`
  - builds a new HTTP client

  That happens on every call.
- With a TMDB key, a first fetch holds a media-app binder thread for up to `LOOKUP_TIMEOUT = 20 s`, long after Rust's 300 ms `LOOKUP_WAIT` has given up.
- The emulator figure of 7–28 ms was likely measured without a TMDB key.

**Fix:**
- Remember misses per night in `MediaImageCache`. The media app's `.none` marker already makes a miss stable.
- Build the poster-key map once per catalog version in the core.

### M3. No version bump

Both version lines changed and both still read `0.175.0`. The project CLAUDE.md, § Versioning, says to bump before committing: minor for both. The media app's manifests are root `Cargo.toml`, `web/package.json` and `android/app`. Spieltisch's are `mediagram-game`, `spieltisch-core`, `client/package.json` and `android/spieltisch`.

## Low and nits

- **L1. Check-then-use in `current()`.** `GameNightExport.kt:71,81`: `id` is read before a 2.5 s build, and the build is returned if it fits that `id`. While Spieltisch is in the foreground nobody can switch profile on the box, and `TvGamesFrame` ends the night on `ON_STOP`, so I could not find a way to reach it. Re-reading `chosenProfile()` after the build costs about 1 ms.
- **L2. Cache file names.** `MediaImageCache.kt:25`: `Uri.encode` triples non-ASCII bytes, and `createTempFile` appends about 20 characters. A long show name passes 255 bytes and silently gets no image. Use a SHA-256 hex of the key. The cache also never refreshes when the media app's artwork changes, and never shrinks.
- **L3. Mixed-key installs.** The permission is declared by both apps, which is correct for same-key installs in either order. Installs signed with different keys fail with `INSTALL_FAILED_DUPLICATE_PERMISSION`, not with a refused provider. Examples: a debug Spieltisch beside the channel's release media app, or the media app's debug-signed fast build beside a release Spieltisch. Write this down for development.
- **L4. WEBP sniffing.** `image_files.rs:42`: also check `RIFF` at bytes 0..4.
- **L5. Blocking I/O on `Dispatchers.Default`.** The observer writes and parses 606 KB there. This is moot with H1.
- **L6. Unused `<queries>`.** The media app's `<queries><package com.mediagram.spieltisch>` has no user until the media app launches Spieltisch. The spec settled it, so this is only a YAGNI note.
- **L7. Flaky port test (older than this change).** `start_twice_replaces_the_first` in `mediagram-core/src/api/game_night_tests.rs:14-23`, with the same pattern in `spieltisch-core`. It asserts that an ephemeral port is free while parallel tests bind ports. It failed once here.
- **L8. CursorWindow cliff.** The `/night` row passes through a 2 MB CursorWindow. Above that, the row is dropped and Spieltisch says the films are unavailable: fail-closed, but the message misleads. Log the JSON size in the provider.
- **L9. Doc comments.** `GameNightSnapshots.start()` and `MediaApp.night` have none.
- **L10. Contract literals on both sides.** The authority, the permission and the column names (`MediaLibraryBridge.kt:94-95`) are written out separately in each app. A rename on one side locks Spieltisch without any error. Add a small contract test, like the titles fixture.

## Checked and sound

- **Fails closed by default:**
  - The provider answers `ExportedNight.Locked` on any throw.
  - The bridge unlocks only on an explicit integer 0 and requires a JSON array. A timeout, a refusal or no app all give `Unanswered`.
  - `titles_of` turns bad JSON into no titles.
  - `TitlePool::new` drops titles that are not `kids_ok` again when the night is locked.
- **Profile switch:** the snapshot is labelled with the `watchState` profile and checked against the core's `chosenProfile`. A mismatch, or a kids flag that changed, answers locked (tested).
- **Corrupt files:** a header that does not parse or titles cut short give `null` and a rebuild (tested). The `.part` file plus rename is atomic within one directory.
- **Paths:**
  - The media app uses the key only as a map lookup. The textless key maps to the catalog's `poster_key` before any path is built.
  - Spieltisch's encoded name cannot contain `/`. `..` becomes `poster-..` (tested).
  - `/img` takes an index and answers only the box itself.
  - The image hook is only ever asked for keys in tonight's pool, so the cache cannot serve another profile's title.
- **Provider:**
  - `openFile` accepts `"r"` only.
  - Insert, update and delete throw.
  - The manifest permission plus `enforceCallingPermission` give a second check.
  - No `grantUriPermissions`.
  - `<queries><provider>` is correct.
- **Hilt:** an entry point from a provider before `Application.onCreate` is supported, and the singletons are double-checked. `CoreProvider` builds lazily, so injecting into `MediagramApp` costs little.
- **Threads:**
  - `spawn_blocking` around the foreign trait is correct; a Kotlin throw becomes a `JoinError` and then `None`.
  - Nothing new blocks Kotlin's main thread.
- **Night lifetime:** a night never outlives Spieltisch leaving the foreground (`TvGamesFrame` `ON_STOP`), so every return asks again.
- **In-app night:** unchanged with `images: None` (tested). Its only differences: extensionless files are now typed by their bytes, and a file is read before it is typed.
- **Rules:**
  - Every new code file is under 200 lines.
  - No plan or phase references in code, comments or test names.
  - Tests check behaviour, and each was watched failing.

## Missing tests (ranked)

1. A progress or watched write does not rebuild the night (C1).
2. Same profile, rules tightened (limit lowered, section hidden, mark removed): the next ask, before the observer runs and after a failed rewrite, does not serve the old titles (C2).
3. A marks change invalidates. Only the profile and catalog inputs are tested today.
4. Two concurrent asks build once (H2). After two writes, `read()` returns what is in the file (M1).

## Recommended actions

1. C1: one-line `distinctUntilChanged`, plus its test.
2. C2 and H1 together:
   - kid rules in the header, checked in `fits`
   - the observer invalidates instead of rebuilding, with no `changes > 0` gate
   - a generation guard
   - count only real catalog changes
3. H2 and M1: a `Mutex` in `current()`, and synchronize the store.
4. M2: per-night miss cache in `MediaImageCache`.
5. M3: bump both version lines before committing.

## Unresolved questions

- How long does a night build take on the real TV box? That decides whether the cold first ask fits in 8 s, and how costly C1 is until fixed.
- Should the media app run TMDB textless fetches on demand for Spieltisch at all, given a binder thread held for up to 20 s? Or prefetch them when the snapshot is built?
- Does the box's Android round `File.lastModified()` to whole seconds? This is moot if M1's lock lands.

---

# Re-review (2026-11-11): the fingerprinted night

Same worktree, uncommitted changes against `1cc843a9`, after the redesign:
- `NightInputs` holds a SHA-256 fingerprint.
- `GameNightExport` answers single-flight under one `Mutex` and keeps a night only if the fingerprint still matches after the build.
- Images are checked against the night `/night` last served.
- Also new:
  - a per-night memory of textless misses in Rust
  - hashed cache names
  - `RIFF` required for WEBP
  - `MediaAppContractTest`
- The observer and `LibraryUpdateCoordinator.changes` were removed; `MediagramApp` and `LibraryUpdateCoordinator` match HEAD again.

### Tests run

- `cargo test --no-fail-fast -p spieltisch-core -p mediagram-game`: green.
  - game lib 422
  - spieltisch-core 11
  - the fixture contract 1
- `./gradlew :feature:games:testDebugUnitTest :spieltisch:testDebugUnitTest`: BUILD SUCCESSFUL. Both tasks were UP-TO-DATE, so these inputs had already passed.

## The earlier findings

| # | Status | Evidence |
|---|---|---|
| C1 rebuild on every progress save | **Fixed** | Nothing runs in the background. `GameNightBuildTest.progressAndOtherWatchStateNeverRebuild` |
| C2 stale snapshot for a kids profile | **Mostly fixed**; two gaps, R1 and R2 | The limit, sections, marks and catalog are each tested with the rebuild failing, in-process and in a fresh process |
| H1 catalog trigger | **Fixed** by removal | The build happens on the next ask; the warm-up's build is joined (4493 ms, one build) |
| H2 single-flight | **Fixed** | `lock.withLock` covers fingerprint, read, build, re-check and write. `asksThatComeTogetherBuildOnce` |
| M1 mtime cache race | **Fixed** | No mtime cache. The only writer is under the lock; `kept` is read and written only under it |
| M2 textless misses | **Partly fixed**, see R3 | The miss memory works only for misses that answer within 300 ms. The core still lists the whole catalog on every textless call |
| M3 version bump | **Open** | Both lines still read 0.175.0 |
| L1 check-then-use | Fixed | The fingerprint is re-read after the build. `aNightWhoseInputsMoveWhileItIsBuiltIsNotKept` |
| L2 cache names | Fixed | SHA-256 names. Copies still never refresh when the media app's artwork changes (cosmetic) |
| L3 mixed-key installs | Documented | `docs/system-architecture.md:1368` |
| L4 WEBP | Fixed | `image_files.rs:42` |
| L5, L6 | Gone or fixed | |
| L7 port-flake test | Open, older than this change | `start_twice_replaces_the_first` |
| L8 cursor size | Logged | `GameNightExport.night()` logs the length |
| L9 doc comments | Mostly fixed | Only `NightInputs.fingerprint` lacks its own comment |
| L10 contract strings | Fixed | `MediaAppContractTest`, with Gradle inputs on the media app's manifest and provider |

## Coverage of the fingerprint (question 1)

What `gameNightConfig` and `gameNightTitles` read, and whether the fingerprint covers it:

- **`profile.kids` and the derived `kidsLimit`:** covered. A profile with no explicit age derives a limit (`kidsAge == 6 ? 6 : 12`), and the derived value is hashed.
- **`hiddenFrom`:** covered. Its owner and kids rule is applied before hashing.
- **Kids marks:** covered. `kids` plus `kidsFromSix` become `kidsMarks`. Marks are install-wide (`kids_marks.rs:12`), not per household. `snapshot()` answers empty on failure, and empty marks is the stricter night.
- **Profiles in other households:** `profiles()` lists this device's profiles. A chosen id missing from that list gives `null`, which locks. There is no guest-profile concept in the model (grep).
- **`fsk`:** comes only from the installed index (`listing.rs:121`, `certifications(conn)`), so the catalog marker covers it.
- **Set membership, kind, credits:** from the index, covered.
- **The marker itself:**
  - The version directory is `v-<pushed_at>`.
  - Re-installing the same snapshot names it `v-<ts>-1`, which reads back as the same `publishedAt` with the same content.
  - Package installs use `pointer.created_at`, and the origin tells packages from the channel.
  - `merge` publishing is gone.
  - I found no in-place rewrite of an installed index with different content.
  - A version that cannot be read with sets > 0 answers locked.

  So the index side is sound.
- **Not covered:** see R1 and R2.

### R1. Device-fetched descriptions can move a title into a hidden section with no fingerprint change

**Severity:** Medium-High. It is a fail-open path for a parental rule, though not for the age rule.

**How it happens:**
1. `sectionKey()` (`Sections.kt:20-27`) answers `"anime"` when `MediaSet.anime` is true.
2. `anime` is `original_language == "ja" && genres ∋ "Animation"` (`shows/anime.rs:32-40`).
3. Where the index row has none, both genres and language fall back to this device's fetched sidecar `details.db` (`listing.rs:85-107`).
4. The enrichment run writes descriptions after artwork (`enrich/fetch.rs:140-176`).

**Failure:**
1. A Japanese animated film has uploader artwork but no genres in the index.
2. A library update fetches its description. No poster arrives, so `catalogFacts()` is unchanged: same `publishedAt`, same counts.
3. The film now sits in Anime in the library, but the fingerprint is unchanged.
4. A kids profile with Anime hidden is still served the old night, which lists the film.

**Fix:** add the sidecar to the catalog marker. Either:
- a new `CatalogFacts` field such as `described`, a row count of `details.db`, which is cheap; or
- the store's size and modification time.

**Test:** in `GameNightSnapshotTest`, flip only a set's `anime`. Today it is served the old night.

### R2. The fingerprint has no code version, so a rule fix shipped in an update is not applied to Spieltisch

**Severity:** High under the fail-closed rule; the fix is one line.

**What is not covered:** these are code, not data:
- `GAME_NIGHT_KIDS_LIMIT`
- `forKidsProfile` and `kidsVerdictOf`
- `sectionKey`
- the `kidsLimit` derivation
- the core's `kids_ok` rule, for example "one unsafe episode makes the show unsafe"

`files/game-night/night.snapshot` survives an app update, and the fingerprint hashes only data.

**Failure:** an update that fixes a kids rule has no effect on Spieltisch until a catalog publish, a poster count change, a mark or a profile edit. That is exactly when the fix matters. The same goes for a change to the title JSON's shape: after an update the old snapshot reads as no films (fail-closed, but broken).

**Fix:** provide `NightSnapshotStore` with the app's `longVersionCode` from `PackageInfo` in `GameNightModule`. Write it into the header and reject a snapshot from any other version. Alternatively, hash it into the fingerprint.

**Test:** a snapshot written under version N is rebuilt under N+1.

## New issues from the redesign (question 2)

### R3. The textless miss memory loses exactly the slow misses

`spieltisch-core/src/media_images.rs:46-55`, `poster_lookup.rs:19-21`

**Severity:** Medium (performance).

**Failure:**
1. `textless_file` wraps the lookup in a 300 ms `timeout`.
2. When that fires, the `async move` block is dropped at `fetch(...).await`, so `misses.insert` never runs. The `spawn_blocking` task finishes, detached.
3. A crossing slower than 300 ms is therefore never remembered. Each one goes through the binder plus the core's full `list_sets` and `poster_keys`, unchanged since the first review, plus a TMDB check when there is no `.none` marker yet.
4. Every later poster request repeats that crossing. The test passes only because its fake answers at once.

**Fix:**
- Do the bookkeeping in a spawned task that outlives the caller: `tokio::spawn(async move { let found = fetch(..).await; if found.is_none() { misses.insert(key) }; found })`, then await its handle under the timeout.
- Optionally, mark a key as pending, so a second request during a slow first one doesn't cross again.
- Test with a fake whose `fetch` sleeps 400 ms.

### R4. A sign-out during a build holds the lock until the next sign-in

`GameNightExport.kt:131-163` with `CatalogRepository.sets()`

**Severity:** Low-Medium; rare, but the effect spreads to the whole app.

**Failure:**
1. `build()` takes the `core` captured under the lock, but `catalog.sets()` resolves the core again through `coreProvider.awaitCore()`.
2. A sign-out (`forget`/`resetAccount`) during the 4–5 s build makes `awaitCore()` suspend until someone signs in, while still holding `lock`.
3. Every later `/night` and first image check parks a binder thread inside `runBlocking`. Spieltisch retries after each 8 s timeout. Once about 15 threads are parked, the media app's whole IPC stalls.

**Fix:** either
- list the sets from the captured `core` (`core.listSets().map(::toMediaSet)`), or
- wrap the locked body in `withTimeout`. Suspension in `awaitCore` is cancellable, so the lock is released.

### R5. `served` is written after the lock is released

`GameNightExport.kt:107`

**Severity:** Low.

**Failure:**
1. The warm-up's ask and the night's ask finish the locked section in order.
2. Their assignments to `served` can still land in the opposite order.
3. If an input changed between the two asks, `served` names the older night.

Spieltisch only asks for keys in the pool it was answered with, so this has no effect today.

**Fix:** set `served` inside `current()` under the lock, or compare a sequence number.

### R6. The first ask after a library change now always builds, and the box may not fit 8 s

**Severity:** Medium. It is a functional risk, not a safety one, and it is unmeasured.

**Why:**
- With no background work, the first ask after any marker change pays a build. Marker changes include every publish and every enrichment that fetched posters, which change the count.
- A build is 4.2–4.7 s on the x86 emulator.
- On an ARM box about 2× slower, the night's ask, which joins the warm-up's build about 1 s in, can wait past 8 s.
- It then opens locked, with "films aren't available", for that whole night. It fails closed, but on the most common path after an update.

**Next step:** measure on the box. If it doesn't fit, options include:
- re-ask `/night` when a film game is picked after an unanswered start, or
- raise the limit while the shelf shows "Starting".

### Checked and sound

- **Deadlocks:**
  - The coroutine `Mutex` is not taken re-entrantly. `image()` → `servedNight()` → `night()` takes it only when `served` is null, never while holding it.
  - Lock order with `CoreProvider`'s mutex is one-way.
  - Nothing under the lock uses `Dispatchers.Main`.
- **A failed build under the lock:** `withLock` releases. `night()` answers `Locked` and sets `served = Nothing`, so images are refused. `kept` and the file are left as they were; the fingerprint keeps them from being served.
- **Images after the active profile changes during a session:**
  - The served night keeps its titles, the same as a night hosted in the media app. This can only happen through sync from another device, because Spieltisch ends its night on `ON_STOP`.
  - A fresh media-app process answers `/night` first, then checks images against the current inputs. That fails closed.
- **Failed reads:** a failed `profiles()`, `chosenProfile()`, `snapshot()` or `catalogFacts()` gives `null` (locked), stricter marks, or a different fingerprint and a rebuild. All fail closed.

## Test quality (question 3)

The new tests check behaviour and aim at the earlier findings:
- progress does not rebuild
- inputs moving during a build
- single-flight with real threads
- the served-night image check, including a fresh process
- each tightening (limit, section, mark, catalog rating) with the rebuild failing, in-process and on disk
- corrupt files
- the contract test across both apps' manifests
- textless asked once per night

**Missing:**
1. Only a set's anime or section flipping, with the catalog marker unchanged: would fail today (R1).
2. A snapshot from another app version: would fail today (R2).
3. A textless miss slower than 300 ms is remembered: would fail today (R3).
4. A catalog with sets but no readable version answers locked. This is cheap and would pass; it pins a fail-closed branch.

## Rules (question 4)

- Every code file is under 200 lines. The largest new ones are `GameNightExport.kt` at 126 and `MediaLibraryBridge.kt` at 115.
- No plan or phase references in code (grep; one hit in `server.rs` is HTML `<h1>`).
- Doc comments are present except on `NightInputs.fingerprint`.
- No YAGNI or DRY concerns. The contract test replaces the duplicated literals' risk.
- Version bump (M3) still missing.

## Verdict

**Not commit-ready yet.** Two cheap blockers remain on the kids-safety bar, plus the version bump:

1. **R2:** a code version in the snapshot header, or in the fingerprint, with a test. About 5 lines.
2. **R1:** device-fetched descriptions in the catalog marker. One field in the core's `CatalogFacts` plus a test, or a size-and-mtime stat of `details.db`.
3. **M3:** bump both version lines.

R3–R6 can follow in a later change. R3 and R4 are small enough to do now.
