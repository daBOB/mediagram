# Phase 05 implementation report: TV control card

Branch `worktree-agent-a2d8034793c1fcd0c`, fast-forwarded to main a0f5e5c5 first.

## Commits
1. 08136d78 skip 15 s, Back puts statistics away first
2. e9d92f07 controls keep their size under focus (`LocalFlatControls`)
3. dda2e04e episode sidebar
4. 6690e3a0 card menus replace the settings panel
5. 58410725 one card, marks and Notes along the top (also holds the lead's sidebar ruling, see below)

## RED / GREEN
- T1: reverted main sources, test compile failed (`HideStats`, `statsShown` unresolved); then green.
- T3: compile failed (`TvEpisodeSidebarTag`, `NOW_PLAYING`); green.
- T4: compile failed (`TvCardMenuTag`); green.
- T5: compile failed (`TvCardWidth`); green after implementation + two fixes below.
- Final: `:ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint :ui-mobile:compileDebugKotlin` all pass. 173 `ui.tv.player` tests (172 before the sidebar-clearance test).

## Lead ruling: card clears the sidebar
With the list open the card is laid out left of it: start padding 32 dp, end padding 360 + 32 dp, centred, max 760, rows wrap (FlowRow), targets do not shrink. Closing restores the normal bounds. Test `theCardStandsClearOfTheListAndComesBackWhenItCloses` (TvPlayerEpisodesTest) asserts card.right <= list.left and bounds equal to the closed ones afterwards. Implemented in the card commit (sidebar commit came earlier): `TvPlayerExtras.sidebarOpen`, `TvControlsView.sidebarOpen`.

## Deviations (main differed from the plan text)
- Main still had the settings panel, `TvRunSteps`, `speed/hasSubtitles/subtitlesOn` extras, `onPlayNext` action; adapted edits rather than the plan's line numbers. `TvRunSteps` and `TvPlayerRun.kt` deleted in T5; remote Previous/Next now `viewModel::previous` / `playNext`.
- `TvTransport` descriptions "Skip back/forward 10 seconds" were already "Back/Forward N seconds" on main; T1 renamed from "Skip ..." forms.
- Up-next card bottom gap: dropped the extra `+ Spacing.small` (`TvUpNextOverStage`). The new card sits ~10 dp higher than the old band, leaving the cue-room test 8 dp short between the title bar (bottom 96) and the up-next card (152). Existing test `whileTheUpNextCardShowsTheCueLiftsClearAboveIt` unchanged and passes. Measured: top band bottom 96, up-next 152..253, card 277..508 (before the fix).
- Plan values kept: card 760 dp, 32 dp inset, menus 300 dp wide/320 dp max.
- Files over ~200 lines: `TvPlayerScreen.kt` 245, `TvPlayerControls.kt` 236 (plan estimated 195 for the screen; main's screen carries more). Not split to keep the diff to the plan.

## Deliberate TV differences for docs
- Sidebar opens with the remote on "Now playing"; it cannot leave except via x, Back, or picking a row.
- Back order: menu, episode sidebar, up-next card, notes, statistics, card, leave.
- Menus open on the chosen value; Back returns to the opening tool. Subtitle style menu stays open while used.
- Controls hold their size under focus (no 1.08 grow) over the stage; profile screens and the two mark dialogs keep it.
- Top-bar marks and Notes are focusable on TV, reached Up from the seek bar.
- Card narrows to the room left of the open sidebar (lead ruling).

## Concerns
- Robolectric only; no box walk (phase 06). The `focusGroup` + `onExit` fences on menu and sidebar are direct stage children per the focus gotchas, no `focusRestorer` anywhere.
- No docs, version or changelog touched.

## Fix round 1

Commits (ui-tv/player only; all test-first):
- 943d1f41 H1: landing is one-shot (`onLanded` resets it to play/pause and the opener to play/pause); opener gone falls back to play/pause (`requestFocus() || ...`); a title change closes the open menu and resets the landing. Tests `TvPlayerSwitchFocusTest` (Speed menu -> Back -> next; Audio with two tracks -> Back -> next to a one-track title, then Centre toggles play; open menu closed by a switch). RED confirmed with main sources reverted (3 of 3 failed). The fixture gained `holdPrepare` / `becomeReady()` / `reportTracks()`: while held, `prepare()` leaves the player buffering and quiet, so the controls really drop and return.
- 62fdd385 M1: row one is `position  bar  duration · ends HH:MM` on one line (`TvSeekRow` in `TvSeekBar.kt`); separate clock line removed. Test pins order and that all three share the bar's middle line. `TvPlayerFocus` and `TvPlayerExtras` moved to their own files.
- bcd72ea5 M2: `FocusRequester.Cancel` on Left of the first and Right of the last control in the top bar, tools row and transport row. Tests `TvPlayerRowEndsTest` (five, one per row end incl. Notes and a run's Episodes). 4 of the 5 failed before.
- 48fa9890 Lows: gap between card and sidebar pinned at 32 dp (+-0.5); stats button carries state description On/Off and dims its label while off (`dimmed` on `TvOverlayButton`); watched episode rows fade their content only, so the focus border stays full strength (not covered by a test: alpha on content vs surface is not observable through semantics; box walk); stale "settings panel" / "gear" comments and the seek-bar focus doc fixed.
- dd0f62fb File size: `TvPlayerOverlayState.kt` (state + close helpers), `tvControlsActions` and `TvMarkDialogs` in the bridge. `TvPlayerScreen.kt` 196, `TvPlayerControls.kt` 153.

Gate: `:ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint :ui-mobile:compileDebugKotlin` pass; 183 `@Test` in `ui.tv.player`.

Notes
- The 32 dp gap test would fail at 0 dp (the assertion is on the difference, not on overlap).
- L5 (switch with the sidebar open into another season) left for the phase 06 walk, as the review proposed.
- Unchanged: Escape hatch for H1 is only in the focus effect; `TvControlsLanding.SeekBar` is now one-shot too.
