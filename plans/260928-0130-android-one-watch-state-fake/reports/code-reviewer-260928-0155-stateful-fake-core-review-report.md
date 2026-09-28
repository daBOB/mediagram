# Code review: stateful FakeCore watch state and contract cases

Worktree `/home/andre/Workspace/mediagram-watch-state`, branch `refactor/android-one-watch-state-fake`, uncommitted, off `main` @ 220fa71e. Version 0.69.5.

## Scope
- Files: `android/core/testing/src/main/kotlin/testing/{FakeWatchState.kt (new, 196 lines), FakeCore.kt (515), CoreContract.kt (340)}`, `android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt`, version manifests, changelog.
- Checked against the Rust code: `state/rows.rs`, `state/editors_choice.rs`, `state/lists.rs`, `state/profiles.rs`, `state/schema.rs`, `state/mod.rs`, `api/state.rs`, `rows_tests.rs`, `kids_profile_tests.rs`.
- Method: I copied `android/` into a scratchpad and ran 7 mutations plus 5 probe tests there. The worktree was not touched.

## Gate (run by me)
| Command | Result |
|---|---|
| `./gradlew :core:testing:testDebugUnitTest :core:data:testDebugUnitTest :core:rust:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| `./gradlew testDebugUnitTest` (whole project) | BUILD SUCCESSFUL |
| Forced rerun with per-test logging (`:core:testing`, `:core:data`) | 161 passed, 0 failed. `FakeCoreContractTest` has 26 cases (9 old + **17** new, not 21). `WatchStateRepositoryTest` has 9. |

## Mutations (scratch copy)
| Mutation | What fails |
|---|---|
| Fake: a repeat finish keeps the first stamp (cc8bf95d's shape) | only `finishingATitleAlwaysReStampsAndClearsItsPosition` |
| Fake: finishing leaves the position (40a43589's shape) | only `finishingATitleAlwaysReStampsAndClearsItsPosition` |
| Fake: drop `MAX(now, removed_at+1)` on re-finish | nothing |
| Fake: drop `MAX(now, finished_at+1)` on un-mark | nothing |
| Fake: a second watchlist `true` bumps `added_at` | nothing |
| **Repository** `markFinished` skips `setWatched` when already watched (the real cc8bf95d bug) | nothing in this change. Only `ProgressRecorderTest` (feature/player, hand-written call-log fake) catches it. |
| **Repository** `markFinished` = `clearProgress` + `setWatched` (the real 40a43589 bug) | nothing in this change. Only `ProgressRecorderTest` catches it. |

## (a) Would each contract case pass on the real core? Yes, all 17.
- Order-sensitive cases: `progressListsNewestFirst` and the re-stamp case put `delay(5)` between writes stamped by `now_ms()`. The only way they could fail is the wall clock stepping backwards (for example an NTP correction), which is negligible.
- Tie-insensitive cases: pinning the editor's choice works even if both writes land in the same millisecond, because the old pick is retired before the insert (`editors_choice.rs:34-42`).
- Global tables (Kids, editor's choice) cannot leak between cases. `RealCoreContractTest.kt:29` gives each case a fresh UUID data directory.
- Foreign keys: every per-profile case creates its profile first. `foreign_keys` is on (`state/mod.rs:113`).
- Snapshot for an unknown profile: no case uses one. Both fake and real would answer empty per-profile lists plus the global Kids and editor's choice.

## Findings

### High
**H1. The two historical regressions lived in the Kotlin repository, and nothing in this change pins them there.**
- Where: `android/core/data/src/main/kotlin/WatchStateRepository.kt:147-149` (`markFinished`). Both cc8bf95d and 40a43589 changed this method, not the core.
- What the contract covers: those shapes if they reappear inside the fake, and then only through one case.
- What catches the real bugs today: only `feature/player/.../ProgressRecorderTest.kt:35,61`, whose `FakeWatchStateRepository` call log phase 03 plans to delete.
- The implementer report says 40a43589 is also covered "by every other finish-true assertion". That is false. The mutation fails exactly one case.
- Fix, part 1: add this case to `WatchStateRepositoryTest` now. I verified it passes on the current code and fails under the cc8bf95d mutation.
  ```kotlin
  @Test
  fun markingAWatchedTitleFinishedAgainReStampsItAndDropsThePosition() = runTest {
      val core = FakeCore().apply { profiles = listOf(CoreProfile("p1", "Alice")); chosen = "p1" }
      val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
      repository.reload()
      repository.markFinished("s1")
      val first = repository.snapshot.value.watched.single().finishedAt
      repository.setProgress("s1", 30.0, 100.0)
      repository.markFinished("s1")
      assertTrue(repository.snapshot.value.watched.single().finishedAt > first)
      assertEquals(emptyList(), repository.snapshot.value.progress)
  }
  ```
- Fix, part 2: no end-state check on a stateful fake can see 40a43589's two-call shape, because both writes land. The only structural guard is decision 5 (drop `clearProgress` from the repository). Land it in phase 02, before phase 03 deletes `ProgressRecorderTest`'s call-log fake. Otherwise keep that assertion until it does.

### Medium
**M1. The fake ignores whether a profile exists. The real core enforces it in three places.**
- Probes that fail on FakeCore but pass on the real core:
  - A write under a profile nobody created is stored. The real core drops it on a foreign-key violation (`schema.rs:26,35,42,67`), and `createCollection` returns null.
  - `deleteProfile` leaves the profile's progress, watched marks, watchlist and lists readable. The real core deletes them (`ON DELETE CASCADE`).
  - `FakeCore.chosenProfile()` (FakeCore.kt, `= chosen`) returns a seeded id without checking it exists. The real `profiles::chosen` checks (`profiles.rs:80-92`).
- Why it matters for phases 02-03 (question (d)): feature tests will seed `FakeCore().apply { chosen = … }`. A test that seeds only `chosen`, or deletes a profile while something is playing, passes on the fake for a reason the real core refuses.
- Fix (about 10 lines):
  - `FakeWatchState(now, exists: (String) -> Boolean)`. Per-profile writes return early when the profile is missing, and `createCollection` returns null.
  - Add `forget(id)` and call it from `FakeCore.deleteProfile`.
  - `chosenProfile() = chosen?.takeIf { id -> profiles.any { it.id == id } }`.
  - Add two contract cases, both of which pass on the real core: `removingAProfileTakesItsWatchStateWithIt` and `aWriteForAProfileNobodyCreatedIsDropped`.
  - Current users are unaffected: `WatchStateRepositoryTest` seeds both `profiles` and `chosen`.

**M2. Seeded profile ids collide with created ones (FakeCore.kt `nextProfileId = 1`).**
- Scenario: `FakeCore().apply { profiles = listOf(Profile("p1", …)) }` followed by `createProfile(...)` mints `"p1"` again. My probe confirmed it.
- This predates the change, but used to only confuse the profile list. Now the two profiles also share watch state.
- Fix: skip ids that are already in `profiles` when minting (`while (profiles.any { it.id == "p$n" }) n++`).

**M3. Two `WatchStateRepositoryTest` cases are named for the snapshot but never check it.**
- `reloadPopulatesProfilesAndTheChosenOnesSnapshot` asserts `WatchSnapshot.Empty` (line 31).
- `choosingAKnownProfileSetsItAndLoadsItsSnapshot` never asserts the snapshot (lines 52-55).
- A reload or choose that never reads the snapshot passes both. With the stateful fake the fix is one line each: seed `core.setProgress("p1", "set-1", 12.0, null)` first, then assert `set-1` is in the published snapshot.
- Separately, `aWriteWithNoChosenProfileDoesNothing` now inspects only `p1`. A write under any other id would pass, which is weaker than the old any-write log. The report's "strengthened" wording overstates it.

**M4. `FakeWatchState` is unsynchronized, but the core it stands in for is serialized. This will surface in phase 03.**
- The real core runs every call under `Mutex<LocalState>` (`state/mod.rs:45-48,78`). `DefaultWatchStateRepository` defaults to `Dispatchers.IO`.
- Today every test passes `Unconfined` or `Main.immediate`. Once phase 03 fixtures run writes on more than one thread, the plain `LinkedHashMap`s can throw ConcurrentModificationException or lose updates, giving flaky tests.
- Fix: `@Synchronized` on the public methods (the class has one instance per FakeCore).

### Low
- **L1. Rules the fake implements but nothing pins** (their mutations survive):
  - The re-finish and un-mark `MAX(…+1)` clamps. These cannot be pinned on the real core offline. Pin them in `FakeCoreTest` with `clock` stepped backwards.
  - A live watchlist or Kids mark is not bumped by a second `true`. This can be a contract case that passes on the real core (`rows.rs:153,181`): add A, delay, add B, delay, add A again, expect `[B, A]`.
  - `addingToTheWatchlistTwiceIsNotTwoRows` and `markingATitleForKidsTwiceIsNotTwoRows` cannot fail on the fake at all (a map key cannot repeat). They only mean something on the device.
- **L2. `FakeWatchState.kt:102-103` clamps a first finish.** It stamps `maxOf(now, 1)`, while the real core inserts plain `now_ms()` and only applies the MAX on conflict (`rows.rs:114-116`). They differ only when a test clock returns 0 or less (probe: fake 1, real 0). Fix: `existing?.let { maxOf(now(), (it.removedAt ?: 0) + 1) } ?: now()`.
- **L3. A retired FakeCore keeps answering.** The real core answers empty/None/false after `retireLocalState` (`mod.rs:80-81`). This makes reset tests stricter, not looser. Document it, or model it with a `retired` flag.
- **L4. NaN handling differs.** Rust `f64::max(NaN, 0.0)` gives 0.0, and SQLite stores a NaN duration as NULL. `coerceAtLeast` keeps NaN. This is an edge case only.
- **L5. Tie-break when two rows share a timestamp (question (d)).** The default monotonic clock never produces equal timestamps, so phase 02-03 tests cannot observe the difference unless one sets a constant clock. Add one sentence to the `clock` KDoc: equal timestamps sort by insertion in the fake, and the real core's order for them is unspecified.
- **L6. Mistakes in the implementer report:**
  - It says 21 new cases; there are 17.
  - It gives the editor's-choice tie-break as `set_id DESC`; it is `ORDER BY marked_at DESC, set_id`, i.e. ascending.
  - It says the old `setProgress` log proved nothing about which profile was written to. It did: the log string carried `p1`.
- **L7. Style.**
  - No plan, phase or candidate references anywhere in the code, comments or test names (grep-verified). Comments explain the why.
  - `FakeWatchState.kt` is 196 lines. `FakeCore.kt` is 515 (was 496): over the 200-line norm in `docs/code-standards.md` (which only Rust `src/` enforces) and under detekt's `LargeClass` limit of 600. The next growth should split out the profile half.
  - `KidsMark` duplicates `Listed` on the strength of a future age limit, which is a speculative (YAGNI) justification.
  - `FakeWatchState` and `monotonicClock` could be `internal`.

## (c) Other FakeCore users
- The whole-project suite is green.
- No setup, settings, system, TV or mobile production path writes watch state. Grep shows every write comes from `CatalogViewModel`, `PlayerMarksController` or `ProgressRecorder`, and their tests still use the hand-written repository fakes.
- The `CoreInterface by FakeCore()` wrappers (`WatchStateOwnershipTest`, `ProfileOwnershipTest`, `SettingsWatchOwnershipTest`) override `snapshot` and every write they call.
- Result: no test passes for a changed reason. Only the three `WatchStateRepositoryTest` cases changed intent (see M3).

## Positive
- The fake follows the Rust SQL rule by rule: upsert, tombstones, the `WHERE removed_at IS NOT NULL` idempotency, the collection ownership checks, and one clock shared by progress and watched marks (which is what `NextUp` compares).
- The clock is wrapped as a lambda, so reassigning it mid-test takes effect.
- `freshProfile` avoids a case passing on the real core only because a write was silently dropped.

## Unresolved questions
- Should the profile-existence modelling (M1) land in this phase or in phase 02? It changes no current test, and phase 03 is where it starts to matter.

**Status:** DONE_WITH_CONCERNS
