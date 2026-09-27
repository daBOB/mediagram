# Code review: TV Settings two-pane, phone spacing, home cache server status, 0.69.3

Worktree `mediagram-channel-index`, branch `feat/android-settings-redesign`, uncommitted diff against `225b3bd6`. Read-only review. Nothing in the worktree was edited except this report.

## How it was checked

- I read every changed and new file, plus the phone reference (`ui-mobile/.../settings/*`, `SystemScreen.kt`), the ViewModels (`SystemViewModel`, `LanCacheViewModel`, `CacheBudgetViewModel`) and Compose UI 1.11.4 bytecode (`FocusTargetNode.fetchFocusProperties`, `FocusRequester`).
- **Behaviour was tested, not just read.** I copied `android/` (without build output) plus `web/test/fixtures/markdown/cases.json` into the session scratchpad. There I ran D-pad key-event probes against the real `TvAppFixture`. I also tried one candidate fix in that copy. The worktree was never touched.
- In the same copy, `:ui-tv/:ui-mobile/:feature:system` `lintDebug` passed, as did `TvMenuTest`, `ui.tv.system.*`, `ui.settings.*` and the `feature:system` tests. detekt and spotless are defined in build-logic but not applied to any module.
- A caveat on the probes: Robolectric measures text at about 1px per character. Probe widths for text are meaningless, but positions and focus order are real.

## Findings, most severe first

### 1. HIGH: pressing Up at the top of a section jumps into the index and switches the section
`TvSettingsPanes.kt:94-101`. The content column is not a focus group, so a vertical move with no candidate inside the content falls through to the 2D search. That search then picks the nearest index row above-left, and that row's `onFocusChanged` swaps the section.
- Probe 1: Settings → OK Telegram → focus on Change library → DPAD_UP. Focus lands on the **System** index row and the page head becomes "WHAT THIS PLAYER IS DOING".
- Probe 2: Storage → Right → Cache heading → DPAD_UP. Focus lands on the **Telegram** row and the page swaps to Telegram.
- Real-world impact: a viewer presses Up to re-read the Telegram ledger above Change library, and the page is replaced by System.
- Fix: see finding 2. One fix covers both.

### 2. HIGH/MEDIUM: Left inside a row of swatches leaves the section instead of moving to the previous swatch
`TvSettingsPanes.kt:100`, `.focusProperties { left = rowRequesters.getValue(section) }`. In Compose 1.11, `fetchFocusProperties` walks up from each focus target to the nearest ancestor focus target. The content Column has no focus target of its own, so this `left` applies to **every** control in the section, not just the leftmost one.
- Probe: Appearance → Coral → Right → Blue → Right → Violet → DPAD_LEFT. Focus lands on the Appearance index row; it should have landed on Blue.
- Affected: the 7 accent swatches and the 4 artwork cards. The spec said "Left from any section control" and the code follows it literally, but that breaks Left movement within a row.
- Fix, tested in the scratch copy: replace `left = …` with an exit handler on a focus group.
  ```kotlin
  .focusProperties {
      onExit = {
          when (requestedFocusDirection) {
              FocusDirection.Up, FocusDirection.Down -> cancelFocusChange()
              FocusDirection.Left -> rowRequesters.getValue(section).requestFocus()
              else -> Unit
          }
      }
  }
  .onFocusChanged { state -> if (state.hasFocus) onFocusInContentChange(true) }
  .focusGroup()
  ```
- Results with the fix applied:
  - Up from Change library stays on Change library.
  - Right, Right, Left among the accents lands on Blue.
  - Left from the leftmost swatch goes to the Appearance row.
  - Left from Pairing token, far down the page, goes to the **Storage** row, not the nearest one.
  - Right re-enters, Back goes to the index, and returning from a panel lands on the Server address row.
  - `TvMenuTest` stays green.

### 3. MEDIUM: one scroll position is shared by all four sections, so a section can open with its title scrolled off
`TvSettingsPanes.kt:98`, `verticalScroll(rememberScrollState())` is not keyed by section.
- Probe: go deep into Storage (its page head is at y=-187), press Back, then Up to Telegram. Telegram's page head sits at y=-146, off screen.
- Fix: `val scroll = remember(section) { ScrollState(0) }`. Returning from a panel is unaffected, because the panes are unmounted and remounted around a panel anyway.

### 4. MEDIUM: the page-head eyebrow is 11sp on TV, but the spec requires 16sp
`TvSettingsPanes.kt:104-110`. `PageHead` has no way to set the eyebrow size and always draws `Eyebrow` at 11sp. The phase-04 spec requires eyebrow 16sp, with ≥16sp as the floor for eyebrow and status text. The index's own "SETTINGS" label does use `TvTypeScale.eyebrow`.
- Fix: add `eyebrowStyle: TextStyle = Eyebrow` to `designsystem.PageHead`. The default leaves the phone unchanged. The TV passes `Eyebrow.copy(fontSize = TvTypeScale.eyebrow)`.

### 5. MEDIUM: the TV home cache server block lacks the new server budget and chunk count, and the changelog says it has them
- `TvLanCacheBlock.kt:68` still draws a single `"Status" to lanCacheStatusLine(current)`. The phone draws `lanCacheRows(state)`: Status, Holding "x of y (n%)", Chunks.
- `docs/project-changelog.md` 0.69.3 says the block shows this "on every surface that shows the block". That is not true for TV, and it is a Surface Parity gap as CLAUDE.md defines it.
- Fix: `rows = lanCacheRows(current)`. `TvInfoBlock` already skips null values, and `TvMenuTest`'s "Not found" check still matches the Status value.
- Once that is done, `lanCacheStatusLine` has no production caller, and its KDoc ("the words both Android surfaces show") is out of date. Delete it or fix the KDoc.

### 6. LOW-MEDIUM: the full-screen profile-reload panel lost its overscan padding
`TvTelegramSection.kt:82`. The old `TvSettingsRows.kt` padded `TvProfileReload` with `Overscan` when `takesFocus`; the move dropped that padding.
- `TvSettingsScreen.kt:113` draws it bare as the whole Application panel, so after a failed profile reload "Reload profiles to continue" and "Try again" sit at (0,0), inside the area a TV may crop.
- Fix: restore `Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)` when `takesFocus`.

### 7. LOW: the new 2-second System poll can steal focus when a read fails once
`TvSystemScreen.kt:56-62`. The effect is keyed on `failure != null`.
- On a failed poll, the ViewModel keeps `lastSnapshot` (so `current` stays non-null) and sets `failure`. Focus jumps to Try again at the top of the page. On the next successful poll, focus jumps to Catalogue.
- Before this change the TV had no poll, so this only happened on an explicit retry. A flaky `core.isAuthorized()` would now cause it.
- Found by reading the code; not probed.
- Fix: send focus to retry only when `current == null`.

### 8. LOW: pressing OK on Storage before the first cache read finishes loses focus
`TvCacheBudgetBlock.kt:175` and `:183-188` attach `entryFocusRequester` to two different "Cache" headings, one drawn while loading and one after. If OK lands on the loading heading, that node is replaced when the read arrives. The effect is keyed on `focusInContent` only, so nothing re-requests focus, and nothing is focused.
- This only happens on the first visit in the ViewModel's lifetime, while the cache opens (slow on the USB stick).
- Fix: draw the heading from one call site and compute its rows from `current`.

### 9. LOW: tests are thinner than the spec asked for
- Spec step 7's "Left from a section control returns to the index" test was not added. No test sends a D-pad key to the new panes: Right, Left and Up are all untested, and entry is only exercised through semantics `OnClick`.
- In `TvMenuTest` `systemLandsOnItsIndexRow…`, the last `onNodeWithText("System").assertIsFocused()` passes even if Back did **not** leave the screen, because the index row is also named "System". Also assert that the "WHAT THIS PLAYER IS DOING" eyebrow no longer exists.
- The scratch probe is a ready-made template for key-driven tests. It uses the `key(code)` helper from `TvSearchAndGenreTest`.

### 10. LOW / style
- `TvSettingsScreen.kt` is 215 lines, over the 200-line guideline.
- `TvSystemScreen.kt` now holds only `TvSystemContent`. Renaming it `TvSystemSection.kt` would match `TvTelegramSection` and `TvStorageSection`.
- `ui-common/.../SettingsSections.kt:9` has "(phase 04's own two-pane build)". It predates this diff but is a plan reference. Now that the build exists, it can say "the television's two-pane Settings".
- `TvMenuTest.kt:106` has "One more level than before the index existed". That describes history, not behaviour.
- The new KDoc mentions "approved round-2 mockups". That matches how the codebase already talks about design references (see `SettingsControls.kt:39` and the icon XMLs), and it is not a plan or phase code, so I have not flagged it.

## Question from the brief: can `returningFrom` be read stale? The change is sound
The `LaunchedEffect(focusInContent)` bodies in `TvTelegramSection` and `TvCacheBudgetBlock` work as intended:
- On the first composition after a panel closes, the effect captures `returningFrom` = the panel. The hub's `LaunchedEffect(panel)` clears `lastPanel` in the same frame, which recomposes with `null`, but the effect does not restart because its key is unchanged.
- Later restarts come only from `focusInContent` changing. That happens only on later user input (Back, Left, Right, OK), after `lastPanel` is already null, so each restart captures the current, correct value.
- Probe: LAN address panel → Back → focus on the Server address row, still there after 3 s. Then Back → Storage row, then Right → Cache heading: the default entry, not a stale jump back to the address row.
- `aSettingsPanelIsLeftForTelegramThenTheIndexThenTheMenu` covers the Application panel.
- The only branch that could replay a stale value is Telegram's `returningFrom == Application` branch, which ignores `focusInContent`. It cannot be reached, because panels only open from controls inside the section.

## Spec phase-04 success criteria
| Criterion | Status |
|---|---|
| Every action reachable by remote | Met. Rows are clumsy because of finding 2. |
| Back from any depth reaches the menu row that opened Settings/System | Met (tests and probe) |
| ui-tv tests green | Met |
| No focus trap between panes | No trap, but focus leaks out of the section (finding 1) |
| Requirements: eyebrow 16sp | Not met (finding 4) |
| Requirements: panels keep behaviour, land on the control that opened them | Met |
| Requirements: entry effects run once per visit in the hub | Met |
| Step 7 tests (Left test) | Missing (finding 9) |

## Touchpoints and contracts
- **TV menu to Settings/System:** `initial` is wired correctly. Landing on the index row is the extra press the spec asked for.
- **Home cache server:** the flows (switch, address and token panels, return focus) are intact on phone and TV. TV parity is finding 5.
- **2 s System refresh:** the phone is unchanged. The TV polls only while System is the section shown. A successful poll keeps the Catalogue node stable, so focus is not lost (the failure case is finding 7).
- **Contracts:**
  - `LanCacheUiState` fields are additive with defaults.
  - `lanCacheRows` adds a `("Chunks", null)` row, which both ledgers skip when null.
  - `TvSystemScreen()` and `TvSettingsRows` were removed and had no other callers.
  - The changed signatures (`TvAppearanceBlock`, `TvCacheBudgetBlock`, `TvSystemContent`) have all callers updated.
- **Phone spacing:** `SettingsColumns` does the per-row math correctly (Dp/Dp gives a Float, clamped to 1..size), and a short last row is padded with weighted spacers. No intrinsic-measurement ancestor exists, which matters because `BoxWithConstraints` cannot answer intrinsic measurements. The test compares px with dp, which works because Robolectric runs at mdpi.
- **Brief vs diff:** the brief mentions "LanCacheBlock field fonts", but that change is not in this worktree's diff.
- **Release:** 0.69.3 is consistent across Cargo.toml, Cargo.lock, web/package.json and build.gradle.kts, with versionCode left alone as intended. The changelog is inaccurate on TV (finding 5).

## Plan status
All phase-04 todos are ticked, but the "tests updated/extended" item is incomplete (finding 9), and findings 1–4 should be fixed before phase 05's on-device pass. I did not edit the plan files.

## Unresolved questions
- Should Up/Down at the edge of a section do nothing (as in the fix above), or should Down from the last index row enter the content? Down currently stays on the System row.
- Finding 7 was found by reading the code only. I did not simulate a flapping `SystemViewModel.failure`, because the fixture keeps it private.
