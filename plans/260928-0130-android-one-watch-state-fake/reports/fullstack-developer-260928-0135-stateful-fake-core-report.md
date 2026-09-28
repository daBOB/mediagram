# Stateful FakeCore watch state + contract cases — phase 01 report

Plan: `plans/260928-0130-android-one-watch-state-fake/` · Phase: `phase-01-stateful-fake-core-and-contract.md`
Worktree: `/home/andre/Workspace/mediagram-watch-state` (branch `refactor/android-one-watch-state-fake`, off `main` @ `220fa71e`)

Revised after `code-reviewer-260928-0155-stateful-fake-core-review-report.md` (DONE_WITH_CONCERNS).
Every High/Medium finding and the requested Low items are addressed below; §"Fixes applied after
code review" maps each to what changed. Two counts in the first version of this report were wrong
and are corrected here: the new contract-case count, and 40a43589's coverage.

## Files

- **New** `android/core/testing/src/main/kotlin/testing/FakeWatchState.kt` (228 lines) — the
  watch-state half of `FakeCore`: progress, watched marks, watchlist, Kids, editor's choice,
  collections, `monotonicClock()`, a profile-existence guard, and the cascade `forget()` runs.
- **Modified** `android/core/testing/src/main/kotlin/testing/FakeCore.kt` (526 lines; was 496
  before this phase) — `clock` (reassignable `var`, defaults to `monotonicClock()`), a
  `watchState` field wired to it and to `profiles` for existence checks, the 12 previously-stubbed
  state methods delegating to it, `chosenProfile()` re-checking existence, `deleteProfile()`
  cascading into `watchState.forget()`, and `createProfile()` skipping ids already seeded into
  `profiles`.
- **Modified** `android/core/testing/src/main/kotlin/testing/CoreContract.kt` (386 lines) — 20 new
  `@Test` cases (29 total; listed below) plus a `CoreInterface.freshProfile` helper. Unchanged:
  `RealCoreContractTest.kt` (inherits every case automatically) and `FakeCoreContractTest.kt`
  (already the right fixture).
- **Modified** `android/core/testing/src/test/kotlin/testing/FakeCoreTest.kt` (71 lines; was 23) —
  two cases pinning the `MAX(…+1)` re-finish/un-mark clamps against a clock stepped backward, which
  cannot be pinned on the real core offline (its clock is wall time).
- **Rewritten** `android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt` (10 tests, was 9) —
  the private `StateCore` fake is gone; every test runs on `FakeCore` directly.
- **Version/docs**: `Cargo.toml`, `Cargo.lock` (5 workspace-member `version` lines only — verified
  with `cargo metadata --offline`), `web/package.json`, `android/app/build.gradle.kts`
  (`versionName` only, `versionCode` untouched) → `0.69.5`. `docs/project-changelog.md` — new entry
  at the top, "Internal" section, no user-visible behaviour claimed.
- `plan.md` — phase 01 row marked done; phase 02's row now notes that dropping `clearProgress`
  from the repository is the only guard against 40a43589's two-call shape once phase 03 deletes
  `ProgressRecorderTest`'s call-log fake, and must land first.
- Phase file: all five Todo boxes checked.

## Rule → contract case

| Rule (phase file Requirements) | Contract case |
|---|---|
| Progress upsert (one row, newest value wins) | `settingProgressTwiceKeepsOneRowWithTheNewestValue` |
| `at` clamped ≥ 0 | `progressNeverStoresANegativePosition` |
| snapshot order newest-first | `progressListsNewestFirst` |
| `clearProgress` — plain delete, stamps nothing | `clearingProgressForgetsThePositionWithoutMarkingItWatched` |
| finish(true): re-stamp every time (incl. repeat), clear position | `finishingATitleAlwaysReStampsAndClearsItsPosition` |
| finish(false): tombstone, position untouched | `takingAMarkBackNeverTouchesAPosition` |
| re-mark after removal visible again | `aRemarkAfterARemovalIsWatchedAgain` |
| watchlist idempotent add | `addingToTheWatchlistTwiceIsNotTwoRows` |
| watchlist tombstone + re-add | `removingFromTheWatchlistThenAddingBackIsVisibleAgain` |
| a live mark is not re-dated by a second `true` | `reAddingALiveWatchlistMarkDoesNotMoveItToTheFront` |
| Kids idempotent add | `markingATitleForKidsTwiceIsNotTwoRows` |
| Kids tombstone + re-add | `removingATitleFromKidsThenAddingBackIsVisibleAgain` |
| Kids GLOBAL across profiles | `kidsMarksAreSharedAcrossEveryProfile` |
| editor's choice: pin replaces last pick | `pinningAnEditorsChoiceReplacesTheLastPick` |
| editor's choice: unpin clears it | `unpinningTheEditorsChoiceClearsIt` |
| lists: create/rename/delete for the owner | `creatingRenamingAndDeletingAListWorksForItsOwner` |
| lists: refused off another profile | `listsBelongToOneProfileAndAreUntouchableByAnother` |
| lists: `setInList` idempotent add/remove | `addingAndRemovingATitleInAListIsIdempotent` |
| a profile's removal takes its watch state with it (FK cascade) | `removingAProfileTakesItsWatchStateWithIt` |
| a write for a profile nobody created is dropped (FK violation) | `aWriteForAProfileNobodyCreatedIsDropped` |

**20 new cases, not 21 as the first version of this report said** (29 total against the original
9). `finishingATitleAlwaysReStampsAndClearsItsPosition` is the direct pin for cc8bf95d (finish
without re-stamp): it finishes an already-finished title with no un-mark in between and requires
`finishedAt` to move forward. **40a43589 (the two-call finish that leaves the position) is pinned
by exactly that one case, not "every finish-true assertion" as the first version of this report
claimed** — the case's `assertEquals(emptyList(), first.progress)` is the only assertion in the
whole suite that a two-call `clearProgress` + `setWatched` shape would fail, and only inside
`FakeWatchState` itself. Neither regression's *historical* home — `WatchStateRepository.markFinished`
— is reachable through `CoreContract` at all; see §"Fixes applied after code review", item 1, for
where that gap is actually closed.

Three cases (`progressListsNewestFirst`, `finishingATitleAlwaysReStampsAndClearsItsPosition`,
`reAddingALiveWatchlistMarkDoesNotMoveItToTheFront`) `delay(5)` between the writes their assertion
orders by — the real core's clock is wall time in milliseconds; back-to-back calls could otherwise
tie. Every other new case avoids asserting order or an exact timestamp against the real core, per
the phase's "assert relations, not exact values" instruction. Every case but
`aWriteForAProfileNobodyCreatedIsDropped` creates its own profile via `freshProfile()` first: real
`progress`/`watched`/`watchlist`/`collections` tables have a foreign key to `profiles(id)` (Kids
and the editor's choice do not) — `aWriteForAProfileNobodyCreatedIsDropped` deliberately skips
`freshProfile()` to pin that exact constraint.

## Fixes applied after code review

1. **(High) The historical regressions' actual home, `WatchStateRepository.markFinished`, had no
   pin.** Added `WatchStateRepositoryTest.markingAWatchedTitleFinishedAgainReStampsItAndDropsThePosition`
   (the case the review verified passes today and fails if cc8bf95d's shape were put back). Its
   second guard, against 40a43589's two-call shape, is structural, not a test: `plan.md`'s phase 02
   row now says dropping `clearProgress` from the repository must land before phase 03 deletes
   `ProgressRecorderTest`'s call-log fake, which is what actually catches that shape today.
2. **(Medium, landed now) Profile existence.** `FakeWatchState` takes an `exists: (String) ->
   Boolean` alongside its clock; every per-profile write (`setProgress`, `setWatched`,
   `setWatchlisted`, `createCollection`, `renameCollection`, `deleteCollection`,
   `setInCollection`) returns early (or `null`/`false`) when it doesn't hold, the same shape a
   foreign-key violation degrades into on the real tables. `forget(profileId)` drops a profile's
   progress/watched/watchlist/collections, called from `FakeCore.deleteProfile` — the same cascade
   `ON DELETE CASCADE` runs there. `FakeCore.chosenProfile()` now checks the id still names a
   profile, matching `profiles::chosen`'s own re-check. Two contract cases pin this:
   `removingAProfileTakesItsWatchStateWithIt`, `aWriteForAProfileNobodyCreatedIsDropped`.
3. **Seeded ids no longer collide with minted ones.** `FakeCore.createProfile` skips past any
   `"p$n"` already present in `profiles` before minting.
4. **Two `WatchStateRepositoryTest` cases now actually check a snapshot.**
   `reloadPopulatesProfilesAndTheChosenOnesSnapshot` seeds `core.setProgress("p1", …)` ahead of
   `reload()` and asserts it lands in `repository.snapshot.value`, rather than asserting
   `WatchSnapshot.Empty` (which a reload that never read the snapshot would also satisfy).
   `choosingAKnownProfileSetsItAndLoadsItsSnapshot` seeds a watchlist mark and asserts
   `chooseProfile` loaded it. `aWriteWithNoChosenProfileDoesNothing` no longer inspects one
   profile's snapshot (which a write under any other id would still pass); it now overrides
   `setProgress` on a `CoreInterface by FakeCore()` delegate and asserts the override never ran —
   the same "any write at all" guarantee the old call-log gave.
5. **`FakeWatchState` is `@Synchronized` throughout** — one lock per instance, matching
   `state_db`'s single `Mutex` on the real core.
6. **The `MAX(…+1)` clamps are pinned fake-side.** `FakeCoreTest` gained
   `reFinishingClampsAheadOfARemovalEvenWhenTheClockStepsBack` and
   `unmarkingClampsAheadOfItsFinishEvenWhenTheClockStepsBack`, both stepping `core.clock` backward
   — the one way to exercise the clamp that the real core's wall clock cannot offer offline. Added
   `reAddingALiveWatchlistMarkDoesNotMoveItToTheFront` to `CoreContract` (passes on the real core
   too): add A, delay, add B, delay, re-add A → `[B, A]`, not `[A, B]`.
7. **A genuinely first finish stores `now()` plain**, matching the real `INSERT`; the `MAX` clamp
   now only applies once a row — live or tombstoned — already exists, matching the real
   `ON CONFLICT`. Previously every first finish was clamped to at least `1`, which only the real
   core and the fake could disagree on, and only for a test clock returning `0` or less.
8. Skipped, per instruction (a retired `FakeCore` still answering — a low-priority, separate
   concern from this phase's rules).
9. **`clock`'s KDoc** now says equal timestamps sort by insertion order in the fake, and the real
   core's order for a tie is unspecified.
10. **`KidsMark` is gone.** Kids and the watchlist now share one `Mark(at, removedAt)` — the
    speculative duplication for a future age limit was premature; that field lands on `Mark` (or
    whatever replaces it) when the age-limit work actually needs it, not before.

## WatchStateRepositoryTest — changed intent

10 tests (was 9); 4 had their assertions changed:

- **`reloadPopulatesProfilesAndTheChosenOnesSnapshot`, `choosingAKnownProfileSetsItAndLoadsItsSnapshot`**
  (item 4 above): both previously could pass without the snapshot ever having been read. Now
  strengthened, not just changed — a real seeded write must reach `repository.snapshot.value`.
- **`aWriteWithNoChosenProfileDoesNothing`** (item 4 above): restored to its original strength —
  intercepting the call, not inspecting one profile's downstream state. The intermediate version
  this report first described (checking only `core.snapshot("p1")`) was weaker than the original
  and is gone.
- **`setKidsWritesGloballyRatherThanUnderAProfile`**: `setKids` takes no profile id, so the old
  assertion (the acting profile's own cached snapshot updated) could not have caught a scoping
  bug — there is no id to scope wrongly in the first place. Still asserts a *second* profile's
  snapshot, read directly from the core, shows the same mark — the actual "global" claim the name
  makes, which the old hand-rolled `StateCore.setKids` happened to guarantee by construction
  (it looped every profile itself) rather than by proving the core's own table is unscoped.

Correcting this report's own first version: `setProgressWritesUnderTheChosenProfileAndRefreshesTheSnapshot`'s
intent did **not** change — the old call-log string carried the actual `profileId` argument
(`"setProgress p1 set-1"`), so it already proved the write reached `p1`, not a hardcoded literal.
What's new is only the added check that a second, uninvolved profile stayed empty, which the old
single-profile test had no second profile to check against; kept, not "strengthened" as this
report first said.

One test **simplified without a behaviour change**: `choosingAnUnknownProfileChangesNothing`
dropped `StateCore`'s `refuseChoose` flag — redundant even before this phase, since the old fake's
`chooseProfile` already checked `profileList.none { it.id == id }` on its own; `FakeCore`'s real
`chooseProfile` does the same check unconditionally.

## Gate

```
./gradlew testDebugUnitTest lint :core:rust:compileDebugAndroidTestKotlin
```
`BUILD SUCCESSFUL` on the full, post-fix tree (re-run after every change in this report, most
recently after the `FakeWatchState` doc trim). Whole project's `testDebugUnitTest` and `lint`,
plus `:core:rust:compileDebugAndroidTestKotlin` (pulls in all 20 new `CoreContract` cases through
`RealCoreContractTest`). No device run, per instruction (both devices in use); phase 04 runs it.

- Type check / compile: pass. Only pre-existing warnings in unrelated files (opt-in markers, one
  deprecated `WindowWidthSizeClass` usage, one pre-existing "always true" instance check in
  `PlayerFactoryTest.kt`).
- Unit tests: pass, whole project.
- `:core:rust:compileDebugAndroidTestKotlin`: pass (device run deferred to phase 04).

## What only the device run may disagree on

- **Tie-break order under identical timestamps**: real `ORDER BY updated_at/finished_at/... DESC`
  has no secondary sort key; ties fall back to whatever order SQLite's b-tree happens to return.
  `FakeWatchState` breaks ties by insertion order (a stable sort over a `LinkedHashMap`), which is
  very likely what SQLite does too but isn't a documented guarantee on either side. No case asserts
  order under a tie — the three order-sensitive cases `delay(5)` apart specifically to avoid this —
  so this is a latent difference, not a proven one.
- **Editor's choice merge tie-break** (`ORDER BY marked_at DESC, set_id` — ascending on `set_id`,
  corrected from this report's first version, which said `set_id DESC`) isn't modeled:
  `FakeWatchState` only ever holds one live pick, matching every path reachable through
  `CoreInterface` alone (no merge call is exposed there). Deliberate, not a gap: the plan's
  decision 2 scopes this phase's editor's-choice rule to what `set_editors_choice`/`editors_choice`
  alone can express.
- **`clean_name`'s length cap** (`MAX_NAME` in `state/profiles.rs`) is not replicated in the fake's
  list-name cleaner — pre-existing gap, `FakeCore`'s own `cleanProfileName` already had it before
  this phase; unrelated to this phase's rules, left as-is.
- **A retired `FakeCore` keeps answering** (the real core answers empty/`None`/`false` after
  `retireLocalState`). Left alone per instruction (item 8 above) — makes reset tests stricter on
  the real core, not looser, so nothing in this phase depends on it.

The foreign-key gap this section previously listed (writes under a profile nobody created; a
deleted profile's state surviving; `chosenProfile()` not re-checking existence) is fixed — see
§"Fixes applied after code review", item 2 — and is no longer a fake/real divergence.

## Todo (phase file)

All five boxes checked: contract cases first, stateful FakeCore + clock, `WatchStateRepositoryTest`
on `FakeCore`, whole-project gate green, version/changelog.

**Status:** DONE
**Summary:** Code review's High and Medium findings, and the requested Low items, are fixed: profile existence now guards every per-profile write and cascades on delete, the historical regressions' actual home (`WatchStateRepository.markFinished`) has a direct pin plus a plan note for its structural guard, seeded/minted profile ids no longer collide, two vacuous snapshot assertions now check real state, the any-write guard is restored, `FakeWatchState` is synchronized, its clamps are pinned with a backwards clock, a first finish stores plain `now()`, and the speculative `KidsMark` type is gone. 20 new `CoreContract` cases (not 21), `40a43589` pinned by one case plus a plan-level note (not "every finish-true assertion") — both corrected from this report's first version. Whole-project gate green.
**Concerns/Blockers:** None blocking. Two latent, unexercised fake/real divergences remain (tie-break order under identical timestamps; editor's-choice merge tie-break, deliberately out of scope) — worth a glance when phase 04 runs the device suite, not before.
