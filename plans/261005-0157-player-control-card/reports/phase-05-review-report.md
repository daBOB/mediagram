# Phase 05 review: TV control card

Branch `worktree-agent-a2d8034793c1fcd0c`, `a0f5e5c5...58410725` (5 commits), 45 files, +1486/−758.
Gate: `:ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint` passes. Worktree left clean;
the only untracked path is the implementer's own `reports/`, which was there before the review started.

## Verdicts

- **A, spec compliance: ❌, two items.**
  - Row 1 layout differs from the spec, and from the phone.
  - "Focus starts on ▶/❚❚" does not hold after a title switch that follows any menu or the sidebar (H1).
  - Everything else is ✅.
- **B, quality: changes needed.** One High (H1), two Medium, six Low.

## (A) Spec checklist

| Item | Result |
|---|---|
| Card 760 dp, centred, 32 dp inset, `playerCard()` (78% black, 12 dp, hairline, no blur/shadow) | ✅ pinned: left 100, bottom 508 at 960×540 |
| Row 1: position, seek, duration · ends | ❌ M1: the clock line sits *above* the bar, with ends-at in the middle |
| Row 2: CC▾, Speed, Audio (>1 track), Framing; left-aligned; wraps | ✅ |
| Row 3: ↺ ⏮ −15 ▶ +15 ⏭ ⓘ ☰; ⏮⏭☰ hidden with no run | ✅ (centred, as on the phone) |
| Focus starts on ▶/❚❚ | ✅ on open; ❌ after a switch (H1) |
| Menu opens on its current value; Back returns to the opener | ✅ |
| Back order: menu → sidebar → up-next → notes → stats → card → leave | ✅ |
| D-pad ±15, `SKIP_SECONDS = SKIP_MS / 1000` | ✅ `TvPlayerKeys.kt:195`; clamped at 0 and at the end (tests) |
| `previousInRun` deleted; MediaPrevious = previous in run via `viewModel::previous` | ✅ |
| Settings panel retired; sections reused in the menus | ✅ `TvPlayerSettingsPanel.kt` and `TvPlayerRun.kt` deleted |
| Slim top bar: title, marks, Notes | ✅ focusable; Up from the seek bar, Down back to it |
| No 1.08 grow on the stage (`LocalFlatControls`); dialogs and profile screens keep it | ✅ in code; no test pins it (box walk only) |
| Sidebar: 360 dp; enters on Now playing; ✓ + 45%; progress line; pick plays and closes | ✅ |
| No auto-hide while a menu or the sidebar is open | ✅ |
| Accessible names, exact | ✅ all 13, plus "Previous season", "Next season", "Close episodes" |
| Lead ruling: card left of the sidebar, 32 dp gap, wraps, returns on close | ✅ measured: card 32..568, list 600..960. The gap itself is not pinned (L1) |

## Findings

### High

**H1. A stale `Opener` landing puts the remote on an old tool, or on nothing, after a title switch.**

Where:
- `TvPlayerScreen.kt:119-122` sets `landing = Opener` and nothing ever resets it.
- `TvPlayerScreenEffects.kt:88` re-applies it every time `barShown` flips.
- A switch drops `barShown` (Preparing) and raises it again (Playing).

Probe results (scratch test, since removed):
- **Speed menu → Back → ⏭, with a real prepare gap.** Focus goes through `tv-player-screen` during Preparing and lands on **Speed** at Playing, not ▶.
- **Audio menu (2 tracks) → Back → ⏭.** `PlayerViewModelOpen.kt:47` `choicesController.reset()` empties `audioOptions`, so the Audio tool is gone at Playing.
  - `requestFocus()` prints "FocusRequester is not initialized" and returns false. In Compose 1.11 it does not throw: the bytecode was checked.
  - Result: **nothing is focused**, and Centre does nothing until the 4 s fade re-homes focus.
  - On the box, gotcha 5 suggests it may land on the root's first focusable instead (the top bar's My List).
- The same happens through up-next autoplay and MediaNext/MediaPrevious, and with ☰ as the opener.
- **Up-next Cancel after a menu** also lands on the old tool (Speed).

Why it is new: main had the same stale `Settings` landing, but the gear was always drawn. Audio and ☰ are conditional.

Why tests miss it: the fixture's `prepare()` reaches Playing synchronously, so `barShown` never flips. Recipe:
- capture listeners with `verify { media.addListener(capture(list)) }`;
- stub `prepare`, `play`, `playWhenReady=`, `isPlaying=false`;
- press ⏭;
- then fire `onIsPlayingChanged(true)`.

Fix:
- Make the landing one-shot. After the effect acts on `Opener`, reset it to `PlayPause` through an `onLanded` callback.
- Fall back when the opener is gone: `focus.opener.requestFocus() || focus.playPause.requestFocus()`.
- Close the open menu on a `setId` change, as `TvPlayerOverlaysReset` already does for the list dialogs. An Audio or Subtitles menu otherwise stays open across a switch, listing the new title's still-empty tracks.
- Add the probe above as a test.

### Medium

**M1. Row 1 differs from the spec and the phone, with no written reason.**
- TV (`TvPlayerControls.kt:205-217`, `224-236`): a `position · ends · duration` text line over a full-width bar.
- Spec layout and phone (`PlayerScrubber`): `12:04 ━━●── 58:30 · ends 23:41` in one row.
- Plan phase 05 copied main's old clock row. Neither the report's "Deliberate TV differences" nor the plan gives a reason.
- Fix: either make it one row (`Row(position, TvSeekBar(weight 1), "duration · ends")`) or record the reason as a deliberate difference.

**M2. Row ends are not fenced, so Left and Right leave the row by geometry** (plan: "every hop between rows is named").
Probe results at 960×540:
- Left from My List (top right) goes to **☰** in the card's bottom row.
- Right from ☰ goes to **My List**.
- Right from Framing goes to ↺.

Only Up and Down are named (`TvMarksRail.kt:101`, `TvToolGroup.kt:128-132`, `TvTransport.kt:50`). Nothing is pressed by this, but on a remote the jumps between the top and the bottom are disorienting. Robolectric widths are about 0, so the exact targets on the box will differ.

Fix: add `left`/`right = FocusRequester.Cancel` on the first and last stop of each row, or an `onExit` fence per row. Each row is its own focus target, so this does not run into gotcha 2.

### Low

- **L1. The 32 dp gap is unpinned.** `TvPlayerEpisodesTest.kt:160` asserts only `card.right <= list.left`. A mutation to `end = TvSidebarWidth` (a 0 dp gap) passed. Assert `list.left - card.right == 32.dp`.
- **L2. Stale docs.**
  - `TvSeekBar.kt:44-47`: Down is now CC, not play/pause, and Up can go to the marks or Notes.
  - `TvSubtitles.kt:56,70` and `TvUpNextCard.kt:34` still say "settings panel".
  - `TvOverlayButtons.kt:28` still says "the gear".
- **L3. ⓘ shows no state.** The phone dims ⓘ while stats are off (`CardTransportRow`, `dimmed = !statsShown`); TV `TvTransport.kt:88` does not. This is a parity gap with no reason given.
- **L4. A focused watched row is dimmed with its focus border** (`TvEpisodeRow.kt:51`: alpha applied after `focusRequester`). At 45% the accent ring is hard to see across a room. Fade the content, not the surface.
- **L5. A switch with the sidebar open into another season** (MediaPrevious, or up-next) re-keys `shown` and removes the focused row.
  - In Robolectric focus fell to "Previous season", still inside the list.
  - On the box, gotcha 5 may hand it to the root instead.
  - Add this to the phase 06 walk.
- **L6. "No grow under focus" is pinned by no test.** Accepted: the box walk is the check.

## Focus walk (Robolectric probes)

- Card auto-hides with a tool focused: focus goes to `tv-player-screen`, and Up then lands on the seek bar. ✅
- Up from row 2 goes to the seek bar, Up again to My List, and Down from there back to the seek bar. Up from My List stays put. ✅
- Style… by D-pad: focus stays inside the style menu, and Back returns it to "Subtitle options". ✅
- No `focusRestorer`. Sidebar rows are keyed by `setId`. There are no conditional modifiers on focus nodes: the requester swaps (`focusRequester(a ?: b)`) are element updates, not inserts. ✅
- Menus and the sidebar are direct stage children with their own `focusGroup` + `onExit` cancel. ✅

## Tests with teeth (each break reverted)

| Break | Result |
|---|---|
| Back hides the card before the stats | 2 fail (BackOrder, KeysTest.backPutsTheStatisticsAway…) |
| Back with a panel open hides the card | 8 fail |
| `menuOrSidebarOpen = false` | 2 fail (menu, list) |
| Landing → seek bar instead of ▶ | 8 fail |
| Speed lands on the first row, not the current one | 4 fail |
| No `requestFocus` into the menu | 7 fail |
| Card end padding ignores the sidebar | 1 fails (theCardStandsClear…) |
| Gap 32 → 0 | **survives** (L1) |
| Close lands on ▶ instead of the opener | 6 fail |

## File size (rule ~200)

- **`TvPlayerScreen.kt` (245).**
  - Move the overlay state (`statsShown`, `choosingList`, `choosingKids`, `menu`, `sidebarOpen`, `closePanel`, `closeMarkDialogs`) into `TvPlayerOverlayState.kt` with `rememberTvPlayerOverlayState()`. This mirrors the phone's `PlayerCardState.kt`, which is also a parity win.
  - Move the `TvControlsActions(...)` wiring into a builder in `TvPlayerControlsBridge.kt`.
  - Result: about 170 lines.
- **`TvPlayerControls.kt` (236).**
  - Move `TvPlayerFocus` and `openerOf` to `TvPlayerFocus.kt` (about 30 lines).
  - Move `TvPlayerExtras` to `TvPlayerControlsBridge.kt` or its own file.
  - Move `TvPlayerClock` into `TvSeekBar.kt`, where row 1 lives (and where M1's one-row fix would go).
  - Result: about 140 lines.

## Dead code and comments

- No leftovers of the settings panel, `TvRunSteps`, `previousInRun` or `TvControlsLanding.Settings`. The `onPlayNext` that remains is used by ⏭.
- No comments cite plans. The stale wording is listed under L2.

## Unresolved

- M1: keep the two-line clock as a recorded TV difference, or match the spec's one row? This is a lead/user call.
- The top bar has no ✕ Close on TV (Back leaves). It is not listed among the deliberate differences for phase 06's docs.
