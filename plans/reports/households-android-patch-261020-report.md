# Households on Android: patch for the final review's findings

Worktree `/home/andre/Workspace/mediagram-hh-patch`, branch `fix/households-android-patch`,
from `main` 6e1bf38d (0.136.0). Released as 0.136.1.

## What was fixed and how

### I1 (Important): the owner's part opened over the picker without the owner's PIN

- Cause, as reported: `HouseholdsOwnerViewModel` read "the owner is watching" from
  `repository.chosenProfileId`, which `ProfileViewModel.reopen()` leaves set, so the
  Profiles page over the picker (`chosen = null`) still drew the part Open. On TV the
  panel then skipped `open()`, so no PIN was asked.
- Fix: the view model no longer reads the repository's choice. It holds its own
  `watcher` flow, set through `watching(id: String?)`, and both `state` (the `combine`)
  and `isOpen()` use it.
- Who calls `watching`: the **gate** (`ProfileGate` on phone, `TvProfileGate` on TV),
  with `LaunchedEffect(owners, chosen?.profile?.id) { owners.watching(chosen?.profile?.id) }`.
  The reviewer suggested the pages call it. I put it in the gate instead, for these
  reasons:
  - The gate is what hands every page its `chosen`. The Profiles page over the picker
    is drawn only while the gate has nobody chosen (null). Settings' Profiles page is
    drawn only inside `content`, which is drawn only for `ProfileUiState.Chosen`. So
    the gate's value is the page's own value, and like the page it comes from
    `ProfileUiState`, never from `chosenProfileId`.
  - The gate sets it on the picker's first frame, before anyone can press Manage
    profiles. An effect on the page would run after the page's first frame. On the
    phone that frame would draw the owner's household list over the picker for a moment.
  - One line per surface. Otherwise `chosen` would have to be threaded through
    `ProfilesPage` → `Household` → `HouseholdsOwnerBlock`, and through
    `TvProfilesPage`, which was at 197 lines.
- This matches the web: `profiles-page-overlay.js:20` passes `profile: null`, and
  `settings-households.js:54` opens the part only when `profile` is the owner.
- `close()` does not clear the watcher. The TV panel closes on every stop, and the
  watching owner must still find the part open when they come back to it.

### M1: a fresh device on a one-household install could flash "Join a household"

- Smallest correct fix:
  - `DefaultHouseholdsRepository.reload()` takes `watchState.syncedOnce.value` before
    it reads the households, and stamps the view with it: a new field,
    `Households.synced` (default `false`).
  - `joinState` treats a view as **not read yet** when a round has been heard
    (`syncedOnce`) but the view was read before it (`!view.synced`) and says none
    joined. That gives `JoinUiState()`: `read = false`, `needsJoin = false`.
  - The round's own re-read is already triggered (the repository re-reads when
    `syncedOnce` changes). When it lands, the stamp is `true` and the view says what
    the round left: joined, so no Join, or not joined, so Join.
- Why this is correct:
  - A stamp of `true` means the core had synced before this households read began,
    so what the read says about `joined` already includes the round.
  - The check uses only one value, the view, so the race between two separate flows
    is gone.
  - If the re-read fails, the picker shows its waiting line with Try again
    (`read = false`). It does not show Join from a pre-round view, and it does not
    offer "Create the first profile".
- Why not drop `syncedOnce` from the `combine`: a stamped view alone cannot tell a
  view from before the round from a current one. And a households re-read that
  returned an identical value would not re-emit.

### M2: the join PIN prompt stayed open saying "did not go through" after a switch that took

- `JoinHouseholdViewModel.pick`: the left-behind callback is now
  `{ ask.cancel(); stillHere.tryEmit(false) }`, as specified.
- `cancel()` bumps the ask's counter, so the outcome that arrives after the throw
  (null) no longer touches the screen. The prompt closes and the picker shows.

### M3: TV focus could land on nothing when returning to the Profiles page

- `TvProfilesPage`'s own effect now only enters the page when there is no row of the
  page to return to.
- Landing back on a row moved into `Household`, which knows `shown`. It requests
  `returningFrom` if that row is drawn, and Manage profiles otherwise: for example the
  This device row after only one household is left. The landing runs once as the
  page shows (`LaunchedEffect(Unit)`), as before: the hub forgets the panel right
  after.
- No conditional modifiers and no new restorers. The existing "lost" hand-off for a
  row that goes while it holds the remote is unchanged.
- `TvProfilesPage.kt` went from 197 to 198 lines.

### M4: some owner changes did nothing and said nothing

- `changeAdmin`, `moveHere` and `create` no longer return early when the chosen
  grown-up has left the view. The request is sent with the chosen id.
- The core (and the fake) checks `owned()` before anything PIN-related, so it answers
  `NotFound`. That goes through the usual `report()`: the notice "That profile is not
  here any more." (the NotFound sentence), `answers + 1`, so the part is drawn again.
  On TV the waiting step gets its answer and shows the notice.
- `create` now passes `adminId` itself, not `admin?.id`, so the core receives the id
  that has gone. The one-of-two input check now compares `adminId`, not the resolved
  grown-up.
- I chose "let the core say it" over a local `report(NotFound)`. The notice is the
  same either way, but this way the core decides, as it does for every other refusal
  in the part. It is also the version that fits: `HouseholdsOwnerViewModel.kt` stays
  at 200 lines.
- Behaviour difference: a watching owner with no PIN held yet is asked their PIN
  first, then told.

### M5: the fake's household create accepted a former profile name

- `FakeProfiles.formerNames` (by profile id, seedable) is the fake's version of the
  `former_names` column.
- `FakeProfiles.unusable` (now `internal`) refuses a name any profile here answers to
  now or had before, like `taken_names` + `name_taken`.
- `FakeHouseholds.create` uses that same check for the new admin's name, as
  `create.rs` reuses the profiles' `taken_names`.
- As a result, profile creation in the fake also refuses former names, matching
  `checks.rs:45`.
- To keep `FakeProfiles.kt` at 321 lines, `setPin`'s `run { … ; Done }` became
  `Done.also { … }`, the idiom the households fake already uses.

## Tests and results

Command, run on the final tree, from `android/`:

`./gradlew --continue testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin`

It finished with **BUILD SUCCESSFUL** (1m 35s, exit 0):

- **3,187 Android unit tests, 0 failed, 0 skipped**, across 13 modules:

  | Module | Tests |
  |---|---|
  | `core:data` | 233 |
  | `core:designsystem` | 56 |
  | `core:playback` | 314 |
  | `core:testing` | 59 |
  | `core:update` | 24 |
  | `feature:catalog` | 445 |
  | `feature:player` | 399 |
  | `feature:setup` | 93 |
  | `feature:stats` | 41 |
  | `feature:system` | 90 |
  | `ui-common` | 93 |
  | `ui-mobile` | 566 |
  | `ui-tv` | 774 |

- `:core:model:test` (a JVM module, which `testDebugUnitTest` does not cover; its
  `Households` changed): **46 tests, 0 failed**.
- `lint`: passed in every module.
- `:ui-tv:compileDebugAndroidTestKotlin`: passed.
- No devices were used.

## TDD evidence (RED before each fix)

Each test below was run before its fix and failed at the line that encodes the defect:

| Finding | Test | RED |
|---|---|---|
| I1 phone | `ui-mobile` `HouseholdsOwnerWatchingTest.openForTheOwnerWatchingAndClosedOverThePickerAfterThem` | `AssertionError` at :35, no owner note over the picker (part drawn open) |
| I1 TV (D-pad) | `ui-tv` `TvOwnersPartOverPickerTest.theOwnerWatchingSeesThePartAndThePickerAfterThemIsAskedThePin` | `AssertionError` at :43, no "The owner's PIN" (the panel opened without it) |
| M1 | `feature:catalog` `JoinHouseholdAfterARoundTest.aRoundHeardBeforeTheHouseholdsAreReadAgainNeverAsksToJoin` | "Expected value to be false" at :55, `needsJoin` true while the re-read was held |
| M2 | `feature:catalog` `ChosenProfileLeftBehindTest.aJoinSaysSoThoughTheHouseholdsCannotBeReadAfterIt` (assertion changed from `DID_NOT_GO_THROUGH` to a closed prompt) | `AssertionError` at :51, prompt still open |
| M3 TV (D-pad) | `ui-tv` `TvHouseholdsFlowTest.backToARowThatWentMeanwhileLandsOnManageProfiles` | `AssertionError` at :191, Manage profiles not focused |
| M4 | `feature:catalog` `HouseholdsOwnerVisitsTest.aGrownUpGoneSinceIsSaidNotSkipped` | `AssertionError` at :88, no notice |
| M5 | `core:testing` `FakeProfilesTest.aFormerNameIsNeverTakenAgain` | first: compile error (no seed for former names); after adding only the seed store: test FAILED (create took the former name) |

After the fixes, all of them passed. Supporting tests added beside them:
`HouseholdsOwnerVisitsTest.onlyTheOwnerTheGateSaysIsWatchingOpensThePart` (the
view-model rule) and the stamp assertions in
`HouseholdsRepositoryTest.rereadsWhenTheFirstRoundLands`.

Harnesses updated because "watching" no longer comes from the core's choice:

- `TvHouseholdsPanelHarness.panel(watching = true)` now calls `owner.watching("a")`.
  The panel is drawn there without a gate.
- `HouseholdsOwnerVisitsTest.theWatchingOwnerSeesThePartOnEveryVisit` does the same.
- `HouseholdsRepositoryTest.readsWhatTheCoreSays` expects `synced = true`.

## Files changed (line counts; budget 200)

| File | Lines |
|---|---|
| `feature/catalog/.../profile/HouseholdsOwnerViewModel.kt` | 197 → **200** |
| `core/testing/.../FakeHouseholds.kt` | 200 → **200** |
| `core/testing/.../FakeProfiles.kt` | 321 → **321** (over 200 before; did not grow) |
| `ui-tv/.../settings/TvProfilesPage.kt` | 197 → **198** |
| `ui-tv/src/test/.../TvHouseholdsFlowTest.kt` | 177 → 193 (two-household seed lifted to top-level `twoHouseholds()`, shared with the new TV test) |
| `feature/catalog/.../profile/JoinHouseholdViewModel.kt` | 151 → 154 |
| `ui-tv/.../profile/TvProfileGate.kt` | 159 → 161 |
| `ui-mobile/.../profile/ProfileGate.kt` | 140 → 142 |
| `core/data/.../HouseholdsRepository.kt` | 77 → 79 |
| `core/model/.../Households.kt` | 90 → 93 |
| new: `ui-tv/src/test/.../TvOwnersPartOverPickerTest.kt` | 46 |
| new: `ui-mobile/src/test/.../settings/HouseholdsOwnerWatchingTest.kt` | 39 |
| new: `feature/catalog/src/test/.../JoinHouseholdAfterARoundTest.kt` | 61 |
| `HouseholdsOwnerVisitsTest.kt`, `ChosenProfileLeftBehindTest.kt`, `FakeProfilesTest.kt`, `TvHouseholdsPanelHarness.kt`, `HouseholdsRepositoryTest.kt` | 143, 75, 85, 113, 110 |

`JoinHouseholdViewModelTest.kt` (200) and `TvProfilesHarness.kt` (199) were not
touched; the new tests went into new files.

## Commits

On `fix/households-android-patch`, on top of 6e1bf38d:

1. `677cac86` test(android): the households fake refuses a former profile name (M5)
2. `52ce0782` fix(android): back on the TV Profiles page lands on Manage profiles when its row has gone (M3)
3. `0adf4fd6` fix(android): a household switch that took closes its PIN prompt (M2)
4. `5d5dfd6f` fix(android): Join a household waits for the households read after the first round (M1)
5. `0dad81e7` fix(android): an owner's change for a grown-up gone since says why (M4)
6. `40ee8f65` fix(android): the owner's Households part over the picker waits for the owner's PIN (I1)
7. `b786c31d` chore: 0.136.1, households on Android fixes. Bumps `Cargo.toml`, `Cargo.lock`, `web/package.json` and `versionName` (versionCode derives 136001), and adds a `docs/project-changelog.md` entry.

`JoinHouseholdViewModel.kt` (M1 and M2) and `HouseholdsOwnerViewModel.kt` with its
test (I1 and M4) were staged per finding, without touching the working tree, so each
commit holds only its own finding. Not pushed, not merged.

## Concerns

- **I1 is fixed at the gate, not on the pages.** The reviewer suggested the pages
  call `watching(chosen?.id)`; the gate calls it instead (reasons above). The effect
  is the same, because each page's `chosen` is the gate's. Reverting to the page
  version means calling `watching` from `HouseholdsOwnerBlock` and from
  `TvProfilesPage`'s `Household`, and accepting a one-frame stale part on the phone.
- **M4 asks the core instead of answering locally.** The notice is the same. But a
  watching owner who has not given their PIN yet is now asked for it before being
  told the grown-up has gone.
- **M1 changes the shared model.** `Households` has a new field, `synced`, so code
  that builds a `Households` by hand gets `false`. In the app only the repository
  builds one. If the re-read after the first round fails, the picker shows its
  waiting line with Try again, not Join.
- **M5 also changes profile creation in the fake.** Creating a profile there now
  refuses former names too, as `checks.rs` does. No existing test seeded any.
- **A similar case was left alone.** The owner's Use on this device has the same
  "left behind, then the re-read fails" path as M2. There the result is a notice in
  the part, not a prompt, and `close()` clears it when the page is left. It was not
  in the findings, so I did not change it.
- **Only the final tree was built and tested.** The intermediate commits were
  checked by reading, not by building each one. The I1 commit relies on the
  `twoHouseholds()` helper lifted in the M3 commit, which comes earlier.
- **Not checked on devices** (by instruction). The new TV landing and the gate's
  watching are covered only by Robolectric D-pad tests.
