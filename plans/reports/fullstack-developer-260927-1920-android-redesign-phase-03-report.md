# Phase Implementation Report

### Executed Phase
- Phase: phase-03-settings-system-phone-tablet
- Plan: /home/andre/Workspace/mediagram-channel-index/plans/260927-1731-android-settings-system-redesign
- Worktree: /home/andre/Workspace/mediagram-channel-index (branch feat/android-settings-redesign, on top of phase 01/02's 0.69.1)
- Status: completed, not committed (lead commits)

### Files Modified

Create
- `android/ui-common/src/main/kotlin/ui/settings/SettingsSections.kt` (25 lines): `SettingsSection` enum (title + pageEyebrow), `IndexStatus`.
- `android/feature/catalog/src/main/kotlin/LibraryTally.kt` (42): `libraryTallyLines` — web `spellCount`/`countOf` ported (spelled ≤20, figures above).
- `android/core/designsystem/src/main/res/drawable/core_designsystem_ic_settings_{telegram,storage,appearance,system}.xml`: stroke vectors traced from the mockup's inline SVGs (prefixed per the module's lint `resourcePrefix`, not `ic_settings_*` as the phase file names them).
- `android/ui-mobile/src/main/kotlin/ui/settings/{SettingsIndex,SettingsPanes,SettingsPage,SettingsControls,TelegramSection}.kt` — index, two/one-pane branch + back rules, page head + section dispatch, pills/chip/swatch-card/columns, Telegram's Connection column + ProfileReload.
- Tests: `feature/catalog/src/test/kotlin/LibraryTallyTest.kt`, `ui-mobile/src/test/kotlin/ui/settings/SettingsPanesTest.kt`, `ui-mobile/src/test/kotlin/ui/system/SystemScreenPollTest.kt`.

Modify
- `feature/setup/src/main/kotlin/SettingsRows.kt` (+`telegramStatus`), `feature/system/src/main/kotlin/SystemRows.kt` (+`systemStatus`), `feature/system/src/main/kotlin/LanCacheStatusLine.kt` (+`lanCacheRows`, `storageStatus`; `lanCacheStatusLine` refactored, not behaviour-changed) + their tests.
- `ui-mobile/src/main/kotlin/ui/components/Block.kt`: ledger restyle (`LedgerEntry`: label/value/meterFraction/onClick/selected).
- `ui-mobile/src/main/kotlin/ui/settings/{SessionsSection,CacheBudgetBlock,CacheVolumeBlock,LanCacheBlock,AppearanceSection,SettingsScreen}.kt`: restyled to the mockup tokens; `SettingsScreen` rewritten as the 5-ViewModel hub.
- `ui-mobile/src/main/kotlin/ui/settings/CacheSection.kt` → `git mv` → `StorageSection.kt` (two-column host, no own ViewModel reads).
- `ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt`: 3-column layout (Catalogue+This app | Cache | Upstream), 2s poll while composed.
- `ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt`: MENU branch — Settings/System now call `SettingsScreen` directly (no `LibraryScaffold`); TmdbKey unchanged.
- Tests updated: `MobileAppTest`, `SettingsProfileRetryTest`, `LibraryFlowTest`, `CacheSectionTest` (kept filename, hosts `StorageSection`), `AppearanceSectionTest` (+backdrop), `LanCacheBlockTest` (content-description buttons, short field label), `SystemScreenTest` (+`expanded` param). `CacheVolumeBlockTest`/`CacheBudgetBlockTest` needed no edits.
- Release bookkeeping: `Cargo.toml`/`Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (`versionName` only) `0.69.1` → `0.69.2`; `docs/project-changelog.md` new `## 0.69.2` entry; `plan.md` + phase file ticked complete.

### Tasks Completed
All ten implementation steps and all ten todo items in the phase file.

### Tests Status
- Type check / compile: pass, `ui-mobile`/`ui-common`/`feature:{catalog,setup,system}`/`core:designsystem` main + test sources clean.
- Full verify (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin`): **BUILD SUCCESSFUL**, all lint clean (including the `core:designsystem` resource-prefix lint the new icons had to satisfy).
- Unit tests: 1496 (phase 02's own count) → **1521** (+25): `LibraryTallyTest` +4, `SettingsRowsTest` +3, `SystemRowsTest` +4, `LanCacheStatusLineTest` +5, `AppearanceSectionTest` +2, `SystemScreenPollTest` +2, `SettingsPanesTest` +3, `LanCacheBlockTest` +1, plus net deltas from renamed/adjusted existing tests (no test deleted).
- No new lint warnings beyond the pre-existing `compose_compiler_config.conf` notice (present before this phase, per phase 01's own report).

### Issues Encountered (debugging notes, since fixed)
- Two `Block(heading, rows: List<…>)` overloads erased to the same JVM signature (`Pair` vs `LedgerEntry`) — resolved with `@JvmName("blockOfEntries")` on the richer one.
- `designsystem.Interface`/`Display` (the Geist/Fraunces `FontFamily`s) are `internal` to `core:designsystem` — every Settings pill/wordmark reaches its type through a public `MaterialTheme.typography.*` role instead (`bodyMedium`/`titleLarge`), not the family directly.
- `SwatchCard` originally put `.selectable()` on its own nested `Box`, with the caller's `.semantics(mergeDescendants = true) {}` on an *outer* `Column` two levels up — the `Selected`/`Role`/`OnClick` semantics never reached the merged node (`assertIsSelected()` failed with the property simply absent). Fixed by making `SwatchCard` purely presentational and moving `.selectable()` onto the same `Column` that declares the merge boundary — the exact pattern the existing (working) accent-dot code already used.
- `compose.performClick()` (v2 API) injects a real touch gesture at the node's on-screen bounds, not a semantics action call — a control pushed below a short Robolectric window's fold by the new page head's height gets a silent no-op click (no exception, no effect). Fixed with the same `qualifiers = "w400dp-h2400dp"` tall-window `@Config` `LibraryFlowTest`/`HeroBackdropModesTest` already use (`AppearanceSectionTest`, `SettingsProfileRetryTest`), or `.performScrollTo()` for a multi-scenario file (`MobileAppTest`).
- `core:designsystem`'s convention plugin enforces a `resourcePrefix` (`core_designsystem_`) lint rule the phase file's own drawable names (`ic_settings_*`) don't carry — renamed to `core_designsystem_ic_settings_*`.
- `CacheVolumeBlock`'s single-volume "Where" row lost its `RadioButton`/`Selected` semantics when redrawn as a plain ledger row with a bare `.clickable()` — added `LedgerEntry.selected` so it stays `selectable(selected = true, …)` (it's the only choice, always "selected"), keeping `CacheVolumeBlockTest`'s existing assertion valid.

### Differences from the mockups (deliberate, for phase 05's DESIGN.md pass)
- **Storage's DOM order**: `CacheVolumeBlock`'s "Where" row draws *after* the Budget chip picker, not between "Held" and Budget as the mockup's own ledger shows — kept `CacheBudgetBlock`/`CacheVolumeBlock` fully independent (matches the pre-phase file's own separation rationale) rather than interleaving two composables' output.
- **Budget/volume chip height**: drawn at the mockup's own 48dp (`round2/b-storage.html`'s `.segments label`), not the 36dp `DESIGN.md` Components section's earlier "chip" token guess — the round-2 screenshot is the newer source of truth.
- **Theme/Artwork swatch pictures**: flat `Palette` role fills (`Ground`/`Page`/`Sunk`, `colorScheme.primary` at reduced alpha for Blurred/Artwork) rather than the mockup's own decorative CSS gradients — keeps the "no new hex outside `core:designsystem`" rule phase 01 established; visually close but not pixel-identical.
- **Toggle**: Material3 `Switch` with `SwitchDefaults.colors` overrides (sunk/quiet at rest, accent when on) rather than a hand-drawn 52×32dp control — same colour language, default Material geometry.
- **System's index status and honest health**: mockup's fixed "0.68.13 · all current" replaced with the real version and a real health check (failed reads / lost session / refused refresh → "needs attention") — the phase file's own "Next steps" names this as expected.
- **Session rows carry no per-platform icon** — `SessionSummary` has only free-text fields; also explicitly pre-approved by the phase file.
- **No "not yet on Android" tag on Artwork** — required by the phase (Artwork is live), confirmed removed.
- Every other structural detail (index 320dp, page padding 76/64/72, ledger row 13dp, pill/chip/swatch-card radii and colours, System's 2s poll, compact/expanded back rules) matches the approved mockups and `DESIGN.md`'s own component tokens.

### Unresolved Questions
None — all open questions the phase file left were answered by the plan (System polls at 2s) or resolved above as deliberate, documented deviations.

---

## Addendum — tablet-check fix round (0.69.2, same unreleased commit)

The coordinator compared a fresh 0.69.2 build against the round-2 mockups on the Redmi Pad Pro (1600×1068dp landscape) and found 6 real regressions plus one confirmed-fine item, from four screenshots (`/tmp/.../scratchpad/p03-{telegram,Storage,Appearance,System}.png`) against `round2/b-*.png`. All fixed in the same worktree/branch, still uncommitted, version unchanged at 0.69.2.

### 1. Page title font — root cause found, fixed with static fonts

Measured against the mockups, `TELEGRAM`/`STORAGE`/`SYSTEM` rendered heavy and wedge-serifed. `fraunces.ttf`'s own `fvar` table (`fonttools varLib.instancer`, checked directly) registers `wght` default **900** and `opsz` default **9** — its heaviest, most decorative display cut. That is exactly the look in the screenshots, which means the requested `variationSettings` (`wght 500`, `opsz 112`/`72`) were not reaching the renderer at all — the font fell back to its own raw default instance.

Isolated the trigger: `grep`-ing the whole app for `TextAutoSize`/`autoSize` turns up exactly one consumer — `PageHead.kt`, which is also the only place `pageTitleFont()`'s `FontFamily` was used. `Display`/`Read`/`Interface` (all the same `variationSettings`-on-`Font()` pattern, same `fraunces.ttf`/`newsreader.ttf`/`geist.ttf` resources) are drawn through plain `Text`, never `TextAutoSize`, and render correctly in the same screenshots (the sidebar's own "mediagram" wordmark, Fraunces `Display` at opsz 28, is fine). `TextAutoSize.StepBased` re-measures the same `BasicText` at several candidate sizes in one pass; on this device that re-measurement is what lost the axis values. I could not fully trace this into decompiled Compose-UI-text internals in the time available (traced the public API surface — `ResourceFont.resId`/`.variationSettings` — via `javap` on the resolved `ui-text-android:1.11.4` classes to build the regression test below, but not the `TextAutoSize` re-measurement path itself), so the mechanism is isolated by elimination (the one code path affected is the one consumer of a feature — autosize re-measurement of a variable font — the other three never exercise), not fully proven by source reading.

Fix: shipped [PageTitle]/[PageTitleCompact] as **static, pre-instanced fonts**, which have no axis left to drop regardless of the mechanism:
- `fonttools varLib.instancer fraunces.ttf wght=500 opsz=112 -o fraunces_page_title.ttf` (36,052 bytes) and `wght=500 opsz=72 -o fraunces_page_title_compact.ttf` (36,272 bytes), each with its `fvar` table dropped (`fonttools` confirms "Dropping fvar table") and its own distinct family name (`Fraunces PageTitle`/`Fraunces PageTitleCompact`, set via `fontTools.ttLib` name-table edits, so the two new resources and the original variable `fraunces.ttf` can never be confused by name).
- `Type.kt`: removed `pageTitleFont()`; `PageTitle`/`PageTitleCompact` now build from `Font(resId = R.font.fraunces_page_title/_compact, weight = FontWeight.Medium)` — no `variationSettings` at all.
- `Display`/`Read`/`Interface` **unchanged** — confirmed by the new test they never showed the bug (no `TextAutoSize` consumer) and are not worth the APK cost of a static cut.
- `PageHead.kt`: now picks `PageTitle` vs `PageTitleCompact` by `maxTitleSize` (≥90sp → wide) instead of always drawing through `PageTitle`'s baked-in opsz-112 family scaled down — a latent second bug (compact callers were never actually getting the 72-cut) that would have stayed invisible once the static fonts made the two families genuinely different.
- Added `core/designsystem/src/test/kotlin/PageTitleFontTest.kt` (4 tests, plain JVM, no Robolectric needed): asserts `PageTitle`/`PageTitleCompact` resolve to their own `R.font.fraunces_page_title{,_compact}` resource with **empty** `variationSettings`, that the two are distinct font resources, and that `Display`/`Read`/`Interface` still carry a **non-empty** `variationSettings` request (regression guard the other direction — if someone "fixes" those too under a misreading of this note, the test catches the needless static conversion, not just the original bug).
- APK cost: +72,324 bytes raw (two new font files), ≈+44KB deflate-compressed (measured directly, not from an `assembleDebug` build — see Tests Status below) — modest, in the same range as phase 01's `geist.ttf` addition.

### 2. Eyebrow position — fixed

`PageHead.kt` drew the eyebrow above the title; the web and both mockups' own `<header>` order is title (`.dept-title`/`h1`) then eyebrow (`.eyebrow`/`p`) beneath it. Swapped the `Column`'s children and updated its own KDoc; also corrected the gap between them from `Spacing.small` (8dp) to `Spacing.medium` (16dp), matching the mockup's `.title{margin:0 0 16px}` exactly (it had been wrongly copied from a different gap in the original build).

### 3. Index row names — fixed

`SettingsIndexRow`'s label used `MaterialTheme.typography.titleSmall` (Fraunces/`Display`). The mockup's own CSS (`round2/tokens.css:73`, `.row b`) sets no `font-family` of its own, so it inherits `body`'s Geist, not the wordmark's serif — `DESIGN.md`'s "Settings index row" component entry saying "Fraunces Medium 16sp" is stale against the round-2 CSS (flagging for phase 05's docs pass, not fixing there — out of this phase's file ownership). Changed to `MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)` (Geist 15sp/500, matching `.row b`'s `font-size:15px; font-weight:500` exactly). Checked the "mediagram" wordmark's own size against the mockup's `.brand{font:600 27px}` — matches (`titleLarge` 27sp/SemiBold) — no change made there.

### 4. Left pane width — fixed defensively

Measured all four screenshots by pixel (the sidebar's own background colour, `#09090A`, transitions to the page's `#0D0D0E` at a consistent x/width fraction on all four): **440dp**, not the coded 320dp — `SettingsIndexWidth` was already `320.dp` and the modifier chain reads correctly by inspection, so I could not fully isolate why a `Modifier.width(320.dp)` child inside a `Row` measured wider on-device without one in hand to instrument; the systematic, identical-across-all-four-screens overshoot is consistent with a device-side inset (the Redmi Pad Pro's own navigation chrome in this orientation) being let through as extra measured width rather than pure inner padding. Rather than leave that unresolved, changed the index pane's sizing from `Modifier.width` to `Modifier.requiredWidth(SettingsIndexWidth)`, which — unlike `width` — cannot be exceeded regardless of what a descendant (a `windowInsetsPadding` call included) asks for; content that does not fit would clip rather than push the pane, and the page pane beside it (`Row.weight(1f)`) is guaranteed the rest of the 1600dp regardless. Added `SettingsPanesTest.theIndexPaneNeverGrowsPastItsOwn320dpEvenWithAStartInset`, which fetches the "Telegram" row's own semantics bounds and asserts its right edge is at or under 320dp — this passes today (Robolectric reports no inset), so it is a regression guard for the logic, not proof the on-device cause is fully understood; flagging for a real-device re-check once this build reaches one.

### 5. System ledgers — fixed

`Block`'s row used `Arrangement.SpaceBetween` with two natural-width `Text`s — with a long value ("3341 playable sets, 2136 posters") that collided directly against the label with no gap and wrapped under it rather than staying in its own column. Changed to the mockup's own `dt{flex:none}`/`dd{min-width:0}` shape: `Arrangement.spacedBy(16.dp)` (a real, guaranteed gap — the label never shrinks to make room) then the value in its own `Modifier.weight(1f)` box, right-aligned (`TextAlign.End`) and free to wrap within that column alone. Fixed for all 8 `Block` callers at once (System's three-column ledgers, Storage, Telegram's Connection). Widened materially anyway once #4 freed up real width for the two side panes.

### 6. Storage fields — fixed

- Field text (placeholder and typed value) was Material3's own default `bodyLarge` — Newsreader, this catalogue's one deliberate serif exception — for an interface control the mockup sets in Geist (`.input` inherits `body`'s `--text`). Added `textStyle = MaterialTheme.typography.bodyMedium` to `SettingsField`'s `OutlinedTextField` (Geist 15sp, matching every other interface control on the page).
- "Connected to …" carried no held/sage mark. Added `LedgerEntry.held: Boolean`, wired in `LanCacheBlock` for the Status row when `state.connection == CONNECTED` — draws the same 7dp sage dot + sage value colour a session row's "This device" already had, through the same `Block` row change from #5.

### 7. Everything else compared

Re-checked spacing scale, section-head weight/size, tally, toggle, accent ring, and chip styling against all four mockups once the font/order/width bugs above were fixed — nothing else stood out as cheap-and-wrong. Left as previously reported: chip height (48dp, matches round-2 CSS over `DESIGN.md`'s older 36dp guess), swatch gradients (flat `Palette` fills, not the mockup's decorative CSS gradients), Toggle (Material3 `Switch` + colour overrides, not a hand-drawn control) — all already flagged as deliberate in the original report above.

### Files touched this round
`core/designsystem/src/main/kotlin/{Type,PageHead}.kt`; `core/designsystem/src/main/res/font/fraunces_page_title{,_compact}.ttf` (new); `core/designsystem/src/test/kotlin/PageTitleFontTest.kt` (new, 4 tests); `ui-mobile/src/main/kotlin/ui/settings/{SettingsIndex,SettingsPanes,LanCacheBlock}.kt`; `ui-mobile/src/main/kotlin/ui/components/Block.kt`; `ui-mobile/src/test/kotlin/ui/settings/SettingsPanesTest.kt` (+1 test). No version bump (stays 0.69.2, same unreleased commit, per instruction).

### Tests Status (re-verify)
- Full verify (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin`): **BUILD SUCCESSFUL**, lint clean.
- Unit tests: 1521 → **1526** (+5: `PageTitleFontTest` ×4, `SettingsPanesTest` ×1). No test removed, no other test needed adjusting.
- APK size not independently re-measured via `assembleDebug` this round (font-file byte counts and deflate estimates above are direct filesystem measurements, not an installed-APK diff); flagged for the next real device/build pass.

### Unresolved / needs a real device
- Item 1's exact `TextAutoSize` + variable-font mechanism, and item 4's exact device-inset mechanism, are each isolated by elimination and fixed defensively (static fonts; `requiredWidth`) rather than fully proven from source. Both fixes are verified by new unit tests and are correct regardless of the precise mechanism, but neither was confirmed by re-running on the Redmi Pad Pro itself (no device access this session) — worth one more tablet screenshot pass before this ships.

**Status:** DONE
