## Phase Implementation Report

### Executed Phase
- Phase: phase-04-tv-settings-two-pane
- Plan: /home/andre/Workspace/mediagram-channel-index/plans/260927-1731-android-settings-system-redesign
- Worktree: /home/andre/Workspace/mediagram-channel-index (branch feat/android-settings-redesign, on top of phase 01-03's 225b3bd6 / 0.69.2)
- Status: completed, not committed (lead commits)

### IMPORTANT — concurrent edits found in this worktree, not mine
Partway through this session `git status` in this same worktree showed uncommitted
modifications I did not make: `android/feature/system/src/main/kotlin/LanCacheStatusLine.kt`,
`LanCacheUiState.kt`, `LanCacheViewModel.kt` (+ their tests) and
`android/ui-mobile/src/main/kotlin/ui/settings/{SettingsControls,SettingsIndex,SettingsPage}.kt`
(+ `SettingsPanesTest.kt`) — adding a "Chunks"/server-budget row to the home-cache-server
ledger. File mtimes put them ~20:32-20:35, a few minutes after this session started, and
unchanged since (stable for the rest of the session) — another agent, not a runaway process.
None of these files are in phase 04's ownership (all `ui-tv/*`), and I verified the one call
site I share with them (`lanCacheStatusLine(state)`, used by `TvLanCacheBlock`) kept its
signature, so my work compiles and tests clean regardless. I did not touch, revert, or
build on top of these files. Flagging for the lead to reconcile before committing — if this
worktree is meant to be exclusive to phase 04, something is running in it that shouldn't be.

### Files Modified

Create
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsIndex.kt` (163 lines): the left pane — SETTINGS eyebrow, four focusable rows (icon, name, one-line status), selected fill. A row's focus alone (`onFocusSection`) only selects; a click/OK (`onEnterSection`) also enters.
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsPanes.kt` (101 lines): index beside the page (television is one width, no phone-style narrower fallback), `PageHead`, `focusProperties` wiring Right (index → the shown section's own entry point) and Left (any section control → the selected index row), `BackHandler` returning content → index (Back on the index itself is left unhandled, falling through to `TvMenuScreenBranch`'s own leave `BackHandler`).
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvStorageSection.kt` (28 lines): the three storage blocks stacked in one column.

Modify
- `android/ui-tv/src/main/kotlin/ui/tv/TvMenuBranches.kt`: `TvSettingsScreen(initial = SettingsSection.SYSTEM/TELEGRAM)` replaces the old `TvSystemScreen()`/`TvSettingsScreen()`.
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsScreen.kt` (rewritten, hub): all 5 ViewModels (Settings/Appearance/CacheBudget/LanCache/System), `section`/`focusInContent`/`panel`/`lastPanel` hoisted here (survive a panel's own unmount of `TvSettingsPanes`), the three entry-effects (`settings.refresh`/`cache.refresh`/`lan.open`) run once per visit here instead of per section, index statuses computed from phase 03's pure functions.
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsRows.kt` → `git mv` → `TvTelegramSection.kt`: gutted to Telegram-only content (ledger, Change library/Application id/Sign out, sessions); keeps `TvProfileReload`.
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSystemScreen.kt`: `TvSystemScreen()` removed (nothing references it once `TvMenuBranches` calls the hub directly); `TvSystemContent` strips its own page/title (the pane draws one `PageHead` for every section) and gained the 2s poll while shown.
- `TvAppearanceBlock.kt`: now draws Accent *and* Artwork (four square swatch cards, label + note), no longer just Accent; Theme still not asked (TV stays dark).
- `TvInfoBlock.kt`: ledger restyle — quiet label left, ink value right in its own weighted/wrapping column, soft hairline under every row (was `SpaceBetween` with no rule).
- `TvCacheBudgetBlock.kt`: dropped its own `refresh()` trigger (hub's job now); its "Cache" heading is the section's entry point (always drawn, loading or not, so there's never a race waiting for async data — same trick `TvSystemContent` already used for Catalogue).
- `TvLanCacheBlock.kt`: dropped its own `open()` trigger; its own panel-return-focus logic (`returningFrom`) unchanged.
- Tests: `ui-tv/src/test/kotlin/ui/tv/TvMenuTest.kt`, `ui-tv/src/test/kotlin/ui/tv/system/TvAppearanceBlockTest.kt`.

### Tasks Completed
All 8 implementation steps and all 8 todo items in the phase file, checked.

### A real bug found and fixed while testing
Clearing `lastPanel` back to `null` a moment after a panel closes (so a *later*, unrelated
re-entry into a section doesn't replay a stale panel's own return-focus) causes a **second**
recomposition of the shown section with `returningFrom` now `null`. Two sibling effects
(`TvTelegramSection`'s and `TvCacheBudgetBlock`'s) were originally keyed on
`(focusInContent, returningFrom)` — that second recomposition re-ran them, and their own
"land on my default entry point" branch fired again, **stealing focus back** off the row
`TvLanCacheBlock`/the Application panel had already correctly refocused a moment earlier.
Fixed by keying both effects on `focusInContent` alone (still reading `returningFrom` inside
the body) — Compose does not restart a `LaunchedEffect` whose key didn't change, so the
already-correct one-shot landing sticks. Caught by
`settingsShowsTheHomeCacheServerAndAnAcceptedAddressReturnsToItsRow` failing with the address
row reporting `Focused=false` after an accepted address; not something a static read would
have found.

### Tests Status
- Type check / compile: `:ui-tv:compileDebugKotlin` and `:ui-tv:compileDebugUnitTestKotlin` clean.
- Full verify (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin`): **BUILD SUCCESSFUL**, lint clean (0 errors; the run did surface one real lint error in my own new `TvAppearanceBlockTest` — a bare `FocusRequester()` not wrapped in `remember` inside composition, `RememberInComposition` — fixed before the final green run).
- ui-tv module unit tests: **322 → 325** (+3: `TvMenuTest` +1 net — `movingTheIndexOnlySwapsThePageHeadWithoutEnteringTheSection` added, three others renamed in place to match the new flow, none deleted; `TvAppearanceBlockTest` +2 — artwork-selected and artwork-choice-reaches-`chooseBackdrop`). Counted by `@Test` grep at `HEAD` (225b3bd6) vs. now, cross-checked against the JUnit XML's own `tests=` total for the module (325, matching).
- Whole-repo `testDebugUnitTest` aggregate this run: 1521 — **not directly comparable** to phase 03's own reported 1526, because the concurrent LAN-cache/ui-mobile work described above touches `feature:system` and `ui-mobile` test counts independently of phase 04; the ui-tv delta above (322→325) is the number phase 04 actually owns.
- Robolectric/semantics coverage of the new D-pad behaviour, in `TvMenuTest`: index-row-focused-but-not-entered on arrival (`systemLandsOnItsIndexRowThenOkEntersItAndBackWalksOutOneStepAtATime`, `settingsLandsOnItsIndexRowThenOkEntersTelegramAndBackWalksOutOneStepAtATime`), moving the index without entering swaps the page head and leaves nothing inside the section focused (`movingTheIndexOnlySwapsThePageHeadWithoutEnteringTheSection`, via `SemanticsActions.RequestFocus` rather than a real key event — see caveat below), the full Back chain content → index → masthead row (all Settings/System/Telegram-panel tests), and Storage/Telegram flows updated to the two-step select-then-enter navigation. Artwork's `chooseBackdrop` reach confirmed in `TvAppearanceBlockTest`.

### Screenshots (skipped)
No Roborazzi/Paparazzi/`captureToImage`-based screenshot harness exists anywhere in this
project (`grep -rl roborazzi\|paparazzi` across `android/` is empty) — skipping per the
task's own fallback instruction rather than bolting one on for this phase alone.

### Issues Encountered / deliberate choices
- **Real DPAD Right/Left is not exercised by these tests.** `focusProperties { right = entryRequester }` (index → section) and `focusProperties { left = rowRequesters[section] }` (any section control → the selected index row) are real Compose focus-search wiring, but this codebase's own precedent (`ui-tv/build.gradle.kts`'s comment on why `TvFocusTest` needs the real, instrumented `androidTest` suite, and `TvPlayerKeysTest`'s own key handling tested as a pure function rather than through Compose key dispatch) is that real key-event-driven focus movement doesn't run true under Robolectric. I followed that precedent: the new tests exercise "moving the index" via the `RequestFocus` semantics action (which does run the real focus system, just not through a real key event) and "entering" via a click/OK (`performSemanticsAction(OnClick)`), matching how every existing TV test in this module already navigates. The `focusProperties` wiring itself is untested beyond compiling; a real-device or `androidTest` pass (phase file's own "on the TV emulator/box (phase 05, optional)") would be the way to confirm Right/Left on an actual remote — I did not touch a device or emulator per this session's constraints.
- **"Telegram" ambiguity, beyond what the phase's risk table named.** The phase's risk table flagged `System` (index row vs. masthead row); I hit the same class of collision for `Telegram` specifically, between the index row's own label and `TvTelegramSection`'s own "Telegram" ledger heading — the content pane is always in the tree once a section is *selected*, not only once *entered*, so both nodes coexist far more often than the System/masthead case does. Fixed with a `hasText(...) and hasClickAction()` test helper (only the row has an `OnClick` action), not a production-code change.
- **Storage's "Cache" heading is the section's entry point**, not a chip further down: cache-budget choices only exist once the async read finishes, and I didn't want the entry landing to race that (the same reason `TvSystemContent` already lands on "Catalogue" rather than a row inside it). Flagging in case the lead would rather the first *interactive* control receive it instead.
- No file-ownership conflicts within phase 04's own scope; the one external-worktree anomaly is reported above.

### Next Steps
Phase 05 (verify/docs/version) is not started. `docs/system-architecture.md:595` still needs
"accent and artwork" for TV per the phase file's own "Next steps" — left to phase 05, not
touched here. A real-device/emulator pass to confirm Right/Left directional focus (this
session touched no device per its constraints) would close the one gap noted above.

### Unresolved Questions
- Is the concurrent LAN-cache/`ui-mobile` work in this same worktree (see banner above)
  expected — a second agent deliberately sharing this worktree — or a mistake the lead
  needs to untangle before committing phase 04?

**Status:** DONE_WITH_CONCERNS
