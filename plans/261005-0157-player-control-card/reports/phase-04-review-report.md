# Phase 04 review: phone/tablet control card

- **Branch:** `worktree-agent-aa3dca47c4dff0d31`, 7 commits `afb823c1..bcb9c5e9`, reviewed against base `a0f5e5c5`.
- **Date:** 2026-10-05.
- **Scope:** 39 files changed, +2064 −739 lines.
- **Reviewed against:** spec.md (User decisions), plan.md (Global Constraints, Review Focus 2/3), phase-04 (including the lead's 48 dp ruling), phase-03 "Surface rules", and web phases 01/02 for parity.

## Build

`./gradlew -q :ui-mobile:testDebugUnitTest lint :app:checkDebugDuplicateClasses` is green (exit 0).

The worktree was left clean after the probes and breaks. `git status` shows only the implementer's untracked `plans/.../reports/`.

## Verdict A: spec compliance — ❌ (one spec item fails, plus two items the lead already logged)

| Item | Result |
|---|---|
| Card look: 78% black, 12 dp radius (`Radius.card`), 1 dp hairline at 8% white, no blur or shadow | ✅ `PlayerCardSurface.kt:34` |
| Card size: width −12 dp each side, cap 720, 12 dp from the bottom | ✅ `PlayerControlCard.kt:121-124`; the tablet test pins 720 |
| Row 1: position, bar, `duration · ends` | ✅ `PlayerScrubber.kt:30-57` (but see M2 below) |
| Row 2: CC ▾ Speed Audio(>1) Framing PiP, in that order | ✅ order. ❌ alignment: centred, where the spec and web put it left with PiP at the right. Already logged by the lead from the tablet walk. |
| Row 3: ↺ ⏮ −15 ▶ +15 ⏭ ⓘ ☰ | ✅ order. ⓘ ☰ are centred with the transport rather than pinned right; there is a written ruling in the phase Key insights (see L5). |
| ⏮/⏭: hidden with no run, disabled at the ends. ☰ only when `episodes != null` | ✅ `PlayerCardTransport.kt:277-293`. Tests have teeth. |
| Top bar: ←, title, marks, Notes | ✅ `PlayerTopBar.kt` |
| Sheet retired, sections reused | ✅ files deleted; `grep PlayerSettingsSheet\|PlayerControls(` is empty |
| 48 dp everywhere, row 2 wraps | ✅ for buttons and menu rows (width tests at 360, 411 and 1280). See M2 for the seek bar at large font. |
| Menus: one at a time, closed by a choice or by Back | ✅ |
| Menus: **placed above their button** | ❌ **H1**: on a landscape phone the menu is pinned to the window top and covers its own button |
| Sidebar: 320 / full width <600, opens on the current section, Now playing, 45% + ✓, progress line, pick plays and closes | ✅ But on a tablet it overlaps the card; already logged by the lead. |
| Nothing hides while a menu or the sidebar is open | ✅ tests have teeth |
| Double tap ±15 | ✅ reads `seekBack/ForwardIncrement` |
| Accessible names exactly as listed | ✅ including "Previous season", "Next season" and "Close episodes", as on the web |
| DESIGN.md records the frosted, no-blur card and why | ✅ `### Player card`, plus the deliberate-difference bullet |

## Verdict B: quality — changes needed

### High

**H1. On a landscape phone, card menus cover their own button.**

- **Where:**
  - `PlayerCardMenus.kt:44,80,149`
  - `ui-common/.../PlayerCardSurface.kt:48`
- **Cause:** `cardMenuOffset` clamps `y` to 0 but never reduces the menu's height. Its KDoc says "a long language list scrolls inside its own height instead", but nothing passes the available height into the menu.
- **Probe:** a Robolectric probe at `w800dp-h360dp` gave these bounds:

  | What | Top | Bottom |
  |---|---|---|
  | Card | 147 | 348 |
  | Speed button | 219 | 271 |
  | Speed menu | **0** | **360** |

  The menu covers ▾/Speed and the whole transport row. The top bar is composed after the menu (`PlayerScreen.kt:178`), so the title line draws over the menu's first rows.
- **Who hits it:** a 412 dp-tall phone gives the same result for Speed (about 364 dp of content), the subtitle style panel and any long language list. Landscape is the phone's main viewing posture.
- **Why tests miss it:** every screen test runs at 1280×800.
- **Fix:**
  - In `CardMenuOverStage`, compute `available = anchor.top - gap - topLimit`, where `topLimit` is the top bar's bottom or the safe top.
  - Apply `heightIn(max = min(MENU_MAX_HEIGHT, available))` so the list scrolls inside the space above its button.
  - Add a screen test at `w800dp-h360dp` asserting `menu.bottom <= button.top` and `menu.top >= topBar.bottom`.

### Medium

**M1. On a portrait phone, the top bar draws over the full-width sidebar.**

- **Where:** `PlayerScreen.kt:160` (sidebar) and `:178` (`PlayerTopChrome`).
- **Probe** at `w360dp-h800dp`: "Add to list" sits at 180–238 × 52–104 dp, on top of the sidebar header "Season 1" at 64–248 × 72–108.
- **Effect:** the card never hides while the sidebar is open, so the title and the live My List / Kids / Add to list / Notes buttons stay over the header the whole time. A tap on the header lands on a mark.
- **Inset mismatch:** the top bar insets with `systemBarsIgnoringVisibility` (`PlayerTopBar.kt:234`), while the sidebar uses visibility-aware `safeDrawing` (`EpisodeSidebar.kt:69`). With immersive mode hiding the status bar, the sidebar header moves up under ←, and ‹ (8–56 dp) shares ←'s column.
- **Fix:** either hide the title and marks while a full-width sidebar is open, or start the sidebar below the top bar. Then use the same top inset in both places.

**M2. On a narrow phone, the seek bar is squeezed by row 1's text.**

- **Where:** `PlayerScrubber.kt:30-57`.
- **Probe:** native graphics at 360 dp, with 1:02:04 / 1:58:30 · ends 23:41:

  | Font scale | Slider width |
  |---|---|
  | 1.0 | 127 dp |
  | 1.3 | 81 dp |
  | 1.5 | **24 dp** |
  | 2.0 | **24 dp** |

- **Regression:** the old bar gave the slider its own full-width line.
- **Fix:** keep the order but let `duration · ends` wrap under the bar when the row is narrow (FlowRow, or a width check). This is the spec's own "wrap rather than shrink".

**M3. One test has no teeth: the picture tap.**

- **Where:** `PlayerCardScreenTest.aTapOnThePictureClosesTheMenuNotTheCard` (`:122-130`).
- **Problem:** it presses the Speed opener a second time, so it exercises `card.toggle`, not the tap path.
- **Checked:** I removed `card.dismissMenu()` from `PlayerScreen.kt:121`, and the suite stayed green.
- **Fix:**
  - Tap a point on the picture with `performTouchInput` outside the card.
  - Advance past the double-tap timeout, since the gesture layer delays single taps.
  - Then assert that the menu is gone and the card is still there.

**M4. TalkBack falls short of the web, with no written reason.**

- **Web:** focus moves into a menu (on the current row) and into the sidebar, and returns to the opener on Esc. Openers carry `aria-expanded`; CC and Stats carry `aria-pressed`.
- **Android:**
  - `GlyphButton` and `LabelButton` set only `contentDescription` (`PlayerControlParts.kt:203,251`).
  - CC and ⓘ show on/off by alpha alone.
  - Opening a menu or the sidebar announces nothing and leaves focus on the opener.
  - Under the full-width sidebar, the hidden card is still in the traversal.
- **Fix:**
  - Give CC and ⓘ a `toggleableState` or `stateDescription`.
  - Give the openers an expanded/collapsed `stateDescription`.
  - Move accessibility focus into the menu or sidebar on open, and back to the opener on close.
  - Remove the card's controls from the traversal while a full-width sidebar covers it.
  - Or write down why the phone differs.

### Already logged by the lead (confirmed in code)

- Row 2 is centred (`PlayerCardTools.kt:179`).
- On a tablet, the sidebar overlaps the card.

### Low

- **L1. Wrong comment about rotation (`PlayerCardState.kt:19-21`).** The KDoc says "a rotation closes whatever was open". But `MainActivity` declares `orientation|screenSize|smallestScreenSize|screenLayout`, so the composition survives a rotation and the menu or sidebar stays open. `PlayerScreen`'s own KDoc says the same. The behaviour is fine, since the anchors re-report on layout; the comment is wrong. A menu kept open while turning into landscape then hits H1. Other configuration changes, such as uiMode or font scale, do recreate the activity and close it, which is acceptable.
- **L2. Back can close a hidden menu (`PlayerScreen.kt:103`).** If a menu is open when the state goes `Failed`, the card and menu are hidden by `controlsMayShow`, but `BackHandler` is still enabled. The first Back on the error screen closes an invisible menu and appears to do nothing. Fix: gate it on `barShown || card.sidebarOpen`, or clear `card.menu` when `barShown` turns false.
- **L3. `PlayerScreen.kt` is 206 lines.** That is tolerable. If the H1 or M1 fixes add lines, move the sidebar block (`:160-170`) to `PlayerScreenParts`.
- **L4. Comments still name the retired sheet.**
  - In feature/player: `FramingController.kt:44`, `PlaybackSpeed.kt:22`, `PlayerChoices.kt:17,45`, `PlayerChoicesController.kt:186`.
  - Here, as history: `PlayerCardBridge.kt:40` and `PlayerCardMenus.kt:60`.
  - No comment cites a plan, phase or task. This is a close-out sweep.
- **L5. ⓘ ☰ are centred instead of pinned right as the spec diagram shows.** The reason is written only in the phase Key insights. DESIGN.md's "then ⓘ and ☰" hides the difference. Record it there, or move them right when row 2 is fixed.
- **L6. The 48 dp ruling and its test.**
  - Without `heightIn`, the radio rows measure 52 dp; the probe covered Speed, Audio, Framing and Subtitles. The lead's modifier is redundant for them today.
  - The `Style…` row measures 36 dp without its `heightIn`, and the test fails then.
  - **Ruling:** the test is a real outcome guard; it fails whenever a sampled row drops below 48. The implementer's "passes without the modifier" is true only for the radio rows.

### Checked and found fine

- **PiP:**
  - The card, menus, sidebar, top chrome and up-next card are all gated on `isInPip`.
  - A menu left open comes back when the window returns, which is harmless.
- **Immersive mode:** the card's insets are unchanged from the old bar (`safeDrawing`, Horizontal and Bottom), so this is no regression.
- **Recomposition:**
  - The 500 ms progress read sits at the top of `PlayerControlCard`, so its body recomposes on every tick.
  - Under Kotlin 2.3 strong skipping, the tools and transport rows are skipped, because they receive the same `choices` and `actions` instances between ticks. Only `PlayerScrubber` redraws.
  - `PlayerScreen` rebuilds `PlayerCardView` and `PlayerCardActions` only when it recomposes itself. This is acceptable.
- **No focus traps:** the menus are in-tree, and Back is answered by one `BackHandler` that is enabled only while something is open.
- **Phase 03 follow-ups hold:** the episode list stays null until the catalogue has loaded and for hand-built lists (`EpisodeListFlow.kt`).

## Teeth check

Each behaviour was broken locally, then reverted.

| Break | Result |
|---|---|
| `BackHandler(enabled = false)` | ✅ `backWithAMenuOpenClosesOnlyTheMenu` and `backClosesTheMenuThenTheSidebar` FAIL |
| `closeTopmost` closes both the menu and the sidebar | ✅ `backClosesTheMenuThenTheSidebar` and `PlayerCardStateTest.backClosesTheMenuBeforeTheSidebar` FAIL |
| `menuOrSidebarOpen = false` | ✅ `anOpenMenuKeepsTheCardUpWhilePlaying` and `anOpenSidebarKeepsTheCardUpWhilePlaying` FAIL |
| ☰ always shown | ✅ `aFilmHasNoStepsAndNoEpisodes` and `PlayerControlCardTest.aFilmHasNeitherStepsNorEpisodes` FAIL |
| ⏮ always enabled | ✅ `theFirstTitleOfARunOffersNextButNotPrevious` and the matching card test FAIL |
| Every menu-row `heightIn` removed | ✅ `everyMenuRowMeetsTheTouchTargetFloor` FAILs on `Style…` at 36 dp; the radio rows stay at 52 dp |
| Picture tap ignores `dismissMenu` | ❌ `aTapOnThePictureClosesTheMenuNotTheCard` still PASSES (M3) |

## Recommended actions

1. **H1:** cap the menu height to the space above its button, and add a landscape-phone screen test.
2. **M1:** keep the top bar and the full-width sidebar from overlapping, and use the same top inset for both.
3. **M2:** let row 1 wrap when narrow, and add a check at 360 dp with font scale 1.5.
4. **M3:** rewrite the picture-tap test to tap the picture.
5. **M4:** add toggle and expanded states and move focus on open, or write down why the phone differs from the web.
6. Fix the lead's two walk findings: row 2 alignment and the card clearing the sidebar.
7. Low items: L1 and L2 now; L4 and L5 at close-out.

## Plan follow-ups

Done, pending the fix round:

- Tasks 1–7 are implemented.
- The Success criteria tests exist and pass.
- `checkDebugDuplicateClasses` is green.
- DESIGN.md is updated.

## Unresolved questions

- On the web at 390 px, does the full-width sidebar sit above or below the top bar? The answer decides M1's fix direction for parity. Web phase 02 sets the sidebar to `z-index: 3`, but the top bar's stacking was not checked; the web sidebar is not on main yet.
