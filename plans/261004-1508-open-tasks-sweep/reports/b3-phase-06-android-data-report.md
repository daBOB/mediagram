# B3 phase 06: Android model, repository, view models, Kids marks

2026-10-04, fullstack-developer subagent, worktree branch `worktree-agent-ac2ce5e3f3a3e5567`.
Reset to main `e3056cfd` (0.108.1), merged phase 05 (`worktree-agent-a8dee5dd090a40eca`, merge `d4f8517b`).
Plan `260928-0047-profile-roles-pins-kids-age-limits`, phase 06. Built to the web on main (the spec), the 2026-10-04 contract amendments, the pre-flight rulings (rows 13, 14, 16) and the phase-05 hand-over.

**Status:** done (pending merge). Not pushed. Versions not bumped, changelog not touched (by instruction).

**Native library rebuilt** in the worktree with `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`: four ABIs, and the regenerated `mediagram_core.kt` came out **identical** to phase 05's committed bindings (empty `git diff`). The `.so` is gitignored, so **rebuild on main after merging**.

## Commits

| Hash | What |
|---|---|
| `773c9666` | Model roles (`RoleAction`, `ownerOf`, `allowed`, `admin()`), `ProfileRequest`/`ProfileOutcome`, `Profile` + `WatchSnapshot.kidsFromSix`/`kidsMarks`; `manage(request)` + `setKids(setId, age)`; `FakeProfiles` on the model's rule; 5 new `CoreContract` cases; pickers lose Add/Remove. |
| `bf75765d` | A kid sees up to its own limit; `KidsEmpty(limit)`; Settings › Profile "Name · Kids · FSK N". |
| `56d1bf73` | `PinAsk`, `PinPrompt`, `ProfileWords` (the web's words). |
| `a5c08083` | Picker: `pick`, `claim`, `createFirst`, `pin`, `enterPin`, `cancelPin`; `ProfileUiState` in its own file. |
| `c508b094` | `ManageProfilesViewModel` + `ManageUiState`. |
| `86e82271` | Player `setKidsMark(age)`, `kidsMark`, `KIDS_CHOICES`, the web's rated labels. |
| `fac0765c` | `manage()` answers Done for a change that took even when reading it back fails. |

## Test-first record

Every task's tests were written and run red before the code:

| Task | RED |
|---|---|
| Model | `:core:model:test`: Unresolved `RoleAction`, `ownerOf`, `allowed`; no parameter `admin`/`kidsAge`/`parentId`. |
| Repository | `:core:data`: Unresolved `manage` ×13; `setKids` Int vs Boolean; `RecordingRepository` does not implement members. |
| Kid's limit | `:feature:catalog`: too many arguments for `kidsVerdictOf`/`forKidsProfile`, Unresolved `KidsEmpty`/`message`; `:feature:setup`: `expected <Mia · Kids · FSK 6> but was <Mia · Kids>`. |
| PIN prompt / words | Unresolved `PinAsk`, `PinPrompt`, `sentence`, `pinTitleFor`. |
| Picker | Unresolved `pick`, `pin`, `enterPin`, `claim`, `createFirst`. |
| Manage | Unresolved `ManageProfilesViewModel`, `ManageUiState`. |
| Player | No parameter `kidsMark`/`forEveryKid`; Unresolved `KIDS_CHOICES`. |
| Re-read fix | `aChangeThatTookIsDoneEvenWhenReadingItBackFails`: `IllegalStateException: disk`. |

The five new `CoreContract` cases passed on `FakeCore` as soon as they compiled: phase 05 had built the fake. They exist for the tablet's real core.

## GREEN

`cd android && ./gradlew -q --continue testDebugUnitTest :core:model:test :core:rust:compileDebugAndroidTestKotlin :ui-tv:compileDebugAndroidTestKotlin lint`: exit 0.
**2248 unit tests, 0 failures** (phase 05 left 2159):

| Module | Tests | Module | Tests |
|---|---|---|---|
| app | 5 | feature/catalog | 379 |
| core/data | 151 | feature/player | 254 |
| core/designsystem | 21 | feature/setup | 92 |
| core/model | 25 | feature/stats | 44 |
| core/playback | 275 | feature/system | 84 |
| core/testing | 50 (contract 43) | ui-common | 74 |
| core/update | 22 | ui-mobile | 286 |
| | | ui-tv | 486 |

`ProfileRolesFixtureTest` runs all 42 `profile-rules.json` cases. `RealCoreContractTest` and `ui-tv` androidTest compile. Lint: no new findings; the 29 warnings are all in files this phase did not touch.

## What was built

- **Model** (`core/model`)
  - `Profile` gains `kidsAge`, `parentId`, `admin`, `hasPin`, plus `kidsLimit`; anything but 6 reads as 12, as the web's `kidsLimitOf` does.
  - `KIDS_LIMITS = [6, 12]`. `KIDS_AGE_LIMIT` is deleted.
  - `kidsVerdictOf(fsk, limit)` and `forKidsProfile(sets, marks, limit)` follow contract §11.
  - `ProfileRoles.kt` holds the rule. Admin counts only on a grown-up (amended contract §2), through `List<Profile>.admin()`.
  - `ProfileRequests.kt` holds 8 requests and 8 outcomes, `NameTaken` included. The requests are plain classes, so no generated `toString` prints a PIN.
- **Repository** (`core/data`)
  - `manage(ProfileRequest)` re-reads profiles, the choice and `chosenProfile` after a Done. It re-reads nothing after an unlock, a refusal, or an answer that comes back after a reset or a core swap.
  - A change that took is Done even if that re-read fails.
  - `setKids(setId, age: Int?)`: anything but 6 is sent as 12, so an Int cannot wrap to 6.
  - `CoreProfileCalls.kt` holds `sendTo`, the outcome mapping, and the shared `CoreProfile.toModel()`.
- **Fake / contract** (`core/testing`)
  - `FakeProfiles` now enforces the model's `allowed`, not a private copy, so the app's rule is the fake's.
  - `WatchStateFixture` maps the new fields in one line. `hasPin` comes from `roles.pins`.
  - New `CoreContract` cases:
    - `aFreshCoreHasNobodyToChoose`
    - `aMalformedPinIsWrongNotInvalid`
    - `aChangedPinIsTheOneThatOpens`
    - `aKidIsManagedByItsOwnGrownUpOnly`
    - `aClaimWhileThereIsAnAdminIsRefused` (also covers not-found and a name taken across case)
  - `FakeCore` is unchanged, at 583 lines.
- **Catalog**
  - The filter reads `chosenProfile.value?.kidsLimit` and `snapshot.kidsMarks` (pre-flight row 14).
  - A limit changed in Manage refilters the shelves already up without reading the library again; tested through a real `manage(SetKidsAge)`.
  - `KidsEmpty(limit).message` reads "Nothing rated FSK N or under yet."
- **Settings › Profile**: "Name · Kids · FSK N" (row 16).
- **Picker** (`ProfileViewModel`, 234 lines)
  - It handles the §12 states, with `needsFirstProfile`, `needsAdmin` and `grownUps` on `Picking`.
  - `pick(id)`: a kid opens at once. A grown-up gives its PIN, or, with no PIN yet, types a first one twice.
  - `claim(id)` and `createFirst(name)` are both here.
  - "Stay as I am" follows `chosenProfileId`, so a profile removed in Manage takes the offer with it.
  - `reopen` and `stay` cancel an open prompt. `BackHandler`s are untouched.
- **Manage** (`ManageProfilesViewModel` 182 lines, `ManageUiState` 53 lines)
  - `open`, then `actAs` and the PIN, then the panel.
  - `addKid(name, age = NEW_KID_LIMIT)`: FSK 6 by default, the user's decision. The core still takes the age explicitly.
  - Also `setKidsAge`, `remove`, `addGrownUp`, `changePin` (own or reset), and `close`, which drops the held PIN.
- **Player**
  - `PlayerMarksState.kidsMark: Int?` and `forEveryKid`.
  - `setKidsMark(null | 6 | 12)`. Any other age is refused, and so are a kids profile and a rated title.
  - `toggleKids()` is kept as the single-button form, a mark from 12.
  - `KIDS_CHOICES` and `kidsLabel` follow the web.

## Where it follows the web on main rather than the phase file

1. **Words.** These are taken from `pin-prompt.js`/`profile-manage.js`:
   - NotAllowed is **"That is not allowed."**, not "Your profile cannot do that."
   - `NameTaken` is new: "A profile with that name already exists."
   - A malformed PIN gets "A PIN is four digits." and is not sent.
   - The second entry of a new PIN is headed **"The new PIN again"**, the web's field label, not "The same PIN again".
2. **A refused new PIN ends the prompt**, and its outcome goes to the caller, as `askPin({confirm})` does on the web. The picker then shows it as `Picking.notice` above the tiles and re-reads who is here. Manage shows it as the panel's notice. A refused existing PIN keeps the prompt open with the reason. `PinAsk.ask(…, refused: (ProfileOutcome?) -> Unit)`.
3. **Manage and a wrong held PIN.** When a change is refused as WrongPin, the panel drops the PIN and goes back to "Who are you?", with "Your PIN is no longer valid. Choose who you are again." This is the web's `report`. `ChoosingActor` carries a `notice` for it.
   - `canAddGrownUp` is derived from `actor.admin`.
   - The panel is the web's `manageable`: the admin sees all other grown-ups, and everyone sees the kids whose `ownerOf` is them.
4. **The player's rated labels.** A rating of 12 or under but over 6 reads "For kids from 12 · FSK 12", as `player-library-marks.js` has it. 6 or under reads "For kids · FSK 6".
5. **No `LOOK_AGAIN`.** The web has no such word. The pickers' existing "Try again" (retry, which re-reads and waits for a sync round) covers looking again.
6. **No PIN-less `add`/`remove` on `ProfileViewModel`.** Manage owns both.

## How the existing pickers behave until phase 07 (interim, not released)

- **Tiles still call `choose`.** A grown-up opens **without a PIN** until phase 07 points the tiles at `pick` and draws the prompt.
- **Add and Remove are gone from both pickers.** They could only be refused without a PIN. Removed with them: phone `AddTile`, `NameDialog` and the remove button; TV `TvAddTile`, the add flow and the remove row; and their tests (TV state test, androidTest, `TvHousekeepingTest.cancellingTheQuestionRemovesNobody`).
  - `RemoveProfileDialog.kt`, `TvRemoveProfileDialog.kt`, `TvAddProfileFlow.kt` and `TvAddTile` remain in the tree, unused, for phase 07 to rework.
- **A device with no profiles shows "Try again" only.** On TV that button holds focus.
  - A brand-new household cannot be created on Android in this interim; the web can.
  - A device whose household already exists gets its profiles by sync.
- **The player's Kids button still toggles Not for kids ↔ From 12.** Its label now names the choice, including "From 6" for a mark made on the web.
- Live now: per-kid catalog filtering, the empty-shelf sentence, Settings › Profile's line, and Back = "Stay as I am".

## What phase 07 must build (all data is here)

- **Picker, phone and TV.**
  - The three §12 states, from `Picking.needsFirstProfile`, `needsAdmin` and `grownUps`: a first-profile form calling `createFirst(name)`, then "Who runs this household?" calling `claim(id)`.
  - "Manage profiles" whenever `grownUps` is not empty.
  - Tiles call **`pick`** instead of `choose`, and show `Profile.kidsTag` instead of "KIDS".
  - `PICKER_NOTE` replaces both hard-coded notes.
  - `Picking.notice` is drawn above the tiles.
- **PIN prompt.** It is drawn from `ProfileViewModel.pin` / `ManageProfilesViewModel.pin`: `PinPrompt.heading`, `error` and `busy`, with `enterPin` at `PIN_LENGTH` and `cancelPin`. It registers Back after the gate's `BackHandler`.
- **Manage, phone and TV.**
  - `ManageUiState` has three states: Closed, ChoosingActor (with notice), and Managing (with notice and `canAddGrownUp`).
  - Words come from `ProfileWords.kt`: `WHO_ARE_YOU`, `managingAs`, `GROWN_UPS`, `RESET_PIN`, `REMOVE` with `removeQuestion`, `ADD_A_GROWN_UP`, `KIDS_SECTION` with `KIDS_LIMITS`, `ADD_A_KID` with `NEW_KID_LIMIT` first, `YOUR_PIN` with `CHANGE_YOUR_PIN`, and `DONE` calling `close`.
  - `TvAppFixture` and `LibraryFlowFixture` VM maps must gain `ManageProfilesViewModel`.
- **Player Kids choice.** A phone menu and a TV dialog over `KIDS_CHOICES`, calling `setKidsMark(age)`. Then retire `toggleKids`.
- **Device round.** `ANDROID_SERIAL=caad49da ./gradlew :core:rust:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=rust.RealCoreContractTest`. This round has 11 profile cases: phase 05's 6 and this phase's 5.

## Concerns

- **`RealCoreContractTest` not run.** Its 5 new cases compiled but have not run against the real core (no adb, by instruction). Each assertion was checked against `manage.rs`, but they are unproven on the tablet.
- **`ProfileViewModel.kt` is 234 lines**, over the 200 guideline. It was 240 before phase 05. Every new file is under 200.
- **Interim security gap.** Until phase 07, a grown-up's tile opens without a PIN, as it did before 0.108. Phases 06 and 07 should ship together, as the plan says.
- **`choose` is never PIN-checked by the core.** This is by design and stated in `PICKER_NOTE`: the PIN stops a child's tap, not adb.

## Unresolved questions

- Should phase 07 keep a separate "Look again" beside the first-profile form, as the plan suggested? The web has none, so it is left out here.
