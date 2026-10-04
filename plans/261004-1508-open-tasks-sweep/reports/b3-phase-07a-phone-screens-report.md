# B3 phase 07a: phone screens — picker, PIN, Manage, the player's Kids choice

2026-10-05, fullstack-developer subagent, worktree branch `worktree-agent-ada35780193a2361a`.
Plan `260928-0047-profile-roles-pins-kids-age-limits`, phase 07, phone/tablet half (`android/ui-mobile`).
The TV half runs in parallel in another worktree; nothing under `android/ui-tv` was touched.

**Status:** done, with one hand-over (`toggleKids` stays until the TV half lands, see below).
Not pushed. Versions not bumped and changelog not touched, as instructed.

## Setup

- Reset to `worktree-agent-ac2ce5e3f3a3e5567` (phase 06 at `f96e5ead`).
- Merged `main` (`087b7a11`, 0.109.0) as `35cfe95a`. There were **no conflicts**.
- Rebuilt the native lib with `scripts/generate-android-bindings.sh` (NDK 28.2). The regenerated bindings are **identical** to the committed ones (empty diff).

## Commits

| Hash | What |
|---|---|
| `f0dd62ac` | `PinDialog`: masked, number pad, four digits handed over, then the field empties. |
| `09dd67e0` | `ManageProfilesScreen`, plus `NameForm` and `LimitChoice` (`ProfileForms.kt`). `RemoveProfileDialog` is reworked into the web's confirm. |
| `87b63689` | Picker in three start states, the gate (Manage, PIN dialogs, Back), the `LibraryFlowFixture` VM map, and the flow tests. |
| `e6d13e3f` | Player Kids control: a menu over `KIDS_CHOICES` that calls `setKidsMark`. The phone no longer calls `toggleKids`. |

## Test-first record

- **RED: tests did not compile.** All new tests were written before the code. The compile failures were `Unresolved reference 'PinDialog'`, `'PinFieldTag'`, `'ManageActions'`, `'ManageProfilesScreen'`, `'PickerActions'` and `'WhoRunsTag'`, plus `No parameter with name 'onKidsMark'`.
- **RED: repeated PIN dropped (real bug).** The first PIN dialog was built on the `String` overload of `OutlinedTextField`. The flow tests then failed: a new PIN typed twice never arrived, and neither did a wrong PIN typed five times.
  - **Cause:** that overload drops an edit whose text equals the last one it reported. The field empties itself in the same callback, so its `value` never changes.
  - **Fix:** a component test first, `theSameFourDigitsAgainAreHandedOverAgain` (red: `expected [1234, 1234] but was [1234]`), then the field moved to `TextFieldValue`.
  - **Impact:** on a device this would have made a new PIN impossible to confirm. The same applied to the first profile, Add a grown-up, Reset PIN, and a grown-up from before PINs.
- **GREEN:** all of the above pass.

## GREEN

`cd android && ./gradlew -q --continue testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin` exited 0.

**2306 unit tests, 0 failures.** ui-mobile went from 286 to 322.

| Module | Tests | Module | Tests |
|---|---|---|---|
| app | 5 | feature/player | 254 |
| core/data | 151 | feature/setup | 92 |
| core/designsystem | 21 | feature/stats | 44 |
| core/model | 25 | feature/system | 84 |
| core/playback | 297 | ui-common | 74 |
| core/testing | 50 | ui-mobile | 322 |
| core/update | 22 | ui-tv | 486 |
| feature/catalog | 379 | | |

Lint has the same 29 warnings as before. None of them are in a touched file.

The split into harness and flow classes came after that run. Afterwards, `ui.profile.*`, `PlayerKidsChoiceTest`, `LibraryFlowTest` and `LibraryRailTest` were re-run: 56/56 green.

### New tests (36)

- **`PinDialogTest` (5):**
  - four digits are handed over, then the field empties
  - the same four digits typed again are handed over again
  - the field is masked
  - a new PIN's second entry and a wait message are said
  - Cancel
- **`ProfilePickerScreenTest` (7):**
  - a kid's tile shows "Kids · FSK 6" and calls `pick`
  - the first-profile state still shows kids, offers no Manage, and trims the name
  - an empty device has "Try again"
  - "Who runs this household?" offers grown-ups only
  - an admin household shows Manage and the note
  - the notice sits above the tiles
  - a failed load gets no first-profile form
- **`ManageProfilesScreenTest` (7):**
  - Who are you, with its notice
  - a parent sees its kids and no grown-ups
  - the admin's Reset PIN and Remove, with the question asked first
  - Cancel removes nobody
  - a new kid needs a name and starts at 6, with 12 selectable
  - the admin adds a grown-up
  - the notice and Change your PIN
- **`ProfileGateFlowTest` (8).** These run the real `ProfileViewModel` and `ManageProfilesViewModel` over `WatchStateFixture` and `FakeCore` rules. They cover:
  - first run: a name, a PIN typed twice, which makes the admin, then the admin's tile and PIN let them in
  - two different entries of a new PIN are refused
  - "Who runs this household?" is claimed with a PIN
  - a wrong PIN shows "Wrong PIN.", then the right one opens
  - five wrong PINs show "Too many wrong PINs. Try again in 60 s."
  - a grown-up from before PINs chooses one twice
  - a kid opens freely
  - Back on the PIN dialog cancels the PIN, then Back on the picker = "Stay as I am", and the app does not finish
- **`ManageProfilesFlowTest` (6):**
  - Back in Manage = Done, and the PIN is forgotten
  - the admin adds a grown-up with a PIN typed twice, and a kid at FSK 6 with `parentId` = the actor
  - a parent raises its kid's limit
  - the admin resets another grown-up's PIN
  - removing a grown-up takes their kid too
  - a name already taken shows "A profile with that name already exists."
- **`PlayerKidsChoiceTest` (3):**
  - through `PlayerScreen` and a real VM: menu → From 6 → `kidsMarks[set] == 6`, then Not for kids → none
  - a rated title shows its verdict, disabled
  - a kid's profile has no Kids control

## Where it follows the web

- **Picker order is the web's `draw`:** notice, then the start state's question, then tiles, then Manage profiles, then `PICKER_NOTE`, then "Stay as I am".
  - The first-profile form is the web's `addForm("Create")`. Its button is **"Create"**, not "Continue", and the name field's accessible name is "Create: name".
  - The household question uses quiet pills, one per grown-up.
- **The name forms are inline, as the web's are,** not dialogs: the first profile, Add a grown-up, and Add a kid with its FSK choice. Each field empties after it hands over a name, as the web's redrawn form does.
- **The FSK choice is two chips** (`SettingsChip`), named "Age limit for X" / "Age limit for the new kid", which are the web's aria-labels. Choosing the limit already set sends nothing, as a `<select>` fires no change for it.
- **Manage's notice sits above Done**, where the web's `pin-message` sits. It is a polite live region, as the web's is `role=alert`.
- **Player Kids:** picking the current choice again sends nothing, as the web's select does.
- **Design system:** `LinePill` is for the affirmative add buttons (the web's filled `who-create`). `QuietPill` is for the web's grey `pill-line` buttons: Reset PIN, Remove, Change your PIN, Done, Who are you, and the household question. Section titles use `titleMedium` (Fraunces, the web's h2). Rows have a `ruleSoft` divider. Dialogs are M3 defaults (DESIGN.md has no dialog vocabulary).

## Deliberate differences (written down where they live)

- **"Try again" on a device that knows nobody.** It is shown next to the first-profile form when the profile list is empty. The web has none. On Android the first sync may not have brought the household's names in yet, and a first profile made in the meantime would lose the admin role to the older claim. This was the interim behaviour, now kept beside the form; the comment is in `ProfilePickerScreen.kt`.
- **The PIN is handed over at four digits**, with no OK button: phone number-pad UX. The web has an OK button.
- **Manage's actions live in `ui-mobile`** (`ManageActions`), not `ui-common` as the plan sketched. ui-common was not touched, because the TV worker owns its own half in parallel. If both halves want one bundle, it can move to `ui-common` at merge.

## Hand-over: `toggleKids` not yet retired

The phone no longer calls it. `ui-tv/.../TvPlayerControlsBridge.kt:14,69` still does, and that file belongs to the TV half. Deleting it from `feature/player` here would break the ui-tv build.

**After both halves merge:**
1. Delete `PlayerMarksController.toggleKids` and `PlayerViewModelDelegates.kt:49`.
2. Switch the feature tests to `setKidsMark(12)`: `PlayerMarksTest` (drop `toggleKidsMarksAndUnmarksTheOpenTitle`), `PlayerActionFailureTest:104`, and `PlayerActionNoticeTest:42,147`.
3. Check with `git grep toggleKids`, which should find nothing.

## Robolectric note (test-only)

A dialog holding a text field, opened by a tap in a large Robolectric window (`w400dp-h900dp`, `w400dp-h2400dp`), never settles. Thread dumps show `ViewRootImpl.performTraversals` → `DialogLayout` re-measuring forever. A plain M3 `AlertDialog` + `OutlinedTextField` reproduces it, so it is not these screens.

The flow tests therefore run in Robolectric's default window, as `PinDialogTest` and `PlayerNoticesTest` already do, and `performScrollTo` Manage's lower sections. This is explained in `ProfileGateHarness`'s KDoc.

## Not done here

- **Device check on the tablet.** No adb, by instruction. The tablet walk is phase 07 Task 8, read-only first.
- **Phase file checkboxes.** The phase file is shared with the TV worker, so it was not edited, to avoid a merge conflict. Tasks 1–3 and the phone half of 7 are done.
- **`android/.kotlin/`** (the Kotlin daemon's session directory) shows as untracked and is not gitignored. It was left uncommitted.

## Unresolved questions

- Should `ManageActions` move to `ui-common` once the TV half lands, or stay per surface?
