# Phase 3: TV setup, sign-in and "Who's watching?"

**Context:** [plan.md](plan.md) · phone refs: `ui-mobile/.../ui/MobileApp.kt`, `ui/setup/{TelegramApplicationScreen,LoginScreen,LibraryScreen,StartOverAction}.kt`, `ui/profile/{ProfileGate,ProfilePickerScreen}.kt` · web ref: `web/public/lib/profile-picker.js`

## Overview

- **Priority:** High — nothing else is reachable on a fresh TV without it.
- **Status:** done — tasks 1–3 in 6e4bc84..60454d6; emulator walk 2026-09-25 (application step by D-pad, phone number and code entered by the user in the emulator window, landed on the library stub with profiles synced from the channel)
- **Deliverable:** from a fresh install, with only a remote: enter the Telegram application id/hash, sign in (phone, code, 2FA), choose the library, pick or add a profile.

## Key insights

- Same `SetupViewModel` / `LoginViewModel` / `ProfileViewModel`, same states, same order as phone. Prompts come from the moved `promptFor` / `libraryPromptFor`, validation from `SetupInput` — TV adds no rules.
- Text entry uses the system on-screen keyboard (tv `OutlinedTextField`-equivalent: compose `BasicTextField` in a tv `Surface`, `ImeAction.Next/Done` so the keyboard's action key advances). Unpleasant with a remote, accepted this round (plan Open Question 2). Code comment says the field exists until sign-in can be handed over from another device — no phase reference.
- One question per screen with the field focused on entry: the remote lands in the field, centre opens the keyboard, the keyboard's Done submits. No separate Submit hunt.
- "Who's watching?" matches the web: initial-letter tiles, choice kept per device, a convenience not a login. Tiles in a single centred row; first (or last-used) tile focused.
- Start over: reachable on every step after the first, as on phone, behind a confirm dialog. On TV dialogs: compose `Dialog` hosting tv `Surface` + tv `Button`s; the **safe** button (Cancel) takes initial focus.
- Secret fields (API hash, 2FA password) masked — a TV is read by the whole room.
- **Safe area is the screen's job.** `TvShell` only provides colour; each screen pads itself with `Overscan` (lazy containers via `contentPadding`, so focus growth isn't clipped). This is the first edge-aligned TV screen — check the 48×27dp margin on the emulator screenshot.
- **How TV screens are tested (decided here, used by phases 4–5):**
  - *Robolectric* for state and appearance only: which screen, what text, content colour, selection. On tv-material nodes, `assertIsDisplayed()` and `performClick()` misbehave (seen in phase 2) — use `assertExists()`, `performSemanticsAction(SemanticsActions.OnClick)` to activate, `performSemanticsAction(SemanticsActions.RequestFocus)` + `assertIsFocused()` for focus, `performKeyInput { pressKey(Key.DirectionRight) }` for D-pad. First try `@Config(qualifiers = "w960dp-h540dp-land-television")` so layout runs at TV size; if D-pad traversal still doesn't work under Robolectric, stop using it for traversal rather than weakening assertions.
  - *Pure JVM* for focus-restoration logic (which key regains focus on return) — keep it in a plain function/state holder.
  - *Instrumented* (`ui-tv/src/androidTest`, TV emulator `emulator-5554` only, fixture ViewModels as in `TvAppFixture` — no real account or profile touched): per screen, initial focus on appear, one D-pad traversal, Back leads somewhere; focus restored after Back once walls exist. Not part of `scripts/check.sh` (needs the emulator); each phase's verification lists them.
  - Replace the weak `TvFocusTest` (composes + clickable) with a real focus test the first time the instrumented set exists.

## Requirements

- Functional: every step completable by D-pad + on-screen keyboard; Back on a step goes to the previous step where the phone allows it, else leaves the app.
- Non-functional: no new logic; Robolectric tests drive keys, not clicks.

## Related code files

- Create: `android/ui-tv/src/main/kotlin/ui/tv/setup/{TvSetupStep.kt,TvApplicationScreen.kt,TvSignInScreen.kt,TvLibraryChoiceScreen.kt,TvTextQuestion.kt,TvConfirmDialog.kt}`, `ui/tv/profile/{TvProfileGate.kt,TvProfilePicker.kt}`, tests in `android/ui-tv/src/test/kotlin/ui/tv/…`
- Modify: `TvApp.kt` (replace setup stub)

## Implementation steps

### Task 1: Shared pieces
- [x] **1.1** `TvTextQuestion(prompt, value, onValue, onSubmit, secret)`: one heading, one field focused on entry, IME action submits. Test: on show, field is focused; `performKeyInput { pressKey(Key.Enter) }` on a filled field calls `onSubmit`.
- [x] **1.2** `TvConfirmDialog(title, body, confirm, cancel)`: cancel focused initially; Back == cancel. Test both.

### Task 2: Setup steps
- [x] **2.1** `TvSetupStep` switches on `SetupUiState` exactly like phone `SetupStep` (Checking → centred progress; NeedsApplication; NeedsSignIn; NeedsLibrary; Failed → message + Retry focused).
- [x] **2.2** `TvSignInScreen` renders `LoginUiState` steps via `promptFor`; completion wiring from `:ui-common` (same as phone).
- [x] **2.3** `TvLibraryChoiceScreen`: list of libraries as focusable rows, first focused.
- [x] **2.4** Start over on every step after the first → `TvConfirmDialog`.
- [x] **2.5** Robolectric tests with fixture ViewModels (pattern from `ui-mobile` `MobileAppTest`): each state renders its screen and something is focused.
- [x] **2.6** Commit — `feat(android): set up and sign in from a television`.

### Task 3: Profiles
- [x] **3.1** `TvProfileGate` over `ProfileViewModel`: Picking → `TvProfilePicker`; Chosen → content.
- [x] **3.2** `TvProfilePicker`: "Who's watching?", initial-letter tiles (use moved `initialsOf`), kids profiles labelled as on phone/web, "Add" tile last → `TvTextQuestion` for the name + kids choice as on phone. Stay/Retry states as phone.
- [x] **3.3** Tests: first tile focused; D-pad right moves to next tile; centre chooses.
- [x] **3.4** Commit — `feat(android): choose who's watching with a remote`.

### Task 4: Emulator walk (mouse-free)
- [x] **4.1** Fresh data: `adb -s emulator-5554 shell pm clear com.mediagram.android`. **Emulator only — never `pm clear` on the phone.**
- [x] **4.2** Drive with `adb shell input keyevent DPAD_*/DPAD_CENTER/BACK` and the on-screen keyboard; screenshot each step. Sign in with the user's account (the emulator gets its own session; this does not disturb other devices).
- [ ] **4.3** Create a profile named **`TV test`** for all later emulator checks. _Deferred to phase 4: the channel synced `andre` and `test` and the gate chose `andre`; the TV has no way back to the picker until phase 4's masthead profile action, so `TV test` is created from there, before anything is played._

## Todo list
- [x] Text question + confirm dialog, focus-tested
- [x] All setup states on TV
- [x] Profile gate + picker
- [x] Mouse-free emulator walk (`TV test` moves to phase 4)

## Success criteria
Fresh emulator install reaches a chosen profile using only key events; every screen had focus on arrival.

## Risk assessment
| Risk | Mitigation |
|---|---|
| Emulator on-screen keyboard differs from real TVs | Rely only on IME action key, not keyboard layout |
| Login code arrives slowly/needs the phone's Telegram | User reads it from their Telegram app; no automation |
| Sign-in on emulator counts as a new Telegram session | Expected; user can end it from Telegram settings later |

## Security considerations
API hash and 2FA password masked; nothing logged. Session lives in the emulator's encrypted settings like on phone.

## Next steps
Phase 4.
