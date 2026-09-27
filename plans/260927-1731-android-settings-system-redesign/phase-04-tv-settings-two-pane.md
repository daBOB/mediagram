# Phase 04 — TV: Settings as a remote-driven two-pane index

## Context links

- Phase 03 step 1 (shared commit): `ui-common/.../settings/SettingsSections.kt`, status functions, icons, ledger rules
- Ten-foot tokens: `core/designsystem/.../Spacing.kt` `Overscan` (48×27dp, commit e74f00f8), `Type.kt` `TvTypeScale` (title 34sp, body ≥ 18sp — `TvTypeScaleTest`)
- TV dark-only decision: `ui-tv/.../TvTheme.kt` KDoc, `docs/system-architecture.md:595`
- Approved tablet mockups (the TV follows their structure, not their pixel sizes)

## Overview

Priority P2. Status: pending. Give the television the same Settings structure: index on
the left (focusable, each row with its status), the selected section on the right under
the page head, driven by the D-pad. Existing TV controls, words and question panels are
kept; this is layout, tokens and the Artwork question.

## Key insights (verified)

- **Entry:** `TvMenuPage.kt:66-67` rows "System"/"Settings" → `tvMenuActions` (`TvMenuBranches.kt:43-44`, records the restore key, then `at.openMenu`) → `TvMenuScreenBranch` (`TvMenuBranches.kt:67-83`, `BackHandler(leave)`) → `TvSystemScreen()` (`:80`) / `TvSettingsScreen()` (`:81`). Back from a menu screen returns the remote to the menu row that opened it (restore keys).
- **Today's TV Settings** `system/TvSettingsScreen.kt:35-…`: Settings + Appearance VMs, entry `refresh()` (`:47`), panels `TvSettingsPanel { Library, Application, LanAddress, LanToken }` (full-screen question pages — TV text entry), `lastPanel` for focus return, `TvConfirmDialog` for sign out. Rows `TvSettingsRows.kt:33-89`: one scrolling `TvPage` — title, `TvAppearanceBlock` (accent only), Telegram `TvInfoBlock`, three `TvTextRow`s, sessions, `TvCacheBudgetBlock` (own `refresh()` `:35`), `TvCacheVolumeBlock`, `TvLanCacheBlock` (own `open()` `:51`). Landing focus `:42-51`.
- **Today's TV System** `TvSystemScreen.kt:44-94`: own `TvPage` + title + four focusable `TvInfoBlock`s, first focused on arrival, retry focused on failure.
- **`TvInfoBlock` (`TvInfoBlock.kt:33`) has 7 callers, all Settings/System** — restyle to the ledger safely.
- **Tests pin behaviour, not layout:** `ui-tv/src/test/kotlin/ui/tv/TvMenuTest.kt:88-241` (System lands on "Catalogue" `:93`; Settings lands on "Change library" `:107`; panel Back returns to its row `:116-126`; sign-out dialog `:128-135`; storage rows by their words "●  Internal storage", "Use the home cache server — on", "Server address — found on the network", "Pairing token — none" `:170-223`; sessions two-press revoke `:225-241`); `TvAppearanceBlockTest.kt:46`.
- TV is dark-only: Appearance asks accent (+ now artwork), never theme — unchanged difference. `TvFocus.kt:70` square focus shape stays (follow-up).

## Requirements

Functional
- Layout inside `TvPage` + `Overscan`: index column ≈ 260dp (SETTINGS eyebrow 16sp spaced caps, four rows: icon, name 18sp, status 16sp quiet, selected fill) | page: `PageHead` (max `TvTypeScale.pageTitleMax` 72sp, eyebrow 16sp) + section in a vertically scrolling single column. No wordmark/back arrow (Back is the remote's), no tally (unreadable at 3m; the web hides it on narrow too).
- Focus: arriving from "Settings" → index row Telegram focused; from "System" → index row System focused. Focus moving along the index selects that section (content follows). Right/OK → the section's first control; Left from any section control → the selected index row; Back inside a section → the selected index row; Back on the index → leave (menu row regains focus, as today).
- Sections reuse today's TV controls and words: Telegram (ledger, Change library, Application id and hash…, Sign out, profile reload, sessions), Storage (budget rows, volume rows, home cache rows + panels), Appearance (accent swatches + four artwork swatch cards with label + note), System (`TvSystemContent` without its own page/title; blocks stay focusable for scrolling).
- Panels keep today's behaviour; after a panel closes, the same section is showing and the opening control has focus (section state `rememberSaveable`).
- Entry effects move to the TV hub: `settings.refresh()`, `cache.refresh()`, `lan.open()` once per visit (not per section), so index statuses are true while Telegram is selected.

Non-functional
- Body text ≥ 18sp except the spaced-caps eyebrow/status (16sp); every focusable shows the existing `TvFocus` treatment.

## Architecture

```
TvMenuScreenBranch
 ├ Settings ─► TvSettingsScreen(initial = TELEGRAM)
 └ System   ─► TvSettingsScreen(initial = SYSTEM)
TvSettingsScreen (hub): SettingsVM · AppearanceVM · CacheBudgetVM · LanCacheVM · SystemVM
  section (rememberSaveable) · panel (existing enum) · statuses (phase 03 pure fns)
  panel != null → existing full-screen panel
  else TvSettingsPanes: Row(TvSettingsIndex(section, statuses, onFocusSection),
                            TvPage column: PageHead + TvTelegramSection | TvStorageSection | TvAppearanceBlock | TvSystemContent)
  focus: indexRows[section] ⇄ sectionEntry (FocusRequesters; focusProperties left/right)
```

## Related code files

Modify
- `android/ui-tv/src/main/kotlin/ui/tv/TvMenuBranches.kt` (`:80-81`)
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsScreen.kt` (hub, `initial` param, statuses, panes)
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsRows.kt` → `git mv` to `TvTelegramSection.kt` (Telegram content only; keeps `TvProfileReload`)
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSystemScreen.kt` (content without page/title; `TvSystemScreen()` removed or kept as thin wrapper if referenced)
- `android/ui-tv/src/main/kotlin/ui/tv/system/{TvAppearanceBlock,TvInfoBlock,TvCacheBudgetBlock,TvLanCacheBlock,TvSessionsBlock}.kt` (artwork cards; ledger; entry effects moved to hub)
- Tests: `ui-tv/src/test/kotlin/ui/tv/TvMenuTest.kt`, `ui-tv/src/test/kotlin/ui/tv/system/TvAppearanceBlockTest.kt`

Create
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsPanes.kt` (row, page head, focus wiring)
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsIndex.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvStorageSection.kt` (the three storage blocks + `returningFrom`)

Delete: none.

## Implementation steps

1. Hub: move VMs and entry effects into `TvSettingsScreen`; add `initial: SettingsSection`; compute statuses with phase 03's functions.
2. `TvSettingsIndex` (focusable rows, `onFocusChanged` → select; FocusRequester per row).
3. `TvSettingsPanes`: `Row`; page `PageHead`; section switch; `focusProperties` so Left returns to the selected row and Right/OK enters the section's first control; `BackHandler(enabled = focus in section)` → index row.
4. Split content: `TvTelegramSection` (rename), `TvStorageSection`, `TvSystemContent` (strip page/title), `TvAppearanceBlock` + artwork cards (labels/notes from `Backdrop`).
5. `TvInfoBlock` → ledger (label quiet left, value ink-2 right, rule-soft hairline, TV sizes).
6. `TvMenuBranches.kt:80-81` → `TvSettingsScreen(initial = …)`.
7. Update `TvMenuTest` flows: Settings → index Telegram focused → OK → "Change library" focused; storage tests select "Storage" first; System → index System focused → OK → "Catalogue" focused; Back chain section → index → menu row. Add: moving down the index swaps the page head; Left from a section control returns to the index; artwork choice reaches `chooseBackdrop` (`TvAppearanceBlockTest`).
8. Compile ui-tv; run ui-tv unit tests.

## Todo

- [ ] Hub (VMs, entry effects, initial section, statuses)
- [ ] Index rows + focus-selects
- [ ] Panes + focus/Back wiring
- [ ] Telegram/Storage/System/Appearance(+artwork) sections
- [ ] TvInfoBlock ledger
- [ ] Menu branch wiring
- [ ] TvMenuTest + TvAppearanceBlockTest updated/extended
- [ ] Compile + tests green

## Success criteria

- Every TV Settings action reachable with the remote alone (same checklist as phase 03 minus theme).
- Back from any depth ends on the menu row that opened Settings/System (existing restore behaviour).
- ui-tv unit tests green; on the TV emulator/box (phase 05, optional) no focus trap between panes.

## Risks

| Risk | L×I | Mitigation |
|------|-----|------------|
| Focus traps / lost focus when the section swaps under a focused index | M×H | Section entry via explicit FocusRequesters; tests for Left/Right/Back |
| Focus-selects makes a long sessions list recompose while scrolling the index | L×L | Only four rows; content swap is cheap |
| Page too narrow for long ledger values at 18sp (≈ 600dp) | M×L | Single column; values wrap; screenshot in 05 |
| `onNodeWithText("System")` ambiguity (index row vs "System" menu row) in tests | M×L | Menu page is not composed while the frame is on top; assert with `assertIsFocused` on unique nodes |

## Security

Unchanged: token/address entered in the existing question panels; sign-out confirm and two-press session revoke kept.

## Next steps

Phase 05 verification; `docs/system-architecture.md:595` gains "accent and artwork".
