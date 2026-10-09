# TV parity, batch 1: text at the web's size

2026-10-09, branch `feat/tv-parity` (worktree `mediagram-tv`), commit `2ee5f19b` `feat(tv): text at the web's size`. Requirements:
`tv-design-gap-audit-261009-report.md` gaps 1, 3, 6 and 8, with the user's decisions as binding.

**The rule, as built.**
- Text reads the web's CSS at a 960px window, each px read as one sp, and never goes under 11sp.
- The phone's M3 roles already carry those numbers. `designsystem.CatalogueType` now exposes them to `:ui-tv` as plain `TextStyle`s, one per M3 slot (`bodyMedium`, `labelLarge`…).
- The phone's `CatalogueTypography` is built from the same objects. Its values are unchanged.
- Display titles take the web's 1920px size halved (`TvTypeScale`): department 60, cover 56, spread 46, shelf head 28.8sp.
- `TvTypeScale`'s old 18sp and 34sp are gone from the library.
- The player overlay keeps them under a player-scoped name, `TvPlayerType`. See Deferred.

## Role table

The old sizes are what the TV drew at 0.119.2. "Phone role" names the `CatalogueType` slot the TV call site now uses.

| Role | Old | New | Web source |
|---|---|---|---|
| Department pill | Geist 18 + count 13 | Geist 500 **14**, no count | `.departments a` 0.875rem (shell.css:47) |
| Rail row label | Geist 18 | Geist 500 **14** (`labelLarge`) | `.rail-nav a` (shell.css:47) |
| Rail count | 13 | **11** (floor; web 10) | `.n` (shell.css:60) |
| Rail tally | 16 | **11** caps .34em (floor; web 10) | `.rail-masthead` (shell.css:66) |
| Wordmark | Fraunces 500 24 | Fraunces 600 **27** | `.brand` 1.7rem (shell.css:34) |
| Eyebrows: cover, features, This month, settings head, settings index | 16 | **11** (`Eyebrow`) | `.eyebrow` (theme.css:200) |
| Cover headline / long | 61 / 42 | **56 / 36.8** | `.cover-title` 7rem / 4.6rem, halved (home.css:50,56) |
| Cover deck | Geist 18 | **Newsreader 17.6** | `.cover-deck` (home.css:60) |
| Cover meta | 16 | **13** | `.cover-meta` (home.css:65) |
| Cover and title pills | Geist 18 | Geist 500 **15** | `.pill` (title-page.css:14) |
| Cover "Details" | 18 | **14** | `.cover-details` (home.css:79) |
| Feature deck | Geist 18 | **Newsreader 16** | `.feature-deck` (home.css:178) |
| Row heads ("Continue Watching", "Continue your series", "Popular") | Fraunces 34 | **Geist 600 20.8** | `.row-head h2` (home.css:195) |
| Row-head count | 34 | **13** regular | `.row-head .count` (home.css:196) |
| "See all" | 18 | **13** | `.see-all` (home.css:203) |
| Wall section ("Films"), Search group | Fraunces 34 / Geist 600 18 | **Fraunces 600 24** | `.shelf-sub` (catalog.css:98) |
| Shelf head; kept wall, "All films" and Stats headings | 42 / Fraunces 34 | **Fraunces 600 28.8** | `.shelf-head h1` 3.6rem, halved (catalog.css:7) |
| Shelf extent | 16 | **11** caps .24em | `.shelf-head .sub` (catalog.css:13) |
| Department title | 81.6 | **60** | `.dept-title` 7.5rem, halved (departments.css:32) |
| Franchise title | 52.8 | **40** | 5rem, halved (departments.css:108) |
| Department line | Geist 18 | **Newsreader 20** | `.dept-line` (departments.css:36) |
| Department quote / credit | Geist italic 18 / 16 | **Newsreader italic 20.8 / 11** caps | departments.css:47-48 |
| Spread title / long | 59.5 / 44 | **46 / 34** | `.spread-title` 5.75 / 4.25rem, halved (title-page.css:89,93) |
| Spread facts | 18 | **14** | `.spread-facts` (title-page.css:94) |
| Spread overview | Geist 18/1.5 | **Newsreader 17/1.6** | `.spread-overview` (title-page.css:101) |
| Spread quote | Geist italic 22.4 | **Newsreader** italic 22.4 | `.spread-quote` (title-page.css:113) |
| Title tabs | 18 | Geist 500 **15** | `.tab` (title-page.css:127) |
| Fact label / value | 16 / 18 | **11** caps / **15** | `.fact dt/dd` (title-page.css:140-141) |
| "Directed by" and the names | Geist 18 | **Newsreader 17** | `.cast-crew` (title-page.css:174) |
| Person name / role / initials | 18 / 18 / Geist 18 | **14 / 12 / Fraunces 26** | title-page.css:181-183 |
| Plate name / meta | Geist 18 / 18 | **Fraunces 500 17 / 12** | `.grid.plates .name/.meta` (catalog.css:54-55) |
| Strip plate name / meta | Fraunces 600 18 / 16 | **Fraunces 500 15 / 12** | `.strip .name` (catalog.css:41) |
| Strip and course initials | 34 | **23** (`titleLarge`) | `.thumb` 1.4rem (catalog.css:49) |
| Continue card name / sub | 18 / 16 | **14 / 12** | `.resume-name/-sub` (home.css:272-273) |
| Continue card initial | Fraunces 500 34 | Fraunces 600 **32** | `.resume-initial` (home.css:247) |
| "offline" badge | 18 | **12** | `.badge` (catalog.css:87) |
| Plate tick | 18 | **13** | `.plate-tick` (catalog.css:67) |
| Pull quote / credit | Geist italic 18 / 16 | **Newsreader italic 25.6 / 11** caps | `.pull-quote` (home.css:286-300) |
| This month: number / name / meta | Fraunces 18 / Geist 18 / 16 | **Fraunces 20.8 / Fraunces 500 15 / 12** | home.css:320-323 |
| Course tile name / meta | 18 / 16 | **Geist 600 15 / 13** | the phone's `CourseList` |
| Art tile: genre / destination / count | 24 / 28 / 16 | **21.6 / 22.4 / 12–13** | catalog.css:146-147, departments.css:104-105 |
| Page pill ("All N films") | 18 | Geist 500 **15** | `.dept-all` (departments.css:78) |
| Text row (lessons, menu, settings rows) | 18 | **15** | `.row .title b` (catalog.css:81) |
| Season heading: top / nested | Geist 600 18 | Geist 600 **18 / 15** | `.season h2` (catalog.css:71-73) |
| Search filter / search "why" | 18 / italic 18 | **14 / italic 13** | `.search-filter` (catalog.css:151) |
| Quiet line / centred message | 18 / Geist 18 | **13 / Newsreader 17** | `.badge`, `.sub`; the Android sentence role |
| Settings page title | 72 max | **60** max | `.dept-title` (settings-page.js:55) |
| Settings section head | Fraunces 34 | **`SectionHead` 16** | `.setting-title` (appearance.css:118) |
| Ledger label / value | 18 / 18 | **`LedgerLabel` 14 / `LedgerValue` 15** | Type.kt roles |
| Settings index label / status | Geist 18 / 16 | **Fraunces 16 / 12** | DESIGN.md, Settings index row |
| Appearance: accent label / artwork label / note | 18 | **12 / 14 500 / 12** | appearance.css:125-126,153 |
| Who's watching: heading / initial / name / kids | Fraunces 34 / 34 / 18 / 18 in the accent | **Geist 600 38.4 / Geist 500 32 / 14 / 12 in sage** | library-controls.css:4,7,8,10 |
| Picker notes and errors; household question | 18; Geist 18 | **14; Fraunces 21.6** | `.who-note`, `.who-ask h2` (library-controls.css:17,74) |
| PIN: heading / dots and digits | 34 / 34 | **Fraunces 19.5 / Geist 500 24** | `.pin-prompt` (library-controls.css:70-71) |
| Dialogs: title / body / buttons | 34 / 18 / 18 | **Fraunces 26 / 15 / Geist 500 14** | the phone's dialog roles |
| Setup: heading / prose / input | 34 / 18 / 18 | **26 / 15 / 16** | the phone's roles; web inputs 1rem |
| Stats: total label / total / section heads / history | 18 / 34 / 18 / 18 | **13 / Fraunces 600 28.8 / Fraunces 18 / 15** | `.stats-total` (appearance.css:156-157) |
| Menu page title | 34 | **Fraunces 26** | the phone's screen heading |
| Player overlay | 18 / 34 | **unchanged** (`TvPlayerType`) | see Deferred |

The tv-material `Typography` slots now hold the same roles (`TvTheme.kt`), so any bare component reads the web's size. The only bare `Text` was the avatar's initial, which now names its style.

## The departments bar (gap 6)

- **No counts on TV.** `TvDepartmentPill` is gone: a pill is its name, so "Movies" rather than "Movies 1143". The rail's foot still shows the totals.
- **Pill size:** Geist 500 14 at the web's 10×12 padding and 44dp minimum height.
- **⋮ folded into the avatar.** The avatar's content description is now "Menu, watching as {name}", and it opens the menu page. That page leads with **Switch profile** and a "Watching as {name}" line. The picker opens from Switch profile, and "Stay as I am" still hands the remote back to the avatar. After it come Update library, TMDB key…, Start over and Preloads, as before, so every entry ⋮ had is still reachable.
- **Focus:** the order along the bar is pills, then Search, then the avatar.
  - Right from the avatar stays put; Left goes to Search; Down goes to the page.
  - With no catalogue loaded, and on Back from the menu, focus goes to the avatar. That replaced `menuButtonFocus`.
  - Covered by `TvDepartmentsBarTest.theAvatarIsTheBarsLastStop`, plus the updated `TvMenuTest` and `TvCatalogScreenStateTest`.
- **Overflow:** at the box's 960dp the seven pills, search and the avatar fit, so nothing scrolls or fades. A row that still overflows (at 640dp in the test) scrolls. `fadeClippedEdges` masks only the end it clips (`EdgeFades` from `canScrollBackward`/`canScrollForward`), using a DstIn gradient on an offscreen layer, not a scrim, because over a cover the bar is translucent. A pill scrolled to stops `EdgeFadeWidth` (24dp) clear of the fade.
- **Regression found on the box and fixed:** the offscreen layer clipped the focused pill's grown ring at the top and bottom. The scroll alone had let that spill through. The row is now padded by the ring room on all four sides. Pinned by `theFadeLayerLeavesAFocusedPillsRingRoomAboveAndBelow`, which went red, then green.
- **Docs:** `docs/system-architecture.md` § Television differs now records no ⋮, no counts, and display type at the desktop's proportions. DESIGN.md has the Television Rule and a bar note.

## The Continue card (gap 3)

- The caption is the web's `.resume-text` block (home.css:260-273): 26/12/10dp padding, a gap of 3, and a scrim of the block's own height (0.9 → 0.6 → 0). The old full-card scrim is gone.
- Name 14sp, Medium. Sub 12sp, capped at **42%** of the caption.
- **The progress bar sits on the sub's line, at its far end, 52% of the card wide.** The two never meet. This was the "S1E2 · Der Fall Charly" bug, now fixed (composite 03).
- The "offline" badge is 12sp, on a line of its own below, never sharing the bar's line. Its box and colour belong to batch 2 (see Deferred).

## The rail (gap 8)

- It opens at **224dp**, the web's `--rail-width`, instead of 288.
- Labels are 14, counts and tally 11, and the wordmark Fraunces 600 27 on one line.
- `TvLibraryRailSizesTest` checks that no row label is ellipsized at 224dp and that the wordmark fits. The box agrees (composite 05).

## Tests, red then green

| Test | Red (before the code) | Green |
|---|---|---|
| `designsystem.TvTypeScaleTest` (rewritten: the roles are the web's 960px sizes, the 11sp floor, display = 1920 halved) | compile failure: `CatalogueType` and the display constants did not exist | 3/3 |
| `TvResumeCardTest` (new) | 3/3 failed on the old card: the sub ran 8→232dp under the bar; the badge pushed the bar off the sub's line; name 18 instead of 14 | 3/3 |
| `TvDepartmentsBarTest` (new) | 4 of the first 5 failed against the staged API: no fade, ⋮ still present, no avatar description, pill 17sp. "Fits at 960" passed trivially then. The 6th (ring vs fade layer) was added after the box check and failed first | 6/6 |
| `TvLibraryRailSizesTest` (new) | **not red-first**: written after the rail change, since the old rail no longer compiled against the new roles | 1/1 |

Existing tests whose expectations changed, and why:
- **`TvThemeTest`:** the slots now hold the catalogue roles, not 18/34.
- **`TvLibraryChromeUpTest`:** `TvDepartmentPill` is gone. The two overflow tests now use longer department names, because the real seven no longer overflow at 960.
- **`TvMenuTest`:**
  - the avatar opens the menu;
  - Switch profile is the first row and takes focus;
  - Preloads is four rows down;
  - Back lands on the avatar;
  - a new test walks Switch profile → Who's watching → Back → avatar.
- **`TvCatalogScreenStateTest`:** the avatar calls `onOpenMenu`, not `onChoose`; the empty-catalogue Back lands on the avatar.
- **`TvProfilesFlowTest` and `TvHousekeepingTest`:** reopening the picker is now the avatar, then Switch profile.
- **`TvAppTest` and androidTest `TvCatalogScreenTest`:** the avatar's new description.

Final runs, all green:
- `:ui-tv:testDebugUnitTest`: 592 tests, 0 failures.
- `:core:designsystem:testDebugUnitTest`: 22 tests, 0 failures.
- `:ui-mobile:testDebugUnitTest`: 394 tests, 0 failures. The phone's typography now builds from `CatalogueType`, with the same values.
- `:app:assembleDebug` succeeds.
- `scripts/check.sh`'s `lint` and its four `compileDebugAndroidTestKotlin` tasks pass.
- Lint has no errors. Its one new warning, `UnusedResources` on the ⋮ drawable, went with the drawable: nothing uses it any more.
- The remaining lint warnings were already there (`ModifierParameter`, `ConfigurationScreenWidthHeight`…). The new tests' `chrome()` helper repeats `ComposableNaming`, as the existing chrome test's helper does.

## On the box

- **Install:** release build, cert `5840181d…f576` verified with `apksigner`, installed with `adb install -r -d` over 0.119.2 (same versionCode). Nothing was uninstalled or cleared.
- **Navigation only:** no setting was touched, nothing was played, and Start over was not pressed. The box was left on Home.
- **An accidental page:** OK on a feature card opened The Dark Knight's title page. That was navigation only, and Back closed it. The shots are kept (composite 08).

Shots are in `scratchpad/tv-audit/batch1/`: `before-*` (0.119.2) and `after-*` (this build). The composites are in `scratchpad/tv-audit/`, each labelled with the web at 1920 on the left:

| # | File | Shows |
|---|---|---|
| 01 | `batch1-01-home-top.jpg` | cover: 56sp title, Newsreader deck, 11sp kicker, 13sp meta; the whole bar fits |
| 02 | `batch1-02-home-continue.jpg` | Continue cards, Geist 600 row head, Newsreader pull quote |
| 03 | `batch1-03-series-continue.jpg` | web, the user's photo, before and after: the bar no longer crosses "S1E2 · Der Fall …" |
| 04 | `batch1-04-series-top.jpg` | SERIES at 60sp, the line in Newsreader 20 |
| 05 | `batch1-05-rail-open.jpg` | rail at 224dp with the web's sizes |
| 06 | `batch1-06-menu.jpg` | the ⋮ page, then the avatar's menu with Switch profile |
| 07 | `batch1-07-settings.jpg` | the eyebrow on one line, 60sp title, ledger 14/15, Fraunces index |
| 08 | `batch1-08-title-page.jpg` | spread 46sp, Newsreader overview, facts 11/15, tabs 15 |
| 09 | `batch1-09-departments-bar.jpg` | the bar before (cut, then scrolled to ⋮) and after (fits, avatar last) |

## Deferred to batches 2–4

- **Batch 2 (rounded and flat):**
  - the "offline" badge loses its box and turns sage (table C); only its size changed here;
  - 3dp corners on the Continue cards and strips;
  - grey caption cards behind plates;
  - plate caption spacing of 12/4/0.
- **Batch 3 (gutters and bleed):**
  - the masthead to 83dp;
  - one left edge;
  - hero bleed;
  - wall gaps;
  - 16dp between a row head and its row;
  - title tabs 32 apart over a rule.
- **Batch 4 (focus):**
  - the accent-plus-underline on text rows, still visible on rail rows and on "Switch profile";
  - the 1.08 grow on pills and the ring-room hacks it needs;
  - the cover pills, now about 37dp tall where the web's are 48;
  - lists drawn as rows and pills;
  - the profile tiles' layout (only their type changed here).
- **The player overlay** (`TvPlayerType`, 18/34), now batch 5, "player overlay text at the web's size", after batch 4:
  - it is not in table A and has no web measurement yet;
  - the box check plays nothing, so a change there could not be seen;
  - its two dialogs (Add to list, Kids) draw in the shared `TvDialog`, so they already follow the new roles.

## Concerns

1. **Two presses to switch profile.** It is now avatar, then OK on Switch profile, where it used to be the avatar alone. That follows from folding ⋮ into the avatar, and Switch profile is the first row and takes the remote.
2. **Files already over 200 lines.** These were not split. Most did not grow: `TvHome` 361, `TvCatalogScreen` 271, `TvLibraryChrome` 253, `TvDepartmentHero` 221, `TvSetupStep` 207, `TvWall` 200. Two grew by import lines only: `TvSearchResults` 214 (+2) and `TvTitlePills` 207 (+1). The two files this batch did grow past 200 were split instead: `TvPlate` (initials → `TvPlateInitials.kt`) and `TvRecentBand` (This month → `TvThisMonth.kt`). `designsystem/Type.kt` was split into `Type.kt`, `TypeRoles.kt`, `TitleType.kt` and `TvTypeScale.kt`.
3. **Display face.** Display titles keep the TV's Fraunces opsz-28 cut (`DisplayFace`); the tablet sets them in `PageTitle`. That is a face question the audit marked "keep", so it is left alone.
4. **The kids label is now sage.** Table A lists that colour as part of the role. It is a colour, not a size.
5. **Version not bumped.** The worktree's CLAUDE.md asks for a version bump; the batch instructions said not to. 0.119.2 stays in all three manifests.
6. **Stale baseline-profile entries.** The release baseline profile still names `TvTypeScale.getBody()` and its siblings. That is harmless and refreshes at the next profile generation.
7. **Scratchpad previews.** I overwrote `scratchpad/p1.png` and `p2.png` while previewing and regenerated both from their `-full` originals at 50%.

## Batch 1b: review fixes

Commit `0cf9a990` `fix(chrome): search and the avatar stand at the bar's far end`.

**Search and the avatar now sit at the bar's far end.**
- `TvDepartmentsBar` gave the pill row `weight(1f, fill = false)`, so the toolbar followed straight after Collections. The row now fills (`weight(1f)`), as the web's `.library-toolbar { margin-left: auto }` does (shell.css:91). The fade still works: `canScrollForward` is false while the pills fit.
- On the box the avatar's right edge was 809dp at batch 1 and is 911.5dp now, against the 912dp gutter (measured in the screenshot's pixels).
- The tablet's `DepartmentsBar` had the same `fill = false` and floated the same way: two pills while loading put the toolbar at 293dp of 1280. It is fixed the same way and checked on the tablet (debug build, `ANDROID_SERIAL=caad49da`, navigate only).
- Tests (red, then green):
  - `TvDepartmentsBarTest.theAvatarEndsAtTheBarsEndGutter`: was 819, expected 912.
  - `DepartmentsBarToolbarTest.theToolbarEndsAtTheBarsGutter` (new, ui-mobile): was 293, expected 1239.

**Folded in:**
1. **The fade's pixels.** `TvEdgeFadePixelsTest` at 640dp masks white content over black and samples the red channel, the mask's alpha, at the start, the centre and the end band. The clipped end reads under 0.1 and the rest over 0.9; with nothing clipped, both ends read over 0.9.
   - It draws the ComposeView into a software bitmap rather than calling `captureToImage`. That call's redraw wait times out under this project's Robolectric, as `PageGroundColorTest` already records; the bitmap uses the same native canvas, so the offscreen layer and its DstIn really draw.
   - A mutation run (end fade disabled) failed it with `end=1.0`; the file was restored to the commit.
2. **Bring-into-view room.** It is now `EdgeFadeWidth + PillRingRoom`, so a pill scrolled to stops clear of the fade including its 1.08 grow and ring. The comment says so.
3. **The offscreen layer** is offscreen only while an end fades (`CompositingStrategy.Auto` otherwise).
4. **TvThisMonth.** The dead `FontWeight.Normal` on the This month number is removed; Fraunces here has only 500 and 600. The phone's `RecentBand` carries the same no-op and is left alone.
5. **TvLibraryRailSizesTest.** `assertEquals` now takes expected first, and `assertTrue` replaces Kotlin's `assert`.

**Player overlay text.** It stays on 18 and 34 (`TvPlayerType`) and now has an owner: "batch 5, player overlay text at the web's size", after batch 4. DESIGN.md's Television Rule says so.

**Runs (all green):**
- The focused tests.
- `:ui-tv:testDebugUnitTest`: 595 tests, 0 failures.
- `:ui-mobile:testDebugUnitTest`: 395 tests, 0 failures.
- `:app:assembleDebug`, `lint` and `:ui-tv:compileDebugAndroidTestKotlin`. Lint's warning count is unchanged, at 36.

**On the box:**
- The release build, cert `5840181d…f576`, went on with `install -r -d`, navigating only.
- Shots `batch1/after1b-home.png` and `after1b-avatar-focus.png`. Composite `tv-audit/batch1b-bar.jpg`: web, batch 1 (floating), 1b, and 1b with the avatar focused.
- Focus was handed back to the page. The box was left on Home.

**On the tablet:** `tv-audit/batch1b-tablet-bar.jpg` shows the toolbar at the gutter while loading (two pills) and once loaded. The tablet was left on Home with no playback.

**Ledgered, not now:** meta at weight 400, loose caption line heights, the `CoverTitle` naming clash, consolidating the 12px meta role, and long lines.
