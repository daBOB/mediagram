# Phase 03 — Settings + System, phone and tablet (B · two-pane index)

## Context links

- Approved mockups: `~/.gstack/projects/daBOB-mediagram/designs/android-settings-system-20260927/round2/b-{telegram,storage,appearance,system}.{png,html}`, `round2/tokens.css` (index 320dp, page padding 76/64/72dp, title 112, gaps 48/72, ledger rows 13dp, pills 48dp)
- Web reference: `web/public/lib/catalog/settings-page.js:45-115` (head + tabs), `lib/settings-view.js`, `lib/settings-telegram.js`, `lib/settings-sessions.js`, `lib/status/status-view.js:85-113`, rail tally `web/public/app.js:121-123`, `styles/shell.css:61-70,198`
- Phase 01 (PageHead, tones, styles, radius) and 02 (Backdrop, labels) must be merged.

## Overview

Priority P1. Status: pending. Replace the single-column Settings list and the separate
System screen with one Settings frame: an index (Telegram · Storage · Appearance ·
System, each with a status line) and a page with the web's page head. Two panes on
EXPANDED width; one pane (index → section, with back) below it. Same ViewModels.

## Key insights (verified)

- **Entry path:** overflow item → `LibraryFlow.kt:42-43` → `LibraryPositions.openMenu` (`ui-common/.../LibraryPositions.kt:198-202`, a move, not a push, between menu screens) → MENU frame payload `MenuScreen.name` (`LibraryPositions.kt:142`) → `LibraryFlowBranches.kt:95-104` renders inside `LibraryBranch` → `LibraryScaffold` (`AppChrome.kt:66-127`: TopAppBar, overflow, profile, search). `MenuScreen` = System/TmdbKey/Settings (`feature/catalog/.../MenuScreen.kt:12-18`); `MenuActions.onSystem/onSettings` (`ui-common/.../MenuActions.kt:61-74`) stay.
- **Today's Settings** `ui-mobile/.../settings/SettingsScreen.kt:48-123`: SettingsViewModel + AppearanceViewModel, Library/Application panels (`:67-90`), sign-out dialog (`:118-122`), one LazyColumn (`:139-177`) whose order is pinned by tests (comment `:170-175`). Cache slot = `CacheSection()` (`LibraryFlowBranches.kt:102`, `CacheSection.kt:24-31`: one `refresh()` for the shared `CacheBudgetViewModel`). LAN block `LanCacheBlock.kt:46-112` owns the `ACCESS_LOCAL_NETWORK` launcher (API ≥ 37) and calls `open()`.
- **Today's System** `ui/system/SystemScreen.kt:43-91`: `SystemContent(current, failure, onRetry)` → four `Block`s from pure rows (`feature/system/.../SystemBlocks.kt`, `SystemRows.kt:182-195`); no Conversion block on purpose (KDoc `:37-40`).
- **`Block` (`ui/components/Block.kt:14`) has 8 callers, all Settings/System** — restyling it into the mockup's ledger restyles exactly this redesign and nothing else.
- **VM lifetime:** every `hiltViewModel()` here resolves to the activity store (no nav-entry owner); hoisting them to one Settings hub changes no lifetime. `LanCacheViewModel.open()` is documented as "Settings opening" (`LanCacheViewModel.kt:70-73`) — calling it on Settings entry is its intended use. `SystemViewModel` is a per-visit snapshot, `WhileSubscribed(5s)`.
- **Width classes:** the app already branches on `currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass` (`ShelfWall.kt:171-175`). EXPANDED (≥ 840dp) covers the Redmi Pad Pro both ways (1600 and 1068dp). MEDIUM (phone landscape) gets the single pane — a 320dp index would leave < 520dp of page.
- **Insets:** bars are transparent (`app/.../themes.xml`); outside `Scaffold` the frame must pad `WindowInsets.safeDrawing` itself (mockup: sidebar colour runs under the status bar).
- **Status facts exist:** Telegram — `SettingsUiState.account/connection` (`SettingsViewModel.kt:92-97`); Storage — `CacheBudgetViewModel.state` (held/budget) + `LanCacheUiState.connection/enabled`; Appearance — `Appearance` labels (phase 02); System — `SystemUiState.versionName` + `refreshLine` facts (`SystemRows.kt:141-159`). No "all current" fact exists (mockup text).
- **Tally:** web rail masthead = `countOf` of movies/series/tutorials as film/show/course, spelled out ≤ 20 (`format.js:165-175`), hidden on narrow (`shell.css:198`). Android shelves `feature/catalog/.../Shelves.kt:36-38` (Movies/Series/Tutorials). Android `countOf` (`ui-common/.../SearchLines.kt:50`) uses figures only.
- **Parity (web → Android B):**
  | Web | Android B |
  |---|---|
  | Appearance tab: theme, accent, artwork | Appearance section (all three) |
  | Profile tab: who + Switch profile + System link | Not an entry: the profile button in every bar (`AppChrome.kt:129-137`) already does it; System is an index entry |
  | Library & Telegram (admin): rows, Change library, App id/hash, Sign out, sessions | Telegram section |
  | … Cache Held + Size field | Storage › Cache (Held, Where, Budget chips) + Home cache server (Android-only) |
  | System page (own route) | System section; menu shortcut kept |
  | Telegram link / Watching now blocks | absent today → follow-up |
  | Conversion block | deliberately absent (`SystemScreen.kt:37-40`) |
  | — | TMDB key (Android-only) stays a menu screen, unchanged |

## Requirements

Functional
- `SettingsSection` enum (TELEGRAM, STORAGE, APPEARANCE, SYSTEM) with title + eyebrow: "Your account and where it's signed in" / "What this device keeps and where it comes from" / "Make it yours" (web) / System eyebrow per open question 1.
- Index: back arrow + "mediagram" wordmark (Fraunces 600 27sp), SETTINGS eyebrow, four rows (icon 22dp, name Geist 500 15sp, status Geist 12sp quiet, single line + ellipsis, held-dot when LAN connected), selected row `tint-active` fill (ink 10%), 6dp radius, ≥ 64dp rows; tablet-only tally under a rule-soft line. Background `tones.sidebar`, right hairline `tones.ruleSoft`.
- Page: `PageHead` (max 112sp expanded, 72sp compact) then the section. Columns on EXPANDED: Telegram 2 (Connection | Active sessions), Storage 2 (Cache | Home cache server), Appearance 1, System 3 (Catalogue + This app | Cache | Upstream). One column on compact.
- Telegram: ledger Account/Library/Datacenter/Session; accent line pills "Change library", "Application id and hash…"; quiet pill "Sign out" (confirm dialog kept); profile-reload + notice kept; sessions ledger with count, "This device" held mark, per-row quiet small pill "Sign out" → "Confirm sign out" (second tap, as today).
- Storage: Held (heldOfBudget) + thin accent bar; Where row (single volume) or volume chips + next-start note (multiple); Budget chips (humanSize, selected = accent border + tint); fell-back and failure lines kept; Home cache server ledger (Status, Holding), Grant action when permission needed, toggle row, Server address field (placeholder "Found automatically", note, pill "Save"), Pairing token field (password, pill "Replace"/"Save"), rejection/error lines kept.
- Appearance: theme swatch cards (web gradients `appearance.css:52-54`), accent dots (34dp), artwork cards (`appearance.css:55-58`), selected ring = accent 2dp outside a paper gap (`appearance.css:50`).
- System: `SystemContent` restyled; failure + retry kept.
- Navigation: MenuScreen.Settings → Settings (expanded: Telegram selected; compact: index). MenuScreen.System → Settings with System selected; on compact Back from that page leaves the frame (it was asked for directly). Compact from index: Back → index → leave. A panel (Library/Application) answers Back first. Settings/System render **without** `LibraryScaffold` (no bar, as the mockup); TmdbKey keeps it.
- Every control 48dp touch target; index + section heads `heading()` semantics; selection via `selectable`/`selectableGroup`.

Non-functional
- Files < 200 lines; no ViewModel API changes (status functions are pure and live beside the rows they summarise).

## Architecture

```
FrameKind.MENU (LibraryFlowBranches)
 ├ Settings ─► SettingsScreen(initial = null|TELEGRAM, tally)      ┐ outside LibraryScaffold,
 ├ System   ─► SettingsScreen(initial = SYSTEM, leavesFromSection) ┘ own BackHandler(at::pop)
 └ TmdbKey  ─► LibraryBranch { TmdbKeyScreen }  (unchanged)
SettingsScreen = hub: SettingsVM · AppearanceVM · CacheBudgetVM · LanCacheVM · SystemVM
  on entry: settings.refresh(); cache.refresh(); lan.open()
  statuses: telegramStatus(s) · storageStatus(occupancy, lan) · appearance labels · systemStatus(sys, now)
  section: rememberSaveable (hoisted above the width branch, survives rotation)
  SettingsPanes: EXPANDED → Row(SettingsIndex 320dp, SettingsPage) ; else index XOR page
  SettingsPage: PageHead + TelegramSection | StorageSection | AppearanceSection | SystemContent
```
Data in: VM StateFlows (unchanged). Transform: pure status/row functions. Out: VM calls (chooseLibrary, changeApplication, signOut, revokeSession, choose/chooseVolume, setEnabled/setManualAddress/saveToken, chooseTheme/Accent/Backdrop, retry).

## Related code files

Modify
- `android/ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt` (MENU branch `:95-104`; imports)
- `android/ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt` (hub; drop `cache` slot)
- `android/ui-mobile/src/main/kotlin/ui/settings/CacheSection.kt` → `git mv` to `StorageSection.kt` (two columns)
- `android/ui-mobile/src/main/kotlin/ui/settings/{CacheBudgetBlock,CacheVolumeBlock,LanCacheBlock,SessionsSection,AppearanceSection}.kt`
- `android/ui-mobile/src/main/kotlin/ui/components/Block.kt` (ledger)
- `android/ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt` (columns; no own padding)
- `android/feature/setup/src/main/kotlin/SettingsRows.kt` (+ `telegramStatus`)
- `android/feature/system/src/main/kotlin/SystemRows.kt` (+ `systemStatus`), `LanCacheStatusLine.kt` (+ `lanCacheRows`, `storageStatus`)

Create
- `android/ui-common/src/main/kotlin/ui/settings/SettingsSections.kt` (enum + `IndexStatus`)
- `android/ui-mobile/src/main/kotlin/ui/settings/SettingsPanes.kt`, `SettingsIndex.kt`, `TelegramSection.kt`, `SettingsControls.kt` (line/quiet pill, chip, swatch card)
- `android/feature/catalog/src/main/kotlin/LibraryTally.kt` (+ spelled counts, web `format.js` rules)
- `android/core/designsystem/src/main/res/drawable/ic_settings_{telegram,storage,appearance,system}.xml` (stroke vectors from `round2/b-telegram.html` SVGs)
- Tests: `ui-mobile/src/test/kotlin/ui/settings/SettingsPanesTest.kt`, `feature/catalog/src/test/kotlin/LibraryTallyTest.kt`

Delete: none (`CacheSection.kt` is renamed, not duplicated). `Destination.System/Settings` (`Destination.kt:59-63`) become unused by the bar but stay (DestinationTest, `MenuScreen.destination`).

Tests to update
- `ui-mobile/src/test/kotlin/ui/MobileAppTest.kt:120-135` — compact: Menu → Settings → "Telegram" → "Sign out"
- `ui-mobile/src/test/kotlin/ui/settings/SettingsProfileRetryTest.kt:98-129` — new host signature; open Telegram first; `:129` asserts the section, not the old block heading
- `ui-mobile/src/test/kotlin/ui/LibraryFlowTest.kt:117-129,159-170` — no overflow inside Settings/System; overlay flows go through TMDB key instead, System → Back → title kept
- `CacheSectionTest.kt:142-151` (host `StorageSection`), `CacheBudgetBlockTest.kt`, `CacheVolumeBlockTest.kt:102-120` (strings kept), `LanCacheBlockTest.kt:78-144` (buttons by content description "Save address"/"Save token"; field label), `AppearanceSectionTest.kt:46-76` (+ artwork; "Artwork" is both heading and option → match `hasText and isSelectable()`), `SystemScreenTest.kt` (strings kept)
- `feature/setup/src/test/kotlin/SettingsRowsTest.kt`, `feature/system/src/test/kotlin/{SystemRowsTest,LanCacheStatusLineTest}.kt` (+ status functions)

## Implementation steps

1. **Shared commit (unblocks phase 04):** `SettingsSections.kt`; `telegramStatus`, `storageStatus`, `lanCacheRows`, `systemStatus` + their tests; `LibraryTally.kt` + test; four icon drawables; `Block` → ledger.
2. `SettingsControls.kt`: LinePill (accent border/text), QuietPill (rule border, ink-2), small variant keeping 48dp target, Chip (6dp, selected accent), SwatchCard (16:9, 12dp like web swatches, selected ring).
3. `SettingsIndex.kt` (+ tally when EXPANDED) and `SettingsPanes.kt` (branch, insets, back rules).
4. `SettingsScreen.kt` hub: VMs, entry effects, statuses, panels over the page area, sign-out dialog.
5. `TelegramSection.kt` (from today's rows + `SessionsSection` restyle).
6. `StorageSection.kt` (rename) + restyle the three storage blocks; permission launcher stays in `LanCacheBlock`.
7. `AppearanceSection.kt`: three questions, enum labels from phase 02.
8. `SystemScreen.kt`: columns; if open question 1 = poll, `LaunchedEffect` re-read every 2s while composed.
9. `LibraryFlowBranches.kt` MENU branch: Settings/System → `SettingsScreen(...)` with `BackHandler`; pass tally from `catalogState`.
10. Update listed tests; add `SettingsPanesTest` (compact `w400dp-h800dp`, expanded `w1600dp-h1068dp`): index→section→back; System shortcut back leaves; expanded shows index + Telegram; sign-out reachable.
11. Compile; run ui-mobile, feature/setup, feature/system, feature/catalog unit tests.

## Todo

- [ ] Shared commit: sections enum, status fns (+tests), tally (+test), icons, ledger Block
- [ ] Controls (pills, chips, swatch cards)
- [ ] Index + panes (insets, back rules, tally on expanded)
- [ ] Hub SettingsScreen
- [ ] Telegram section (+ sessions)
- [ ] Storage section (rename + 3 blocks)
- [ ] Appearance section (theme, accent, artwork)
- [ ] System columns (+ poll per Q1)
- [ ] MENU branch wiring
- [ ] Tests updated + SettingsPanesTest
- [ ] Compile + tests green

## Success criteria

- Tablet: four sections match `round2/b-*.png` in structure, spacing and tokens (checked in phase 05).
- Compact: index → section → Back → index → Back → previous screen; System shortcut → System → Back → previous screen.
- Every action reachable before is reachable now (checklist: change library, app id/hash, sign out, revoke session, budget, volume, LAN toggle/address/token/grant, theme, accent, artwork, system retry).
- All listed tests green; no file > 200 lines.

## Risks

| Risk | L×I | Mitigation |
|------|-----|------------|
| No overflow menu inside Settings (mockup has no bar) — Update library/Start over need Back first | M×L | Deliberate, written into DESIGN.md; tests moved to TMDB-key overlays |
| Section state lost on rotation when the width class flips | M×M | `rememberSaveable` hoisted above the branch; test in `SettingsPanesTest` |
| Nested scroll: two scrolling columns inside a row | M×M | Each pane `verticalScroll`; no LazyColumn inside scroll |
| Hub reads five VMs on entry → more work than one screen did | L×L | Reads are what each block already did per visit; `open()` is its documented trigger |
| Setup screens reused as panels look wrong in a 1280dp pane | M×L | Constrain panel width (≈ 560dp) in the page; screenshot in 05 |
| Mockup text vs facts ("all current") | — | Status from real facts; listed as deliberate difference |

## Security

api_hash never shown (only `apiId` prefills, `SettingsUiState.kt`); pairing token stays `PasswordVisualTransformation` and is cleared after save; sign-out keeps its confirm dialog; session revoke keeps its second-tap confirm; no new permission (the existing `ACCESS_LOCAL_NETWORK` request is unchanged).

## Next steps

Phase 04 reuses step 1's shared commit. Deliberate mockup differences (for DESIGN.md in 05):
honest System status; no "not yet on Android" tag; no per-session device glyphs (SessionSummary has only free-text fields); `humanSize` chip labels; next-start note under Where; tally tablet-only.
