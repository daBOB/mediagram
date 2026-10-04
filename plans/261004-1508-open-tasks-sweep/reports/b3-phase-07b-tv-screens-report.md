# B3 phase 07, TV half: picker states, PIN pad, Manage profiles, the player's Kids choice

2026-10-04/05, fullstack-developer subagent, worktree branch `worktree-agent-aa8bd4a99449d2135`.
Plan `260928-0047-profile-roles-pins-kids-age-limits`, phase 07, tasks 4–7 (TV parts only).

**Status:** done. Not pushed. Versions not bumped, changelog not touched, no adb (all by instruction).

## Setup

- `git reset --hard worktree-agent-ac2ce5e3f3a3e5567` (phase 06, `f96e5ead`).
- `git merge --no-edit main` gave merge `32e241ac`. Main had moved past `087b7a11` to `0bedf043`. The merge was clean, with no conflicts.
- Native core rebuilt with `scripts/generate-android-bindings.sh`. `mediagram_core.kt` regenerated **identical** to the committed one (empty `git status`).

## Commits

| Hash | What |
|---|---|
| `ee4863b6` | `TvPinPrompt` + `TvPinPad`: a 4-digit pad the D-pad walks, which the remote's digit keys type into too; Robolectric `TvPinPadStateTest` (9), androidTest `TvPinPadTest` (2) |
| `83bde0ed` | `TvManageProfiles` + `TvManageDialogs` + reworked `TvAddProfileFlow`. `TvRemoveProfileDialog.kt` is deleted, its confirm folded into the dialogs. `TvManageProfilesStateTest` (10) |
| `276aa5a4` | `TvPickerQuestions` (first profile, "Who runs this household?", `TvPickerSpot`), picker, tiles and gate; `TvAppFixture` gains `pins`, `core` and the `ManageProfilesViewModel`; `TvProfilesFlowTest` (10 whole-app walks); picker state tests rewritten (14); androidTest first-profile focus |
| `9f4d8432` | `TvKidsChoiceDialog` + `TvKidsChoiceOverPlayer`; the Kids mark opens it (`TvMarksActions.onKids`); `choosingKids` holds the controls like `choosingList`; `TvPlayerMarksTest` +2 |
| `7ccbbbe4` | `docs/system-architecture.md` § Television differs: three bullets (PIN pad, Manage dialogs, Kids dialog) |

## Test-first record

- **PIN pad.** The implementation was set aside, then the test was run: `Unresolved reference 'TvPinPrompt'`, `'TvPinDotsTag'`, `'tvPinKeyTag'`. Restored, all 9 pass.
- **Manage, picker, flow, player.** All tests were written first and run: compile RED with `Unresolved reference 'TvManageActions'`, `'TvManageProfiles'`, `'tvKidsLimitTag'`, `'tvClaimTag'`, `'TvManageProfilesTag'` and `'TvPickerSpot'`.
- **The two new `TvPlayerMarksTest` cases** compiled already. Their RED was hidden behind the same source set's compile failure and was never seen on its own.
- **First GREEN run: 49/50.** `pickingShowsTheHeadingAndEveryProfilesName` failed, and the fault was the test, not the code. Its two non-admin profiles also put Ada's name in the claim row, so "Ada" matched twice. The test data now names Ada the admin.

## GREEN

`cd android && ./gradlew -q :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint` exits 0.

- **ui-tv: 523 unit tests, 0 failures** (phase 06 left 486; +37).
- androidTest compiles.
- Lint: 29 warnings, 1 hint, the same counts phase 06 reported. No new findings.

Every new state and flow test runs **out of touch mode and moves by D-pad key events**. Names are typed through `performTextInput`, the system keyboard's stand-in.

| Flow | Tests |
|---|---|
| First run: a device with only a kid | First-profile row holds the remote → name → PIN typed twice by D-pad ("A PIN for Ann", then "The new PIN again") → tile + Manage. In the core, Ann is admin with PIN 2468 |
| Who runs this household | Up from the first tile reaches the claim row → PIN → the question is gone and andre is admin |
| Grown-up PIN by D-pad | Wrong (1111) → "Wrong PIN." → right → library |
| Wrong PIN + wait | Six wrong PINs on the remote's digit keys → "Too many wrong PINs. Try again in 60 s."; the right PIN then waits too |
| Kid opens freely | Kid tile → library filtered to FSK 6, with no prompt |
| Back | Back on the PIN gives up the PIN only, and the remote lands back on the tile. The next Back is "Stay as I am" and does not leave the app |
| Manage, admin | Reset Bo's PIN (typed twice, saved in the core); remove Bo with his kid Cy (asked, Cancel first); Done → picker with the remote on Manage |
| Manage, grown-up | Bo sees only his own kids. He adds Lina at FSK 6 (the default, holding the remote), then raises her to FSK 12; her parent is Bo |
| Name taken | "A profile with that name already exists." on the panel, and nothing is added |
| Who are you | A wrong PIN keeps asking; Back → Who are you → Back → picker |
| Kids choice (player) | The dialog opens with the current choice holding the remote → Down + Centre marks From 6, and the remote returns to the mark. While open it holds the controls; Cancel changes nothing |

## What was built

**Pad** (`TvPinPrompt.kt`)
- The grid is 1–9, then a gap, 0 and Delete, with keys 80×56dp. 80dp is wide enough for "Delete" in words, so every column lines up.
- The remote starts on 1. Digit keys and Backspace work from anywhere on the pad.
- Dots, never digits. The PIN goes at the fourth digit and the pad empties.
- While busy the keys keep the remote but type nothing.
- Its own BackHandler, composed after the gate's "Stay as I am", cancels.
- The pad fits 540dp with Cancel inside the overscan.

**Picker** (`TvProfilePicker.kt`, `TvPickerQuestions.kt`, tiles, gate)
- The web's order: notice, first profile *or* the household question, tiles ("Kids · FSK N"), Manage profiles once there is a grown-up, `PICKER_NOTE`, Stay.
- The first profile's name is asked in place.
- Neither question is asked over a failed load: whether a household exists is not known then.
- **The remote returns to what it left from** (a tile, a claim, Manage) after a PIN is given up or Manage is done, as a browser hands focus back to what opened a dialog.
- The row's `LazyListState` is held by the gate, so a scrolled-away tile is still composed on return.
- The picker column now scrolls. The household question, tiles, Manage, note and Stay together are taller than 540dp, and a test walks down to Stay and checks it is on screen.

**Manage** (`TvManageProfiles.kt`, `TvManageDialogs.kt`)
- One column: Who are you → As name → Grown-ups (admin) → Kids "Name · FSK N" → Your PIN → Done.
- A row opens a dialog: a kid gets FSK 6/12, the current one focused, then Remove; a grown-up gets Reset PIN, then Remove.
- Remove asks with `removeQuestion`, Cancel first.
- Rows are `key(id)`-ed. Focus is re-requested when someone leaves the list.
- Back is Done.
- `TvManageActions` is a TV-internal callback bundle; see Concerns.

**Player**
- `TvKidsChoiceDialog` over `KIDS_CHOICES`, titled "For kids" (the web select's label). It calls `setKidsMark`.
- The TV no longer calls `toggleKids`.

## Concerns / left for the merge

1. **`toggleKids` is not deleted.** `ui-mobile/.../PlayerScreen.kt` still calls it, and deleting it here would break ui-mobile and the whole-project `lint`.
   - The TV no longer uses it.
   - Delete it (`PlayerMarksController`, `PlayerViewModelDelegates:49`, and its feature tests) in whichever branch lands last, most likely the phone half.
2. **`ManageActions` is not in ui-common.** Only the TV has `TvManageActions` (`ui-tv/.../profile/TvManageProfiles.kt`), which avoids an add/add conflict with the phone worker. If the phone ships `ui.profile.ManageActions`, the TV can take it in one line at the merge.
3. **"Try again" on an empty device sits under the first-profile row.** The web has none. It is kept for Android's first sync round, per phase 06's ruling. This holds on all of Android, not just TV, so it is not written under § Television differs.
4. `TvPlayerScreen.kt` is 217 lines; it was already over 200 before this phase. Every new file is under 200.

## What only the box can confirm

- The first focus and grid travel on a real window manager: `TvPinPadTest` and `TvProfilePickerTest` are compiled but have not been run.
- Whether the box's remote sends digit keys, and which code its delete key sends, if it has one.
- The pad's legibility from the couch, and whether 80×56 keys fit with the system's focus scale.
- The scrolling picker in the household's real "Who runs this household?" state, with Stay showing.
- The Kids dialog over a playing film, including whether the media keys still reach the film through it.

## Unresolved questions

- None blocking. Confirm points 1 and 2 above at the merge.
