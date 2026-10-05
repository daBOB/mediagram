# Phase 05: TV control card

## Context links

- [spec.md](spec.md) — binding, incl. its User decisions table (2026-10-05)
- [plan.md](plan.md) — Global Constraints, Review Focus 1–3
- [phase-03-android-shared-player-model.md](phase-03-android-shared-player-model.md) — the shared API this phase consumes verbatim
- Memory: `tv-compose-focus-gotchas` (focusRestorer, one onExit, key(id), no conditional modifiers on a focused node, a removed focused frame hands focus to the root's first focusable), `device-test-walks-must-not-change-settings`
- Today's TV player: `android/ui-tv/src/main/kotlin/ui/tv/player/*`, tests `android/ui-tv/src/test/kotlin/ui/tv/player/*`

## Overview

- **Priority:** P2, part of the control-card rollout.
- **Status:** pending.
- **Depends on:** phase 03 merged on `main`. Runs in parallel with phase 04 in its own worktree — **no shared files** (this phase touches only `android/ui-tv/src/{main,test}/kotlin/ui/tv/player/*`).
- **What:** the TV player's bottom band and 340 dp settings panel become one 760 dp card (seek / tools / transport) with small menus above their tools, an episode sidebar down the right, marks and Notes in the top bar, 15 s skips, and the Back order menu → episodes → up-next → notes → stats → card.

## Key insights

- **The key table already has the right shape.** `panelOpen`/`ClosePanel` (`TvPlayerKeys.kt:133-139`) means "something that holds the D-pad for itself is open; Back closes it first". It now means *a menu or the episode sidebar*. Only one new row: `HideStats`, between notes and the card.
- **Menus are in-tree, not `Popup`s.** A non-focusable popup window cannot take D-pad focus; a focusable one steals Back from the dispatcher. Each menu is drawn in the stage's `Box`, placed with phase 03's `cardMenuOffset`, and fenced like today's panel (`focusGroup` + `onExit = { cancelFocusChange() }`, `TvPlayerSettingsPanel.kt:70-71`).
- **Focus returns by the panel's own mechanism.** Today a closed panel lands the remote on the gear through `TvRemoteFollowsControls` re-running after the frame that removed it (`TvPlayerScreenEffects.kt:77-87`). That is exactly what the focus memory's gotcha 5 needs; the menus and the sidebar reuse it with a new landing, `TvControlsLanding.Opener`, and a plain `var opener: FocusRequester` on `TvPlayerFocus` (not a modifier, so nothing conditional sits on a focused node).
- **Every hop between the card's rows is named.** The rows are different lengths; a nearest-neighbour search from the end of one lands on whatever sits above it. Transport Up → CC; tools Up → seek bar, Down → play/pause; seek bar Down → CC, Up → the up-next card when it floats, else the first control along the top.
- **A disabled TV `Surface` stays focusable** (tv-material3; `TvOverlayButtons.kt:84-85` already relies on it for the Kids mark). CC without a regular track and ▾ without any track are drawn dimmed, still reachable and readable, and do nothing when pressed.
- **`TvChoiceRow` and `TvSettingsHeading` are shared with the profile screens** (`ui/tv/profile/TvManageDialogs.kt:22,40`, `TvManageProfiles.kt:39`). So "controls never grow on focus" cannot change `TvOverlaySurface`'s default; it becomes a `CompositionLocal` the player's stage sets.
- **`previousInRun` duplicates phase 03's `previousInQueue`.** It is deleted, and the remote's Previous goes through `viewModel.previous()` — the same switch Next takes — instead of `save()` + `onSwitch` by hand (`TvPlayerRun.kt:58-67`).
- **Sections are reused, not rewritten** (spec "What moves where"): `TvSpeedSection`, `TvAudioSection`, `TvSubtitleSection`, `TvFramingSection`, `TvSubtitleStyleSection` keep their rows; Audio/Subtitle/Framing/Style each gain only an optional `current: FocusRequester?` so the menu opens on the chosen value (Speed has one already, `TvSettingsChoices.kt:39`).

### Rulings on spec ambiguity (record; do not re-decide)

1. **Back order on TV:** menu → episode sidebar → up-next card → notes → **stats** → card → leave. The spec lists menu, sidebar, stats, card; up-next and notes keep today's places between sidebar and stats.
2. **Subtitle style menu stays open while used.** "Choosing a value closes the menu" applies to Speed, Audio, Framing and the language rows. Size, backing and sync are three settings judged together against the picture; Back closes the style menu.
3. **No grow on focus.** The spec's "controls never lift or grow" wins over the 1.08 scale in "the existing TV focus treatment"; the player keeps that treatment's border, fill and content colours. Applies to everything drawn on the stage (card, top-bar marks, menus, sidebar, up-next card); profile screens and the two mark dialogs keep the house scale.
4. **The episode list is not lazy.** Every row of a season is composed so the "Now playing" row can take the remote on arrival (a lazy row off-screen has no attached requester). Ceiling: a season of hundreds; noted in code.
5. **CC keeps its glyph state** (`CC ●` / `CC ○`) with content description "Subtitles" and a state description "On"/"Off" — the required name plus a readable state.
6. **The card's tag stays `TvBottomBandTag`** — it is still the band along the bottom other overlays keep clear of — so the cue-room and up-next band tests keep their measuring points.

### Deliberate TV differences (phase 06 copies this into `docs/system-architecture.md` → "Television differs")

- The episode sidebar opens with the remote on the "Now playing" row; the remote cannot leave it except by ✕, Back or picking a row.
- Back closes, in order: an open menu, the episode sidebar, the up-next card, the notes column, the statistics, the card; then leaves.
- Menus open with the remote on the value already chosen; Back returns it to the tool that opened the menu.
- Card controls hold their size under focus (no 1.08 grow); the profile screens keep it.
- The top bar's marks and Notes are focusable on TV (they were not), reached Up from the seek bar.

## Requirements

**Functional**
- Card: `Modifier.playerCard()`, at most 760 dp wide, centred, 32 dp off the bottom, rows seek / tools / transport.
- Row 1: position, ends-at, duration over `TvSeekBar`.
- Row 2: CC ("Subtitles", toggles, disabled without a regular track), ▾ ("Subtitle options", disabled without any track), Speed (reads `1×`…), Audio (only with >1 track), Framing (reads `Fit`…).
- Row 3: ↺ "Restart", ⏮ "Previous" (iff `upNext.inRun`, enabled = `hasPrevious`), −15 "Back 15 seconds", ▶/❚❚ "Play"/"Pause", +15 "Forward 15 seconds", ⏭ "Next" (iff `inRun`, enabled = `hasNext`), ⓘ "Stats", ☰ "Episodes" (iff `viewModel.episodes != null`).
- Menus: one at a time, above their tool, inside the card's width, card fill; focus enters on the current value; choosing closes (except style); Back closes only the menu, focus back on its tool.
- Sidebar: 360 dp, right edge, card fill, `‹ Season N ›` ("Previous season"/"Next season", absent with one section), ✕ "Close episodes", rows keyed by set id, watched at 0.45 alpha with ✓, progress line, "Now playing" on the current row (inert); pick → `playFromRun`, closes.
- Top bar: title + technical line, My List / Kids / Add to list, Notes; focusable; Down → seek bar.
- D-pad and media skip keys move 15 s, clamped to 0 and the end.
- Controls don't auto-hide while a menu or the sidebar is open.
- Retired: `TvPlayerSettingsPanel.kt`, the gear, `TvPlayerRun.kt`, `previousInRun`, `TvControlsLanding.Settings`.

**Non-functional**
- Files under ~200 lines; no new dependencies; the focus-gotcha rules above; code comments explain why and never cite plans.

## Related code files

**Modify** (all under `android/ui-tv/src/main/kotlin/ui/tv/player/`): `TvPlayerKeys.kt`, `TvPlayerRemote.kt`, `TvPlayerKeyHolder.kt`, `TvSeekBar.kt` (doc), `TvTransport.kt`, `TvToolGroup.kt`, `TvMarksRail.kt`, `TvPlayerTopBar.kt`, `TvPlayerControls.kt`, `TvPlayerControlsBridge.kt`, `TvPlayerStage.kt`, `TvPlayerScreen.kt`, `TvPlayerScreenEffects.kt`, `TvOverlayButtons.kt`, `TvSettingsChoices.kt`, `TvSubtitleStyleSection.kt`.

**Create:** `TvEpisodeSidebar.kt`, `TvEpisodeRow.kt`, `TvCardMenu.kt`; tests `TvPlayerEpisodesTest.kt`, `TvPlayerBackOrderTest.kt`.

**Delete:** `TvPlayerSettingsPanel.kt`, `TvPlayerRun.kt`.

**Tests rewritten (not deleted)** under `android/ui-tv/src/test/kotlin/ui/tv/player/`: `TvPlayerKeysTest`, `TvPlayerPanelKeysTest`, `TvPlayerNotesKeysTest`, `TvPlayerRunKeysTest`, `TvPlayerCaptionsKeyTest`, `TvPlayerFixture`, `TvPlayerHeldKeysTest`, `TvPlayerScreenHarness`, `TvPlayerScreenTest`, `TvPlayerSettingsTest`, `TvPlayerSubtitleSettingsTest`, `TvPlayerSubtitleGatesTest`, `TvPlayerSettingsRestoreTest`, `TvPlayerOverlayBandsTest`, `TvPlayerCueRoomTest`, `TvPlayerUpNextTest`, `TvPlayerRunOverlaysTest`, `TvPlayerLessonNotesTest`, `TvPlayerMarksTest`, `TvPlayerNotesTest`.

## Success criteria

- `cd android && ./gradlew -q :ui-tv:testDebugUnitTest` passes after every task's commit.
- Pinned by tests: card 760 dp × centred × 32 dp inset at 960×540; focus opens on play/pause; a menu opens above its tool on its current value; Back with a menu open closes only the menu (review 2); card and menu/sidebar stay up past `CONTROLS_LINGER_MS` (review 3); Left at 0:05 lands on 0:00 and Right at 9:50 of 10:00 lands on 10:00 (review 1); Back order menu → sidebar → stats → card; sidebar opens on "Now playing"; ⏮/⏭/☰ hidden for a film.
- `grep -rn "TvPlayerSettingsPanel\|previousInRun\|Playback settings\|Skip back\|TvControlsLanding.Settings" android/ui-tv/src` returns nothing.
- Phase 06's TV box walk (look and navigate only): card, a menu opened and closed with Back, sidebar opened and closed, focused buttons not growing.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| A menu/sidebar's `onExit` nested inside another region's (gotcha 2) | M×H | Both are direct children of the stage `Box`, which has no `focusProperties`; the notes column is a sibling, not an ancestor. |
| Focus lost when a focused menu is removed (gotcha 5) | M×H | Opener requested from `TvRemoteFollowsControls`' `LaunchedEffect`, after the removing frame — today's panel path, test-pinned on both Back and choose. |
| `requestFocus()` on an unattached requester throws | M×M | Every section lands on the selected row or its first row; ▾ is disabled when its menu would be empty; sidebar rows are all composed (ruling 4). |
| Robolectric passes, box differs (gotcha 1 history) | M×M | No `focusRestorer` anywhere; phase 06 walks the box. |
| `TvPlayerScreen.kt` over 200 lines | M×L | Final version drops `TvRunSteps` and the panel; measured at ≈195. |
| Interim layouts (tasks 3–4 run on the old band) confuse a reviewer | L×L | Each task leaves the suite green; task 5 states the final file contents in full. |

## Security

None: no new data, network, storage or permissions. Device walks only look and navigate (memory `device-test-walks-must-not-change-settings`).

---

### Task 1: Skip 15 s and Back puts the statistics away

**Files:**
- Modify `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeys.kt` (`TvKeyAction` :10-61, `SKIP_SECONDS` :67-68, `tvKeyAction` :118-175)
- Modify `TvPlayerRemote.kt` (`seekStepSeconds` doc :20-27, `apply` :157)
- Modify `TvPlayerKeyHolder.kt` (`TvPlayerBack` :53-82)
- Modify `TvTransport.kt` (doc :23-39, descriptions :64, :78)
- Modify `TvSeekBar.kt` (doc :35)
- Modify `TvPlayerScreen.kt` (`TvPlayerBack(...)` call :142-156)
- Test `android/ui-tv/src/test/kotlin/ui/tv/player/{TvPlayerKeysTest,TvPlayerPanelKeysTest,TvPlayerNotesKeysTest,TvPlayerRunKeysTest,TvPlayerHeldKeysTest,TvPlayerScreenTest,TvPlayerRunOverlaysTest,TvPlayerLessonNotesTest,TvPlayerFixture}.kt`

**Interfaces:**
- Consumes: `playback.SKIP_MS` (phase 03, `const val SKIP_MS = 15_000L`); `TvPlayerFixture.positionMs`/`durationMs` (`TvPlayerFixture.kt:69-70`); `seekBy` clamp (`TvPlayerRemote.kt:167-175`).
- Produces:
  ```kotlin
  sealed interface TvKeyAction { /* … */ data object HideStats : TvKeyAction }
  fun tvKeyAction(key: Key, controlsShowing: Boolean, focusInControls: Boolean, canControl: Boolean = true,
      panelOpen: Boolean = false, upNextShown: Boolean = false, notesOpen: Boolean = false,
      statsShown: Boolean = false): TvKeyAction
  internal fun TvPlayerBack(barShown: Boolean, onSeekBar: Boolean, settingsOpen: Boolean, upNextShown: Boolean,
      notesOpen: Boolean, statsShown: Boolean, onClosePanel: () -> Unit, onCancelUpNext: () -> Unit,
      onCloseNotes: () -> Unit, onHideStats: () -> Unit, onHideControls: () -> Unit, onLeave: () -> Unit)
  ```

- [ ] **Step 1: Write the failing tests**

  `TvPlayerKeysTest.kt` — replace every `SeekByAndShowControls(-10)`/`(10)` and `SeekBy(-10)`/`(10)` (lines 42, 47, 52, 57, 124, 129, 161, 166) with `-15`/`15`, and append before the final `}`:

  ```kotlin
      // The statistics, put away on their own before the controls go.

      @Test
      fun backPutsTheStatisticsAwayBeforeTheControls() {
          assertEquals(TvKeyAction.HideStats, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, statsShown = true))
          assertEquals(TvKeyAction.HideStats, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = true, statsShown = true))
          assertEquals(TvKeyAction.HideControls, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, statsShown = false))
      }

      /** The statistics only show with the controls; with those away, Back leaves as it always has. */
      @Test
      fun withTheControlsAwayBackStillLeaves() {
          assertEquals(TvKeyAction.Leave, tvKeyAction(Key.Back, controlsShowing = false, focusInControls = false, statsShown = true))
      }
  ```

  `TvPlayerPanelKeysTest.kt` — lines 36-37 become `SeekBy(-15)` / `SeekBy(15)`; add a `statsShown: Boolean = false` parameter to the private `panel(...)` helper and pass it (`tvKeyAction(key, controlsShowing, focusInControls, canControl, panelOpen = true, statsShown = statsShown)`); append:

  ```kotlin
      @Test
      fun backClosesThePanelBeforePuttingTheStatisticsAway() {
          assertEquals(TvKeyAction.ClosePanel, panel(Key.Back, statsShown = true))
      }
  ```

  `TvPlayerNotesKeysTest.kt` — lines 30-31 become `SeekByAndShowControls(-15)` / `(15)`; append:

  ```kotlin
      @Test
      fun backClosesTheNotesBeforePuttingTheStatisticsAway() {
          assertEquals(TvKeyAction.CloseNotes, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, notesOpen = true, statsShown = true))
      }
  ```

  `TvPlayerRunKeysTest.kt` — append:

  ```kotlin
      @Test
      fun backCancelsTheUpNextCardBeforePuttingTheStatisticsAway() {
          assertEquals(TvKeyAction.CancelUpNext, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, upNextShown = true, statsShown = true))
      }
  ```

  `TvPlayerFixture.kt` lines 81-82 become (add `import playback.SKIP_MS`):

  ```kotlin
          every { media.seekBackIncrement } returns SKIP_MS
          every { media.seekForwardIncrement } returns SKIP_MS
  ```

  `TvPlayerHeldKeysTest.kt` — replace `aHeldRightSkipsTenAtATimeThenFasterOnTheSeekBar` (lines 27-45):

  ```kotlin
      @Test
      fun aHeldRightSkipsFifteenAtATimeThenFasterOnTheSeekBar() {
          back()

          keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 0)
          assertEquals(57_000L, fixture.positionMs)
          compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()

          for (repeat in 1 until 20) keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat)
          assertEquals(342_000L, fixture.positionMs)

          keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 20)
          assertEquals(387_000L, fixture.positionMs)
          keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 21)
          assertEquals(432_000L, fixture.positionMs)

          keyUp(KeyEvent.KEYCODE_DPAD_RIGHT)
          compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
      }
  ```

  `TvPlayerScreenTest.kt` — line 83 `32_000L` → `27_000L`; line 93 `52_000L` → `57_000L`; line 102 `"Skip back 10 seconds"` → `"Back 15 seconds"`; append (review focus 1):

  ```kotlin
      /** −15 at 0:05 lands on 0:00, never before it. */
      @Test
      fun aSkipBackNearTheStartStopsAtTheStart() {
          compose.runOnUiThread { fixture.positionMs = 5_000L }
          back()

          press(Key.DirectionLeft)

          assertEquals(0L, fixture.positionMs)
      }

      /** +15 inside the last fifteen seconds lands on the end, where media3's own ended event takes over. */
      @Test
      fun aSkipForwardNearTheEndStopsAtTheEnd() {
          compose.runOnUiThread { fixture.positionMs = 590_000L }
          back()

          press(Key.DirectionRight)

          assertEquals(fixture.durationMs, fixture.positionMs)
      }
  ```

  `TvPlayerRunOverlaysTest.kt` line 95 `32_000L` → `27_000L`.

  `TvPlayerLessonNotesTest.kt` lines 70 and 77: `"Skip back 10 seconds"` → `"Back 15 seconds"`, `"Skip forward 10 seconds"` → `"Forward 15 seconds"` (both occurrences of the latter).

- [ ] **Step 2: Run, expect FAIL**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.TvPlayerKeysTest' --tests 'ui.tv.player.TvPlayerHeldKeysTest' --tests 'ui.tv.player.TvPlayerScreenTest'`
  Expected: compilation FAIL — `HideStats` and the `statsShown` argument are unresolved.

- [ ] **Step 3: Implement**

  `TvPlayerKeys.kt`: add the import `import playback.SKIP_MS`. After `CloseNotes` (line 48) add:

  ```kotlin
      /** Puts the playback statistics away, and only that: the controls stay up behind them. */
      data object HideStats : TvKeyAction
  ```

  Replace lines 67-68:

  ```kotlin
  /** One skip — the card's −15 and +15, the phone's and the web's: fifteen seconds. */
  private val SKIP_SECONDS = (SKIP_MS / 1_000).toInt()
  ```

  In the KDoc of `tvKeyAction`, after the `[notesOpen]` paragraph (ends line 108), add:

  ```kotlin
   *
   * [statsShown] is the statistics overlay, which Back puts away after the
   * notes and before the controls themselves: the numbers are the last thing
   * the viewer turned on that is not the controls.
  ```

  Signature (line 118-126) gains a last parameter `statsShown: Boolean = false,`. Line 172 becomes:

  ```kotlin
          Key.Back -> if (statsShown) TvKeyAction.HideStats else TvKeyAction.HideControls
  ```

  `TvPlayerRemote.kt` line 157 becomes:

  ```kotlin
              TvKeyAction.CancelUpNext, TvKeyAction.ClosePanel, TvKeyAction.CloseNotes, TvKeyAction.HideStats, TvKeyAction.HideControls, TvKeyAction.Leave, TvKeyAction.PassThrough, TvKeyAction.Ignore -> false
  ```

  and the `seekStepSeconds` KDoc (lines 20-27) becomes:

  ```kotlin
  /**
   * How far one press of a held Left/Right moves the film. A held key
   * repeats about twenty times a second after its first half-second, so
   * fifteen seconds a repeat already covers minutes quickly; the steps only
   * grow once the key has plainly been held on purpose, so that crossing a
   * two-hour film does not take the better part of a minute, while a tap or
   * a short hold still lands on the fifteen seconds the web and the phone skip.
   */
  ```

  `TvPlayerKeyHolder.kt` — replace `TvPlayerBack` (lines 53-82):

  ```kotlin
  /**
   * The table's Back row, answered from the dispatcher rather than as a key
   * so a Back that is not one — a gesture, the dispatcher itself — does the
   * same: the panel closes first, then the up-next card goes, then the
   * notes, then the statistics, then the controls, and only then is the
   * player left.
   */
  @Composable
  internal fun TvPlayerBack(
      barShown: Boolean,
      onSeekBar: Boolean,
      settingsOpen: Boolean,
      upNextShown: Boolean,
      notesOpen: Boolean,
      statsShown: Boolean,
      onClosePanel: () -> Unit,
      onCancelUpNext: () -> Unit,
      onCloseNotes: () -> Unit,
      onHideStats: () -> Unit,
      onHideControls: () -> Unit,
      onLeave: () -> Unit,
  ) {
      BackHandler {
          val action =
              tvKeyAction(Key.Back, barShown, onSeekBar, panelOpen = settingsOpen, upNextShown = upNextShown, notesOpen = notesOpen, statsShown = statsShown)
          when (action) {
              TvKeyAction.ClosePanel -> onClosePanel()
              TvKeyAction.CancelUpNext -> onCancelUpNext()
              TvKeyAction.CloseNotes -> onCloseNotes()
              TvKeyAction.HideStats -> onHideStats()
              TvKeyAction.HideControls -> onHideControls()
              else -> onLeave()
          }
      }
  }
  ```

  `TvPlayerScreen.kt` — in the `TvPlayerBack(...)` call (lines 142-156) add `statsShown = statsShown,` after `notesOpen = notesOpen,` and `onHideStats = { statsShown = false },` after `onCloseNotes = …,`.

  `TvTransport.kt` — line 64 description becomes `"Back ${seekBack.seekBackAmountMs / 1_000} seconds"`, line 78 `"Forward ${seekForward.seekForwardAmountMs / 1_000} seconds"`; in the KDoc line 24 "back ten, play/pause, forward ten" → "back fifteen, play/pause, forward fifteen".

  `TvSeekBar.kt` line 35: "ten seconds a press" → "fifteen seconds a press".

- [ ] **Step 4: Run, expect PASS**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.*'` — all green.

- [ ] **Step 5: Commit**

  ```bash
  git add android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeys.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerRemote.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeyHolder.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvTransport.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvSeekBar.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerKeysTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerPanelKeysTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerNotesKeysTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerRunKeysTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerHeldKeysTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerScreenTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerRunOverlaysTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerLessonNotesTest.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerFixture.kt
  git commit -m "feat(tv): skip 15 seconds and let Back put the statistics away first"
  ```

---

### Task 2: Player controls hold their size under focus

**Files:**
- Modify `android/ui-tv/src/main/kotlin/ui/tv/player/TvOverlayButtons.kt` (`TvOverlaySurface` :108-137, new local above it)
- Modify `TvPlayerStage.kt` (`TvPlayerStage` body :79-90)

**Interfaces:**
- Consumes: `TvFocus.surfaceScale()` (`ui/tv/TvFocus.kt:118-120`), `ClickableSurfaceDefaults.scale` (tv-material3).
- Produces: `internal val LocalFlatControls: ProvidableCompositionLocal<Boolean>` (default `false`); `TvOverlaySurface` reads it.

- [ ] **Step 1: No new test.** A focused control's scale is a graphics layer *inside* the node its semantics sit on, so Robolectric's `getBoundsInRoot` reads the same either way and a test would prove nothing. The existing suite must stay green (nothing else changes); the box walk in phase 06 checks that focused card buttons do not grow.
- [ ] **Step 2: Run the suite as the baseline:** `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.*'` — PASS.
- [ ] **Step 3: Implement**

  `TvOverlayButtons.kt` — add imports `androidx.compose.runtime.staticCompositionLocalOf`; insert above `TvOverlaySurface`:

  ```kotlin
  /**
   * Whether the controls drawn here hold their size under focus. The player
   * sets it: its controls never lift or grow (the web's rule, kept on every
   * surface), while the rest of this surface — and [TvChoiceRow], which the
   * profile screens share — keeps the house treatment's grow ([TvFocus]).
   */
  internal val LocalFlatControls = staticCompositionLocalOf { false }
  ```

  In `TvOverlaySurface`, replace `scale = TvFocus.surfaceScale(),` (line 132) with:

  ```kotlin
          scale = if (LocalFlatControls.current) ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f) else TvFocus.surfaceScale(),
  ```

  and in its KDoc (lines 76-89) change "so a focused control grows and takes the accent border the way every other focused thing on this surface does" to "so a focused control takes the accent border and fill every other focused thing on this surface does — and, over the player, holds its size ([LocalFlatControls])".

  `TvPlayerStage.kt` — add imports `androidx.compose.runtime.CompositionLocalProvider`; wrap lines 79-90 (from `if (shown) TvPlayerControlsForViewModel(...)` through the settings-panel block) in:

  ```kotlin
      // Everything over the picture is a control that holds still under focus.
      CompositionLocalProvider(LocalFlatControls provides true) {
          // …lines 79-90 unchanged…
      }
  ```

- [ ] **Step 4: Run, expect PASS:** `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.*'`.
- [ ] **Step 5: Commit**

  ```bash
  git add android/ui-tv/src/main/kotlin/ui/tv/player/TvOverlayButtons.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerStage.kt
  git commit -m "feat(tv): player controls keep their size when focused"
  ```

---

### Task 3: Episode sidebar

**Files:**
- Create `android/ui-tv/src/main/kotlin/ui/tv/player/TvEpisodeSidebar.kt`, `TvEpisodeRow.kt`
- Modify `TvPlayerControls.kt` (`TvPlayerFocus` :40-49, `TvPlayerExtras` :52-76)
- Modify `TvTransport.kt` (after the `hasNext` block :87-95)
- Modify `TvPlayerControlsBridge.kt` (`TvControlsView` :19-29, `TvControlsActions` :32-40, extras :64-86)
- Modify `TvPlayerStage.kt` (`TvStagePicture` :46-51, stage body, `TvUpNextOverStage` :102-134)
- Modify `TvPlayerRemote.kt` (`TvControlsLanding` :18)
- Modify `TvPlayerScreenEffects.kt` (`TvControlsAutoHide` :26-40, `TvRemoteFollowsControls` :65-88)
- Modify `TvPlayerKeyHolder.kt` (`TvPlayerBack` param `settingsOpen` → `panelOpen`)
- Modify `TvPlayerScreen.kt` (state, wiring)
- Modify test `TvPlayerScreenHarness.kt` (`toTool` :96-103 → shared `along`)
- Create test `android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerEpisodesTest.kt`

**Interfaces:**
- Consumes: `PlayerViewModel.episodes: StateFlow<EpisodeList?>`, `fun PlayerViewModel.playFromRun(setId: String)`, `EpisodeList`/`EpisodeSection`/`EpisodeRow` (phase 03); `fun controlsShouldFade(isPlaying, isScrubbing, menuOrSidebarOpen = false)` (phase 03); `Modifier.playerCard()` (phase 03, `ui.player`); `catalog.humanDuration(seconds: Int?): String?` (`feature/catalog/src/main/kotlin/TitleFacts.kt:26`); `TvOverlaySurface` (`TvOverlayButtons.kt:110`), `TvGlyphButton` (:30).
- Produces:
  ```kotlin
  internal const val TvEpisodeSidebarTag = "tv-episode-sidebar"
  internal val TvSidebarWidth: Dp // 360.dp
  internal const val NOW_PLAYING = "Now playing"
  @Composable internal fun TvEpisodeSidebar(list: EpisodeList, onPick: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)
  @Composable internal fun TvEpisodeRow(row: EpisodeRow, requester: FocusRequester, onPick: (String) -> Unit)
  internal enum class TvControlsLanding { PlayPause, SeekBar, Settings, Opener }
  // TvPlayerFocus gains: val episodes: FocusRequester; var opener: FocusRequester
  @Composable internal fun TvControlsAutoHide(controlsShown: Boolean, state: PlayerUiState, presses: Int, held: Boolean, menuOrSidebarOpen: Boolean, onHide: () -> Unit)
  @Composable internal fun TvRemoteFollowsControls(barShown: Boolean, panelOpen: Boolean, upNextShown: Boolean, landing: TvControlsLanding, root: FocusRequester, focus: TvPlayerFocus, failed: Boolean = false, notesOpen: () -> Boolean = { false }, busy: () -> Boolean = { false })
  // harness: internal fun along(target: SemanticsMatcher, key: Key = Key.DirectionRight); internal fun toTransport(target: SemanticsMatcher, key: Key = Key.DirectionRight)
  ```

- [ ] **Step 1: Write the failing test**

  `TvPlayerScreenHarness.kt` — replace `toTool` (lines 91-103) with:

  ```kotlin
      /**
       * From the controls as they open, on play/pause: Down to the row of
       * marks and tools, then Right along it until [target] holds the remote.
       */
      internal fun toTool(target: SemanticsMatcher) {
          press(Key.DirectionDown)
          along(target)
      }

      /** Along the transport from play/pause — Right, or Left for what stands before it — until [target] holds the remote. */
      internal fun toTransport(
          target: SemanticsMatcher,
          key: Key = Key.DirectionRight,
      ) = along(target, key)

      /** [key] along a row until [target] holds the remote — failing if it never does, which is a control out of the remote's reach. */
      internal fun along(
          target: SemanticsMatcher,
          key: Key = Key.DirectionRight,
      ) {
          repeat(MAX_ROW) {
              if (compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
              press(key)
          }
          compose.onNode(target).assertIsFocused()
      }
  ```

  `TvPlayerEpisodesTest.kt` (new):

  ```kotlin
  package ui.tv.player

  import androidx.compose.ui.input.key.Key
  import androidx.compose.ui.semantics.SemanticsActions
  import androidx.compose.ui.test.assertIsEnabled
  import androidx.compose.ui.test.assertIsFocused
  import androidx.compose.ui.test.assertIsNotEnabled
  import androidx.compose.ui.test.hasContentDescription
  import androidx.compose.ui.test.onAllNodesWithContentDescription
  import androidx.compose.ui.test.onNodeWithContentDescription
  import androidx.compose.ui.test.onNodeWithTag
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.test.performSemanticsAction
  import data.CatalogRepository
  import io.mockk.coEvery
  import io.mockk.mockk
  import io.mockk.verify
  import model.Kind
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import org.robolectric.annotation.Config
  import player.CONTROLS_LINGER_MS
  import testing.WatchStateFixture
  import ui.tv.catalog.set
  import kotlin.test.assertEquals

  /**
   * [THREE_TITLE_RUN] over two seasons — set-zero alone in the first, already
   * watched; the open set-one and a 45-minute set-two in the second — listed
   * in full by the catalogue, as the episode list reads it.
   */
  private fun seasonsFixture(): TvPlayerFixture {
      val sets =
          listOf(
              set("set-zero", Kind.EPISODE, "Before", show = "A Show", addedAt = 1, episode = 3, durationSecs = 600).copy(season = 1),
              set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 1, durationSecs = 600).copy(season = 2),
              set("set-two", Kind.EPISODE, "After", show = "A Show", addedAt = 1, episode = 2, durationSecs = 2_700).copy(season = 2),
          )
      val catalog = mockk<CatalogRepository>(relaxed = true)
      coEvery { catalog.sets() } returns sets
      for (one in sets) coEvery { catalog.mediaSet(one.setId) } returns one
      return TvPlayerFixture(WatchStateFixture(seed = { setWatched("set-zero", true) }), catalog = catalog)
  }

  /**
   * The episode list ☰ opens down the right of the picture: on the season
   * playing, with the remote on the row that reads "Now playing"; a season
   * switcher over it; a pick plays and closes it; and Back, a Back key and
   * the controls' fade each treat it as the thing in front.
   */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  class TvPlayerEpisodesTest : TvPlayerScreenHarness() {
      override val run = THREE_TITLE_RUN

      override fun makeFixture() = seasonsFixture()

      @Test
      fun theListOpensOnThePlayingSeasonWithTheRemoteOnNowPlaying() {
          openEpisodes()

          compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
          compose.onNodeWithText("Season 2").assertExists()
          compose.onNodeWithText(NOW_PLAYING, substring = true).assertIsFocused()
          compose.onNodeWithText("45m", substring = true).assertExists()
      }

      @Test
      fun theSwitcherShowsTheOtherSeasonWithItsFinishedTitleTicked() {
          openEpisodes()

          compose.onNodeWithContentDescription("Previous season").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()

          compose.onNodeWithText("Season 1").assertExists()
          compose.onNodeWithText("Before", substring = true).assertExists()
          compose.onNodeWithText("✓", substring = true).assertExists()
          compose.onNodeWithContentDescription("Previous season").assertIsNotEnabled()
          compose.onNodeWithContentDescription("Next season").assertIsEnabled()
      }

      @Test
      fun pickingARowPlaysItAndClosesTheList() {
          openEpisodes()

          press(Key.DirectionDown)
          compose.onNodeWithText("After", substring = true).assertIsFocused()
          press(Key.DirectionCenter)

          assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
          compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
      }

      @Test
      fun theNowPlayingRowIsNotAPick() {
          openEpisodes()

          press(Key.DirectionCenter)

          assertEquals(emptyList(), TvPlayerTestActivity.switches)
          compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
      }

      @Test
      fun leftAndRightNeitherSkipNorLeaveTheList() {
          openEpisodes()

          press(Key.DirectionLeft)
          press(Key.DirectionRight)

          assertEquals(42_000L, fixture.positionMs)
          compose.onNodeWithText(NOW_PLAYING, substring = true).assertIsFocused()
      }

      @Test
      fun backClosesTheListOntoEpisodesThenPutsTheControlsAway() {
          openEpisodes()

          back()
          compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Episodes").assertIsFocused()
          verify(exactly = 0) { fixture.media.stop() }

          back()
          compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
      }

      @Test
      fun aBackKeyClosesTheListToo() {
          openEpisodes()

          pressBackKey()

          compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Episodes").assertIsFocused()
      }

      @Test
      fun theControlsStayUpWhileTheListIsOpen() {
          openEpisodes()

          compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
          compose.waitForIdle()

          compose.onNodeWithTag(TvSeekBarTag).assertExists()
          compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
      }

      private fun openEpisodes() {
          compose.waitUntil(timeoutMillis = 5_000) {
              compose.onAllNodesWithContentDescription("Episodes").fetchSemanticsNodes().isNotEmpty()
          }
          toTransport(hasContentDescription("Episodes"))
          press(Key.DirectionCenter)
      }
  }

  /** A title opened on its own has no run, and so no list to open. */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  class TvPlayerNoRunEpisodesTest : TvPlayerScreenHarness() {
      @Test
      fun thereIsNoEpisodesButton() {
          compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
      }
  }
  ```

- [ ] **Step 2: Run, expect FAIL**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.TvPlayerEpisodesTest' --tests 'ui.tv.player.TvPlayerNoRunEpisodesTest'`
  Expected: compilation FAIL — `TvEpisodeSidebarTag`, `NOW_PLAYING` unresolved.

- [ ] **Step 3: Implement**

  `TvEpisodeSidebar.kt` (new):

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.focusGroup
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxHeight
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.key
  import androidx.compose.runtime.mutableIntStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.focus.focusProperties
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.platform.testTag
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import androidx.tv.material3.Text
  import designsystem.Overscan
  import designsystem.Palette
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import player.EpisodeList
  import ui.player.playerCard

  /** Finds the episode list in a test. */
  internal const val TvEpisodeSidebarTag = "tv-episode-sidebar"

  /** The web's 360px column: the film keeps most of the screen while a viewer picks. */
  internal val TvSidebarWidth = 360.dp

  /**
   * The run's episodes — or a course's lessons — down the right of the
   * picture, which ☰ opens and the film keeps playing behind: a season at a
   * time under `‹ Season N ›`, opening on the one playing, with the remote on
   * the row that reads "Now playing".
   *
   * The remote cannot wander out of it, as it could not out of the settings
   * panel that stood here before: Left from a row would otherwise land on the
   * controls behind it, where a press skips the film. ✕ and Back close it;
   * picking a row plays it ([onPick]).
   *
   * Not lazy: every row of the season is composed, so the playing one can take
   * the remote the moment the list opens — a lazy row off-screen has nothing
   * attached to take it. A season of hundreds would want a lazy list scrolled
   * to that row first.
   */
  @Composable
  internal fun TvEpisodeSidebar(
      list: EpisodeList,
      onPick: (String) -> Unit,
      onClose: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      var shown by remember(list.currentSection) { mutableIntStateOf(list.currentSection) }
      val section = list.sections[shown.coerceIn(list.sections.indices)]
      // One per row, kept for the list's lifetime: a row's modifier chain never changes shape.
      val requesters = remember { HashMap<String, FocusRequester>() }
      fun requesterOf(setId: String) = requesters.getOrPut(setId) { FocusRequester() }
      val close = remember { FocusRequester() }
      val playing = list.sections.getOrNull(list.currentSection)?.rows?.firstOrNull { it.current }?.setId
      LaunchedEffect(Unit) { (playing?.let(::requesterOf) ?: close).requestFocus() }

      Column(
          modifier =
              modifier
                  .fillMaxHeight()
                  .width(TvSidebarWidth)
                  .playerCard()
                  .testTag(TvEpisodeSidebarTag)
                  .focusProperties { onExit = { cancelFocusChange() } }
                  .focusGroup()
                  // Inside the overscan margin on the edge it meets; the side facing the picture needs only breathing room.
                  .padding(start = Spacing.medium, end = Overscan.horizontal, top = Overscan.vertical, bottom = Overscan.vertical),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
              if (list.sections.size > 1) {
                  TvGlyphButton(glyph = "‹", description = "Previous season", enabled = shown > 0, onClick = { shown -= 1 }, padding = Spacing.small)
              }
              Text(
                  text = section.title,
                  style = TvTypeScale.body,
                  color = Palette.Text,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
              )
              if (list.sections.size > 1) {
                  TvGlyphButton(glyph = "›", description = "Next season", enabled = shown < list.sections.lastIndex, onClick = { shown += 1 }, padding = Spacing.small)
              }
              TvGlyphButton(glyph = "✕", description = "Close episodes", enabled = true, onClick = onClose, modifier = Modifier.focusRequester(close), padding = Spacing.small)
          }
          Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
              for (row in section.rows) {
                  key(row.setId) { TvEpisodeRow(row, requesterOf(row.setId), onPick) }
              }
          }
      }
  }
  ```

  `TvEpisodeRow.kt` (new):

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxHeight
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.alpha
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import androidx.tv.material3.LocalContentColor
  import androidx.tv.material3.Text
  import catalog.humanDuration
  import designsystem.Palette
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import player.EpisodeRow

  /** What the open title's row says in place of its runtime. */
  internal const val NOW_PLAYING = "Now playing"

  /** A finished title: still there to play again, quieter than the rest. */
  private const val WATCHED_ALPHA = 0.45f

  private val ProgressHeight = 3.dp

  /**
   * One title of the run: its number, its name and its runtime — or "Now
   * playing" for the open one, which a press leaves alone. A finished one is
   * drawn faint behind a ✓; one started and left has a line along its foot
   * for how far in, the catalogue's own progress rule.
   */
  @Composable
  internal fun TvEpisodeRow(
      row: EpisodeRow,
      requester: FocusRequester,
      onPick: (String) -> Unit,
  ) {
      TvOverlaySurface(
          onClick = { if (!row.current) onPick(row.setId) },
          enabled = true,
          modifier = Modifier.fillMaxWidth().focusRequester(requester).alpha(if (row.watched) WATCHED_ALPHA else 1f),
      ) {
          Column(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small)) {
              Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                  if (row.watched) Text(text = "✓", style = TvTypeScale.body)
                  if (row.number.isNotEmpty()) Text(text = row.number, style = TvTypeScale.body)
                  Text(text = row.title, style = TvTypeScale.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                  Text(text = if (row.current) NOW_PLAYING else humanDuration(row.runtimeSecs).orEmpty(), style = TvTypeScale.body)
              }
              row.progress?.let { done ->
                  Box(modifier = Modifier.padding(top = Spacing.extraSmall).fillMaxWidth().height(ProgressHeight).background(Palette.RuleStrong)) {
                      Box(modifier = Modifier.fillMaxWidth(done).fillMaxHeight().background(LocalContentColor.current))
                  }
              }
          }
      }
  }
  ```

  `TvPlayerControls.kt` — in `TvPlayerFocus` add after `notesRegion`:

  ```kotlin
      val episodes = FocusRequester()

      /** The control a menu or the episode list was opened from: where the remote goes back to when it closes. */
      var opener: FocusRequester = playPause
  ```

  and in `TvPlayerExtras` add as the last field:

  ```kotlin
      /** Opens the episode list; null with no list to open (a film, or a title with no run), which leaves ☰ out. */
      val onOpenEpisodes: (() -> Unit)? = null,
  ```

  `TvTransport.kt` — after the `if (extras.hasNext) { … }` block add:

  ```kotlin
          extras.onOpenEpisodes?.let { open ->
              TvGlyphButton(glyph = "☰", description = "Episodes", enabled = true, onClick = open, modifier = toSeekBar.focusRequester(focus.episodes), padding = Spacing.medium)
          }
  ```

  `TvPlayerControlsBridge.kt` — `TvControlsView` gains the last field `val hasEpisodes: Boolean = false,`; `TvControlsActions` gains `val onOpenEpisodes: () -> Unit = {}, val onCloseEpisodes: () -> Unit = {}, val onPickEpisode: (String) -> Unit = {},` as its last three fields; the `TvPlayerExtras(...)` construction gains `onOpenEpisodes = actions.onOpenEpisodes.takeIf { view.hasEpisodes },`.

  `TvPlayerRemote.kt` line 18:

  ```kotlin
  internal enum class TvControlsLanding { PlayPause, SeekBar, Settings, Opener }
  ```

  `TvPlayerScreenEffects.kt` — replace `TvControlsAutoHide` (lines 15-40) and `TvRemoteFollowsControls` (lines 42-88):

  ```kotlin
  /**
   * Takes the controls away after a while of playing, by the shared rule and
   * on the shared clock; every one of [presses] starts the wait again.
   *
   * [held] keeps them up whatever the film is doing: the list and Kids
   * dialogs take their keys to their own windows, so no press here restarts
   * the fade, and the controls they return to must still be there. A menu or
   * the episode list ([menuOrSidebarOpen]) keeps them up the same way — the
   * remote is inside it, and Back from it lands on the control that opened it.
   */
  @Composable
  internal fun TvControlsAutoHide(
      controlsShown: Boolean,
      state: PlayerUiState,
      presses: Int,
      held: Boolean,
      menuOrSidebarOpen: Boolean,
      onHide: () -> Unit,
  ) {
      LaunchedEffect(controlsShown, state, presses, held, menuOrSidebarOpen) {
          if (!controlsShown) return@LaunchedEffect
          val fades = controlsShouldFade(isPlaying = state is PlayerUiState.Playing, isScrubbing = held, menuOrSidebarOpen = menuOrSidebarOpen)
          if (!fades) return@LaunchedEffect
          delay(CONTROLS_LINGER_MS)
          onHide()
      }
  }

  /**
   * Wherever the controls go, the remote goes with them: onto the control the
   * key that raised them asked for ([landing]), or back to the screen itself
   * ([root]) when they leave. While a menu, the settings panel or the episode
   * list is open ([panelOpen]) it takes the remote for itself; when it
   * closes, [landing] says where the remote goes back to — the control that
   * opened it ([TvPlayerFocus.opener]). Requested here, a frame after the
   * one that removed what held the remote, so nothing requested in that same
   * frame is lost to the root taking focus first.
   *
   * The up-next card, when it appears, takes the remote onto Play now — never
   * out from under a viewer in the middle of something, though: not from a
   * panel or a list dialog ([busy]), nor from the seek bar, where the next
   * Right of someone seeking into the last half-minute has to keep moving the
   * film. The card stays one press up from the seek bar for all of them.
   *
   * A failed title puts the remote on its Retry ([retry]), the one thing
   * left to press. With the controls away and the notes open, the remote
   * goes to the notes ([notesRegion]) rather than the screen, so Up and
   * Down page through them.
   */
  @Composable
  internal fun TvRemoteFollowsControls(
      barShown: Boolean,
      panelOpen: Boolean,
      upNextShown: Boolean,
      landing: TvControlsLanding,
      root: FocusRequester,
      focus: TvPlayerFocus,
      failed: Boolean = false,
      notesOpen: () -> Boolean = { false },
      busy: () -> Boolean = { false },
  ) {
      LaunchedEffect(barShown, panelOpen, upNextShown, failed) {
          when {
              panelOpen -> Unit
              failed -> focus.retry.requestFocus()
              !barShown -> if (notesOpen()) focus.notesRegion.requestFocus() else root.requestFocus()
              upNextShown -> if (!busy()) focus.upNext.requestFocus()
              landing == TvControlsLanding.SeekBar -> focus.seekBar.requestFocus()
              landing == TvControlsLanding.Settings -> focus.settings.requestFocus()
              landing == TvControlsLanding.Opener -> focus.opener.requestFocus()
              else -> focus.playPause.requestFocus()
          }
      }
  }
  ```

  `TvPlayerKeyHolder.kt` — in `TvPlayerBack` rename the parameter `settingsOpen` to `panelOpen` (signature and the `panelOpen = panelOpen` argument), and in its KDoc "the panel closes first" → "an open panel or the episode list closes first".

  `TvPlayerStage.kt` — add imports `androidx.compose.ui.unit.Dp`, `player.EpisodeList`. `TvStagePicture` gains `val sidebar: EpisodeList? = null,` (the open list; null while closed). Inside the `CompositionLocalProvider` block, after the settings-panel block, add:

  ```kotlin
          picture.sidebar?.let { list ->
              TvEpisodeSidebar(
                  list = list,
                  onPick = actions.onPickEpisode,
                  onClose = actions.onCloseEpisodes,
                  modifier = Modifier.align(Alignment.CenterEnd).onGloballyPositioned { bands.panelLeft = it.boundsInRoot().left },
              )
              DisposableEffect(Unit) { onDispose { bands.panelLeft = null } }
          }
  ```

  and the up-next call passes `besideWidth = when { picture.sidebar != null -> TvSidebarWidth; picture.settingsOpen -> TvSettingsPanelWidth; else -> 0.dp }`; `TvUpNextOverStage`'s `besidePanel: Boolean` parameter becomes `besideWidth: Dp` and its padding `.padding(end = besideWidth)`.

  `TvPlayerScreen.kt`:
  - add `import player.playFromRun`; collect `val episodes by viewModel.episodes.collectAsStateWithLifecycle()` after `notes`;
  - after `var settingsOpen …` add:
    ```kotlin
        var sidebarOpen by rememberSaveable { mutableStateOf(false) }
        // A switch to a title with no run takes its list away, and the sidebar with it.
        val sidebarShown = sidebarOpen && episodes != null
        val panelOpen = settingsOpen || sidebarShown
    ```
  - `TvPlayerOverlaysReset(..., closePanel = { settingsOpen = false; sidebarOpen = false })`;
  - `TvControlsAutoHide(controlsShown, state, presses, held = choosingList || choosingKids || upNextShown, menuOrSidebarOpen = panelOpen, onHide = { controlsShown = false })`;
  - `TvRemoteFollowsControls(barShown, panelOpen, upNextShown, …)`; `TvNotesFollow(…, busy = { panelOpen }, …)`;
  - `TvPlayerBack(…, panelOpen = panelOpen, …, onClosePanel = { if (settingsOpen) { landing = TvControlsLanding.Settings; settingsOpen = false } else { landing = TvControlsLanding.Opener; sidebarOpen = false } }, …)`;
  - `remote.onKey(…, panelOpen = panelOpen, …)`;
  - `TvControlsView(…, hasEpisodes = episodes != null)`;
  - `TvControlsActions(…, onOpenEpisodes = { focus.opener = focus.episodes; sidebarOpen = true }, onCloseEpisodes = { landing = TvControlsLanding.Opener; sidebarOpen = false }, onPickEpisode = { id -> landing = TvControlsLanding.PlayPause; sidebarOpen = false; viewModel.playFromRun(id) })`;
  - `TvStagePicture(subtitleCues, choices, barShown, settingsOpen, sidebar = episodes.takeIf { sidebarShown })`.

- [ ] **Step 4: Run, expect PASS**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.*'` — all green, the new file included.

- [ ] **Step 5: Commit**

  ```bash
  git add android/ui-tv/src/main/kotlin/ui/tv/player/TvEpisodeSidebar.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvEpisodeRow.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControls.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvTransport.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControlsBridge.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerStage.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerRemote.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreenEffects.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeyHolder.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerScreenHarness.kt android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerEpisodesTest.kt
  git commit -m "feat(tv): episode list down the right of the player"
  ```

---

### Task 4: Card menus replace the settings panel

The tools row (still on today's band; task 5 moves it into the card) loses the gear and gains CC ▾ Speed Audio Framing, each opening its own menu above itself.

**Files:**
- Create `android/ui-tv/src/main/kotlin/ui/tv/player/TvCardMenu.kt`
- Modify `TvToolGroup.kt` (whole file), `TvSettingsChoices.kt` (`TvAudioSection` :54-63, `TvSubtitleSection` :65-74, `TvFramingSection` :76-85, KDoc :28-34), `TvSubtitleStyleSection.kt` (:24-74)
- Modify `TvPlayerControls.kt` (`TvPlayerFocus`, `TvPlayerExtras`, `below` :171-176, bottom band :144-155, `TvToolGroup` call :179), `TvPlayerControlsBridge.kt` (:18-90), `TvPlayerStage.kt` (whole file), `TvPlayerRemote.kt` (:18), `TvPlayerScreenEffects.kt` (`Settings` branch), `TvPlayerScreen.kt` (settings → menu), `TvPlayerKeys.kt` (KDoc :90-96, :44-45)
- Delete `TvPlayerSettingsPanel.kt`
- Tests: rewrite `TvPlayerSettingsTest.kt`, `TvPlayerSubtitleSettingsTest.kt`, `TvPlayerSubtitleGatesTest.kt`, `TvPlayerSettingsRestoreTest.kt`; edit `TvPlayerScreenHarness.kt` (`openSettings` :105-109), `TvPlayerOverlayBandsTest.kt` (:57-74), `TvPlayerCueRoomTest.kt` (:60-68), `TvPlayerUpNextTest.kt` (:95-109), `TvPlayerLessonNotesTest.kt` (:65-84)

**Interfaces:**
- Consumes: `cardMenuOffset(anchor: IntRect, card: IntRect, menu: IntSize, gap: Int): IntOffset` and `Modifier.playerCard()` (phase 03, `ui.player`); `PlayerChoices` fields `speed`, `audioOptions`, `subtitleOptions`, `subtitleStyleVisible`, `ccVisible`, `subtitlesOn`, `framing`, `subtitleSizePercent`, `subtitleBacking`, `subtitleOffsetMs` (`feature/player/src/main/kotlin/PlayerChoices.kt:20-50`); VM extensions `setSpeed`, `chooseAudioTrack`, `chooseFraming`, `chooseSubtitleLanguage`, `setSubtitleSize`, `setSubtitleBacking`, `nudgeSubtitleOffset`, `resetSubtitleOffset`, `toggleSubtitles` (`PlayerViewModelDelegates.kt:258-266` as printed, i.e. lines 40-48 of that file); `TvSpeedSection` (`TvSettingsChoices.kt:35-52`), `TvChoiceRow` (:104-136).
- Produces:
  ```kotlin
  internal enum class TvCardMenu { Subtitles, SubtitleStyle, Speed, Audio, Framing }
  internal const val TvCardMenuTag = "tv-card-menu"
  @Composable internal fun BoxScope.TvCardMenuOverlay(menu: TvCardMenu, choices: PlayerChoices, viewModel: PlayerViewModel, bands: TvStageBands, onClose: () -> Unit, onSwitch: (TvCardMenu) -> Unit)
  @Composable internal fun TvCardTools(focus: TvPlayerFocus, extras: TvPlayerExtras, bands: TvStageBands, each: Modifier = Modifier)
  @Composable internal fun TvAudioSection(options: List<AudioOption>, onChosen: (AudioOption) -> Unit, current: FocusRequester? = null)
  @Composable internal fun TvSubtitleSection(options: List<SubtitleOption>, onChosen: (String) -> Unit, current: FocusRequester? = null)
  @Composable internal fun TvFramingSection(framing: Framing, onChosen: (Framing) -> Unit, current: FocusRequester? = null)
  @Composable internal fun TvSubtitleStyleSection(…unchanged…, current: FocusRequester? = null)
  // TvStageBands gains: var card: IntRect?; val openers: SnapshotStateMap<TvCardMenu, IntRect>
  // TvPlayerFocus: drops `settings`; gains cc, subtitleOptions, speed, audio, framing; fun openerOf(menu: TvCardMenu): FocusRequester
  internal class TvStagePicture(val cues: List<TimedCue>, val choices: PlayerChoices, val barShown: Boolean, val menu: TvCardMenu? = null, val sidebar: EpisodeList? = null)
  internal class TvControlsView(val marks: PlayerMarksState?, val held: Boolean, val upNext: UpNextUiState, val statsShown: Boolean, val choices: PlayerChoices, val hasEpisodes: Boolean = false)
  internal class TvControlsActions(val onToggleStats: () -> Unit, val onAddToList: () -> Unit, val onKids: () -> Unit, val onOpenMenu: (TvCardMenu) -> Unit, val onSwitchMenu: (TvCardMenu) -> Unit, val onCloseMenu: () -> Unit, val onOpenEpisodes: () -> Unit, val onCloseEpisodes: () -> Unit, val onPickEpisode: (String) -> Unit, val onToggleNotes: (() -> Unit)?, val onSeekBarFocused: (Boolean) -> Unit)
  internal enum class TvControlsLanding { PlayPause, SeekBar, Opener }
  // harness: internal fun openMenu(description: String)
  ```

- [ ] **Step 1: Write the failing tests**

  `TvPlayerScreenHarness.kt` — replace `openSettings` (lines 105-109):

  ```kotlin
      /** From the controls as they open: to the tool named [description], pressed — its menu opens above it on the current value. */
      internal fun openMenu(description: String) {
          toTool(hasContentDescription(description))
          press(Key.DirectionCenter)
      }
  ```

  `TvPlayerSettingsTest.kt` — whole file:

  ```kotlin
  package ui.tv.player

  import androidx.compose.ui.input.key.Key
  import androidx.compose.ui.semantics.Role
  import androidx.compose.ui.semantics.SemanticsActions
  import androidx.compose.ui.semantics.SemanticsProperties
  import androidx.compose.ui.test.SemanticsMatcher
  import androidx.compose.ui.test.assert
  import androidx.compose.ui.test.assertCountEquals
  import androidx.compose.ui.test.assertIsFocused
  import androidx.compose.ui.test.assertIsSelected
  import androidx.compose.ui.test.getBoundsInRoot
  import androidx.compose.ui.test.hasAnyAncestor
  import androidx.compose.ui.test.hasTestTag
  import androidx.compose.ui.test.hasText
  import androidx.compose.ui.test.onAllNodesWithText
  import androidx.compose.ui.test.onNodeWithContentDescription
  import androidx.compose.ui.test.onNodeWithTag
  import androidx.compose.ui.test.performSemanticsAction
  import io.mockk.verify
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import org.robolectric.annotation.Config
  import playback.Framing
  import player.CONTROLS_LINGER_MS
  import player.setSpeed
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  /**
   * The Speed and Framing menus on a title with one audio track and no
   * subtitles: each opens just above its tool, inside the controls' width,
   * with the remote on the value already chosen; keeps its keys to itself;
   * holds the controls up; and closes on a choice, or on Back — only itself,
   * onto the tool that opened it.
   */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  class TvPlayerSettingsTest : TvPlayerScreenHarness() {
      @Test
      fun theSpeedMenuOpensAboveItsToolInsideTheControlsOnTheCurrentSpeed() {
          openMenu("Speed")

          inMenu("1×").assertIsFocused()
          inMenu("1×").assertIsSelected()
          val menu = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot()
          val tool = compose.onNodeWithContentDescription("Speed").getBoundsInRoot()
          val card = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
          assertTrue(menu.bottom <= tool.top, "the menu ends at ${menu.bottom}, its tool starts at ${tool.top}")
          assertTrue(menu.left >= card.left && menu.right <= card.right, "the menu spans ${menu.left}..${menu.right}, the controls ${card.left}..${card.right}")
      }

      @Test
      fun aSpeedAlreadyChosenIsWhereTheMenuOpens() {
          compose.runOnUiThread { controller.get().playerViewModel.setSpeed(1.5f) }
          compose.waitForIdle()
          openMenu("Speed")
          inMenu("1.5×").assertIsFocused()
          compose.runOnUiThread { controller.get().playerViewModel.setSpeed(1f) }
      }

      @Test
      fun eachChoiceIsARadioButtonWhoseMarkIsNotReadAloud() {
          openMenu("Speed")
          inMenu("1×").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
          compose.onAllNodesWithText("●").assertCountEquals(0)
          compose.onAllNodesWithText("○").assertCountEquals(0)
      }

      @Test
      fun aTitleWithNoExtraTracksOffersSpeedAndFramingButNoAudio() {
          compose.onNodeWithContentDescription("Speed").assert(hasText("1×"))
          compose.onNodeWithContentDescription("Framing").assert(hasText("Fit"))
          compose.onNodeWithContentDescription("Audio").assertDoesNotExist()

          openMenu("Framing")
          for (framing in Framing.entries) inMenu(framing.label).assertExists()
          inMenu("Fit").assertIsFocused()
      }

      @Test
      fun leftAndRightInTheMenuNeitherSkipNorLeaveIt() {
          openMenu("Speed")
          press(Key.DirectionLeft)
          press(Key.DirectionRight)

          assertEquals(42_000L, fixture.positionMs)
          inMenu("1×").assertIsFocused()
      }

      @Test
      fun aSpeedChosenPlaysAtItAndClosesTheMenuOntoItsToolWhichReadsIt() {
          openMenu("Speed")
          press(Key.DirectionDown)
          press(Key.DirectionCenter)

          verify { fixture.media.setPlaybackSpeed(1.25f) }
          assertEquals(1.25f, controller.get().playerViewModel.choices.value.speed)
          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Speed").assertIsFocused()
          compose.onNodeWithContentDescription("Speed").assert(hasText("1.25×"))
      }

      @Test
      fun aFramingChosenIsTheSharedChoiceAndTheToolReadsIt() {
          openMenu("Framing")
          inMenu("Fill").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()

          assertEquals(Framing.FILL, controller.get().playerViewModel.choices.value.framing)
          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Framing").assert(hasText("Fill"))
      }

      /** Back with a menu open closes that menu and nothing else: the controls, the title and the player all stay. */
      @Test
      fun backClosesOnlyTheMenuOntoItsToolThenPutsTheControlsAwayThenLeaves() {
          openMenu("Speed")

          back()
          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Speed").assertIsFocused()
          compose.onNodeWithTag(TvSeekBarTag).assertExists()
          verify(exactly = 0) { fixture.media.stop() }

          back()
          compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()

          back()
          verify(exactly = 1) { fixture.media.stop() }
      }

      @Test
      fun theControlsStayUpWhileAMenuIsOpen() {
          openMenu("Speed")
          compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
          compose.waitForIdle()

          compose.onNodeWithTag(TvSeekBarTag).assertExists()
          compose.onNodeWithTag(TvCardMenuTag).assertExists()
      }

      @Test
      fun thePlayKeyStillPausesWithAMenuOpen() {
          openMenu("Speed")
          press(Key.MediaPlayPause)

          assertFalse(fixture.isPlaying)
          compose.onNodeWithTag(TvCardMenuTag).assertExists()
      }

      private fun inMenu(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TvCardMenuTag)))
  }
  ```

  `TvPlayerSubtitleSettingsTest.kt` — keep the header, class annotations, `makeFixture` and `ENGLISH_TRACK`; add imports `androidx.compose.ui.test.assertCountEquals`, `androidx.compose.ui.test.assertIsFocused`, `androidx.compose.ui.test.onAllNodesWithTag`, `designsystem.Spacing`; drop `designsystem.Overscan`; replace the KDoc and every test and helper (lines 36-151) with:

  ```kotlin
  /**
   * CC ▾ on a title with one regular English subtitle track: the languages
   * and Off, Off chosen by default — nothing is remembered or preferred for
   * this show — then "Style…", which opens the style menu in the options'
   * place. What a row chosen by hand picks is the shared choice the
   * picture's own subtitles are drawn from.
   */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  // Real text measurement: the sync row's fit is a question of how wide its words draw.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  class TvPlayerSubtitleSettingsTest : TvPlayerScreenHarness() {
      // makeFixture() unchanged

      @Test
      fun theOptionsOfferOffAndEnglishWithOffChosenThenStyle() {
          openOptions()
          for (text in listOf("Subtitles", "Off", "English", "Style…")) inMenu(text).assertExists()
          inMenu("Off").assertIsSelected()
          inMenu("Off").assertIsFocused()
          compose.onNodeWithText("Hello.").assertDoesNotExist()
      }

      @Test
      fun styleTakesTheOptionsPlaceOnTheCurrentSize() {
          openStyle()
          for (text in listOf("Subtitle style", "Small", "Normal", "Large", "Larger", "Shadow", "Box", "None", "Sync")) {
              inMenu(text).assertExists()
          }
          inMenu("Normal").assertIsSelected()
          inMenu("Normal").assertIsFocused()
          inMenu("Shadow").assertIsSelected()
          // One menu at a time: the options gave way rather than standing under it.
          compose.onAllNodesWithTag(TvCardMenuTag).assertCountEquals(1)
          inMenu("English").assertDoesNotExist()
      }

      @Test
      fun choosingEnglishClosesTheMenuTurnsCcOnAndShowsItsCue() {
          openOptions()

          inMenu("English").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()

          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithText("CC ●").assertExists()
          compose.waitUntil(timeoutMillis = 5_000) {
              compose.onAllNodesWithText("Hello.").fetchSemanticsNodes().isNotEmpty()
          }
      }

      @Test
      fun sizeAndBackingChosenAreTheSharedChoicesAndTheStyleStaysOpen() {
          openStyle()
          inMenu("Larger").performSemanticsAction(SemanticsActions.OnClick)
          inMenu("Box").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()

          val choices = controller.get().playerViewModel.choices.value
          assertEquals(135, choices.subtitleSizePercent)
          assertEquals("box", choices.subtitleBacking)
          compose.onNodeWithTag(TvCardMenuTag).assertExists()
      }

      @Test
      fun theSyncRowNudgesAndResets() {
          openStyle()
          compose.onNodeWithContentDescription("Subtitles later").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          inMenu("+0.1s").assertExists()
          assertEquals(100L, controller.get().playerViewModel.choices.value.subtitleOffsetMs)

          inMenu("Reset").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          inMenu("0.0s").assertExists()
      }

      @Test
      fun offTurnsTheSubtitlesOff() {
          openOptions()
          inMenu("English").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          compose.waitUntil(timeoutMillis = 5_000) {
              compose.onAllNodesWithText("Hello.").fetchSemanticsNodes().isNotEmpty()
          }

          compose.onNodeWithContentDescription("Subtitle options").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          inMenu("Off").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()

          val chosen = controller.get().playerViewModel.choices.value.subtitleOptions.single { it.selected }
          assertEquals(SUBTITLES_OFF, chosen.value)
          compose.onNodeWithText("Hello.").assertDoesNotExist()
      }

      @Test
      fun theSyncButtonsFitInsideTheMenuOnOneLine() {
          openStyle()
          // Bounds are clipped to what the menu's scroll shows; bring the row into it first.
          compose.onNodeWithTag(TvSyncButtonsTag).performScrollTo()
          val menu = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot()
          val row = compose.onNodeWithTag(TvSyncButtonsTag).getBoundsInRoot()
          val earlier = compose.onNodeWithContentDescription("Subtitles earlier").getBoundsInRoot()
          val reset = inMenu("Reset").getBoundsInRoot()

          assertTrue(reset.right <= menu.right - Spacing.medium + 0.5.dp, "Reset ends at ${reset.right}, the menu's margin at ${menu.right - Spacing.medium}")
          assertTrue(row.right <= menu.right - Spacing.medium + 0.5.dp, "the row ends at ${row.right}")
          // Squeezed, "Reset" would wrap onto a second line and stand taller than "−".
          assertTrue(reset.height <= earlier.height + 0.5.dp, "Reset is ${reset.height} tall, − is ${earlier.height}")
          assertTrue(reset.width > earlier.width, "Reset is ${reset.width} wide, − is ${earlier.width}")
      }

      /** Opens CC ▾ once the set's own track is known — a regular track is what puts language rows in it. */
      private fun openOptions() {
          compose.waitUntil(timeoutMillis = 5_000) {
              controller.get().playerViewModel.choices.value.subtitleOptions.isNotEmpty()
          }
          openMenu("Subtitle options")
      }

      private fun openStyle() {
          openOptions()
          inMenu("Style…").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
      }

      private fun inMenu(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TvCardMenuTag)))

      private companion object {
          val ENGLISH_TRACK = SubtitleTrackInfo(track = 0, lang = "en", forced = false, sdh = false, label = "")
      }
  }
  ```

  `TvPlayerSubtitleGatesTest.kt` — keep `fixtureWith` (lines 25-32); add imports `androidx.compose.ui.input.key.Key`, `androidx.compose.ui.test.assertIsFocused`, `androidx.compose.ui.test.assertIsNotEnabled`, `androidx.compose.ui.test.onNodeWithTag`; replace lines 34-97 with:

  ```kotlin
  private fun menuText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(TvCardMenuTag))

  /**
   * A title whose only track is forced: CC is there but dimmed — nothing
   * regular to turn on — and its ▾ opens straight onto "Style…", since size
   * and sync still apply to the forced lines.
   */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  class TvPlayerForcedOnlySubtitleGateTest : TvPlayerScreenHarness() {
      override fun makeFixture() = fixtureWith(listOf(SubtitleTrackInfo(track = 0, lang = "de", forced = true, sdh = false, label = "")))

      @Test
      fun ccIsDimmedAndItsOptionsOpenOnStyle() {
          compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.choices.value.subtitleStyleVisible }
          compose.onNodeWithContentDescription("Subtitles").assertIsNotEnabled()

          openMenu("Subtitle options")

          compose.onNode(menuText("Off")).assertDoesNotExist()
          compose.onNode(menuText("Style…")).assertIsFocused()
          press(Key.DirectionCenter)
          compose.onNode(menuText("Subtitle style")).assertExists()
          compose.onNode(menuText("Sync")).assertExists()
      }
  }

  /** A title with no subtitle track: CC and ▾ both dimmed, and ▾ opens nothing. */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  class TvPlayerNoSubtitlesGateTest : TvPlayerScreenHarness() {
      override fun makeFixture() = fixtureWith(emptyList())

      @Test
      fun ccAndItsOptionsAreDimmedAndOpenNothing() {
          // The set has loaded once its title line is up; absence before that proves nothing.
          compose.waitUntil(timeoutMillis = 5_000) {
              compose.onAllNodesWithText("A Show · S1E4 · Pilot").fetchSemanticsNodes().isNotEmpty()
          }
          compose.onNodeWithContentDescription("Subtitles").assertIsNotEnabled()
          compose.onNodeWithContentDescription("Subtitle options").assertIsNotEnabled()

          openMenu("Subtitle options")

          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
      }
  }

  /** A title with a regular track: CC starts off and one press turns it on. */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  class TvPlayerCcButtonTest : TvPlayerScreenHarness() {
      override fun makeFixture() = fixtureWith(listOf(SubtitleTrackInfo(track = 0, lang = "en", forced = false, sdh = false, label = "")))

      @Test
      fun onePressTurnsTheSubtitlesOn() {
          compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.choices.value.ccVisible }
          compose.onNodeWithText("CC ○").assertExists()

          compose.onNodeWithContentDescription("Subtitles").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()

          compose.onNodeWithText("CC ●").assertExists()
      }
  }
  ```

  (drop the now-unused `onAllNodesWithContentDescription` import.)

  `TvPlayerSettingsRestoreTest.kt` — KDoc "A settings panel saved open" → "A menu saved open"; add imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onNodeWithContentDescription`, `androidx.compose.ui.test.performSemanticsAction`; drop `Key`, `isFocused`, `performKeyInput`, `pressKey`; rename the test `aMenuRestoredWithoutAPlayerIsClosedAndBackLeaves`; replace lines 51-56 with:

  ```kotlin
          compose.onNodeWithContentDescription("Speed").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          compose.onNodeWithTag(TvCardMenuTag).assertExists()
  ```

  and line 67 `TvSettingsPanelTag` → `TvCardMenuTag`.

  `TvPlayerOverlayBandsTest.kt` — KDoc "to the left of the settings panel while that is open; and put away by a Back key as a remote sends it, as the panel is" → "to the left of the episode list while that is open; and put away by a Back key as a remote sends it, as a menu is"; add imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.performSemanticsAction`; replace lines 57-74:

  ```kotlin
      @Test
      fun withTheEpisodesOpenTheCountdownStandsBesideThem() {
          compose.onNodeWithContentDescription("Episodes").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          nearTheEnd()
          val list = compose.onNodeWithTag(TvEpisodeSidebarTag).getBoundsInRoot()
          val card = compose.onNodeWithTag(TvUpNextCardTag).getBoundsInRoot()
          assertTrue(card.right <= list.left, "card ends at ${card.right}, the list starts at ${list.left}")
          compose.onNodeWithText("When this ends").assertIsDisplayed()
      }

      /** Review focus: Back as a remote sends it — through focus first — closes the menu and only the menu. */
      @Test
      fun aBackKeyClosesAMenuOntoItsTool() {
          openMenu("Speed")
          compose.onNodeWithTag(TvCardMenuTag).assertExists()
          pressBackKey()
          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Speed").assertIsFocused()
          compose.onNodeWithTag(TvSeekBarTag).assertExists()
      }
  ```

  `TvPlayerCueRoomTest.kt` — KDoc "with the settings panel open, into what the panel leaves" → "with the episode list open, into what the list leaves"; add imports `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onNodeWithContentDescription`, `androidx.compose.ui.test.performSemanticsAction`; replace lines 60-68:

  ```kotlin
      @Test
      fun withTheEpisodesOpenTheCueStaysLeftOfThem() {
          awaitCue()
          compose.onNodeWithContentDescription("Episodes").performSemanticsAction(SemanticsActions.OnClick)
          compose.waitForIdle()
          val list = compose.onNodeWithTag(TvEpisodeSidebarTag).getBoundsInRoot()
          val cue = compose.onNodeWithText(THREE_LINES).getBoundsInRoot()
          assertTrue(cue.right <= list.left, "cue ends at ${cue.right}, the list starts at ${list.left}")
      }
  ```

  `TvPlayerUpNextTest.kt` — KDoc "except over the settings panel" → "except over a menu"; replace lines 95-109:

  ```kotlin
      @Test
      fun theCardDoesNotTakeTheRemoteFromAMenu() {
          openMenu("Speed")
          compose.onNodeWithTag(TvCardMenuTag).assertExists()

          nearTheEnd()

          compose.onNodeWithTag(TvUpNextCardTag).assertExists()
          compose.onNodeWithText("Play now").assertIsNotFocused()

          back()

          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithText("Play now").assertIsFocused()
      }
  ```

  `TvPlayerLessonNotesTest.kt` — replace lines 65-84:

  ```kotlin
      /** The narrowest the stage gets: every control still has width, inside it, and the remote reaches each. */
      @Test
      fun withTheNotesOpenEveryControlFitsBesideThemAndIsReached() {
          val notesLeft = compose.onNodeWithTag(TvNotesTag).getBoundsInRoot().left
          val controls =
              listOf("Back 15 seconds", "Pause", "Forward 15 seconds", "Subtitles", "Subtitle options", "Speed", "Framing", "Show playback statistics")
                  .map { hasContentDescription(it) } + (hasText("Notes") and hasClickAction())
          for (control in controls) {
              val bounds = compose.onNode(control).getBoundsInRoot()
              assertTrue(bounds.width > 0.dp && bounds.right <= notesLeft, "$control spans ${bounds.left}..${bounds.right}, the notes start at $notesLeft")
          }
          press(Key.DirectionRight)
          compose.onNodeWithContentDescription("Forward 15 seconds").assertIsFocused()
          press(Key.DirectionLeft)
          toTool(hasText("Notes") and hasClickAction())
          press(Key.DirectionRight)
          compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
          along(hasContentDescription("Show playback statistics"))
      }
  ```

- [ ] **Step 2: Run, expect FAIL**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.TvPlayerSettingsTest' --tests 'ui.tv.player.TvPlayerSubtitleSettingsTest' --tests 'ui.tv.player.TvPlayer*GateTest' --tests 'ui.tv.player.TvPlayerCcButtonTest'`
  Expected: compilation FAIL — `TvCardMenuTag` unresolved.

- [ ] **Step 3: Implement**

  `TvCardMenu.kt` (new):

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.focusGroup
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.BoxScope
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.heightIn
  import androidx.compose.foundation.layout.offset
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.focus.focusProperties
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.layout.onGloballyPositioned
  import androidx.compose.ui.layout.onSizeChanged
  import androidx.compose.ui.layout.positionInRoot
  import androidx.compose.ui.platform.LocalDensity
  import androidx.compose.ui.platform.testTag
  import androidx.compose.ui.unit.IntOffset
  import androidx.compose.ui.unit.IntRect
  import androidx.compose.ui.unit.IntSize
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.round
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import player.PlayerChoices
  import player.PlayerViewModel
  import player.chooseAudioTrack
  import player.chooseFraming
  import player.chooseSubtitleLanguage
  import player.nudgeSubtitleOffset
  import player.resetSubtitleOffset
  import player.setSpeed
  import player.setSubtitleBacking
  import player.setSubtitleSize
  import ui.player.cardMenuOffset
  import ui.player.playerCard

  /** The controls' menus — one open at a time, each above the tool that opens it. */
  internal enum class TvCardMenu { Subtitles, SubtitleStyle, Speed, Audio, Framing }

  /** Finds the open menu in a test. */
  internal const val TvCardMenuTag = "tv-card-menu"

  /** Room inside the padding for the subtitle style's sync row on one line, as the panel this replaced left it. */
  private val MenuWidth = 300.dp

  /** A long language list scrolls rather than running off the top of a 540dp television. */
  private val MenuMaxHeight = 320.dp

  private val MenuGap = 8.dp

  /**
   * The open [menu], just above the tool that opened it and inside the
   * controls' width ([cardMenuOffset]), in the card's own fill. The remote
   * lands on the value already chosen, as a radio group opens on its
   * selection, and cannot wander out: the controls behind are still drawn,
   * and a Left meant for the next row would otherwise land on a button that
   * skips the film. Choosing closes it ([onClose]); Back closes it without
   * choosing — the player screen answers that, from the key table.
   *
   * Subtitle style is the one menu that stays open while it is used: size,
   * backing and sync are three settings judged together against the
   * picture, not one value to pick.
   */
  @Composable
  internal fun BoxScope.TvCardMenuOverlay(
      menu: TvCardMenu,
      choices: PlayerChoices,
      viewModel: PlayerViewModel,
      bands: TvStageBands,
      onClose: () -> Unit,
      onSwitch: (TvCardMenu) -> Unit,
  ) {
      val current = remember { FocusRequester() }
      var origin by remember { mutableStateOf(IntOffset.Zero) }
      var size by remember { mutableStateOf(IntSize.Zero) }
      val gap = with(LocalDensity.current) { MenuGap.roundToPx() }
      LaunchedEffect(menu) { current.requestFocus() }
      // The stage's own corner, which the notes column can move off the root's.
      Box(modifier = Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot().round() })
      Column(
          modifier =
              Modifier
                  .align(Alignment.TopStart)
                  .offset { placed(bands.openers[menu], bands.card, origin, size, gap) }
                  .width(MenuWidth)
                  .heightIn(max = MenuMaxHeight)
                  .onSizeChanged { size = it }
                  .testTag(TvCardMenuTag)
                  .playerCard()
                  .focusProperties { onExit = { cancelFocusChange() } }
                  .focusGroup()
                  .verticalScroll(rememberScrollState())
                  .padding(Spacing.medium),
      ) {
          when (menu) {
              TvCardMenu.Speed -> TvSpeedSection(speed = choices.speed, onChosen = { viewModel.setSpeed(it); onClose() }, current = current)
              TvCardMenu.Audio -> TvAudioSection(options = choices.audioOptions, onChosen = { viewModel.chooseAudioTrack(it); onClose() }, current = current)
              TvCardMenu.Framing -> TvFramingSection(framing = choices.framing, onChosen = { viewModel.chooseFraming(it); onClose() }, current = current)
              TvCardMenu.Subtitles -> TvSubtitleMenu(choices, viewModel, current, onClose, onSwitch)
              TvCardMenu.SubtitleStyle ->
                  TvSubtitleStyleSection(
                      sizePercent = choices.subtitleSizePercent,
                      onSizeChosen = viewModel::setSubtitleSize,
                      backing = choices.subtitleBacking,
                      onBackingChosen = viewModel::setSubtitleBacking,
                      offsetMs = choices.subtitleOffsetMs,
                      onNudge = viewModel::nudgeSubtitleOffset,
                      onResetOffset = viewModel::resetSubtitleOffset,
                      current = current,
                  )
          }
      }
  }

  /**
   * CC ▾: the languages and Off, then "Style…" into the style menu — where a
   * forced-only title, with no language to pick, opens straight away.
   */
  @Composable
  private fun TvSubtitleMenu(
      choices: PlayerChoices,
      viewModel: PlayerViewModel,
      current: FocusRequester,
      onClose: () -> Unit,
      onSwitch: (TvCardMenu) -> Unit,
  ) {
      val languages = choices.subtitleOptions.isNotEmpty()
      if (languages) {
          TvSubtitleSection(options = choices.subtitleOptions, onChosen = { viewModel.chooseSubtitleLanguage(it); onClose() }, current = current)
      }
      if (choices.subtitleStyleVisible) {
          // Never omitted — a requester swapped in and out would rebuild a focused node.
          val own = remember { FocusRequester() }
          TvOverlayButton(
              text = "Style…",
              style = TvTypeScale.body,
              enabled = true,
              onClick = { onSwitch(TvCardMenu.SubtitleStyle) },
              modifier = Modifier.fillMaxWidth().focusRequester(if (languages) own else current),
              padding = Spacing.medium,
          )
      }
  }

  /** [cardMenuOffset] in the stage's coordinates; the stage's corner until the controls and the tool have been placed. */
  private fun placed(
      anchor: IntRect?,
      card: IntRect?,
      origin: IntOffset,
      size: IntSize,
      gap: Int,
  ): IntOffset = if (anchor == null || card == null) IntOffset.Zero else cardMenuOffset(anchor.translate(-origin), card.translate(-origin), size, gap)
  ```

  `TvSettingsChoices.kt` — KDoc lines 28-34: "The settings panel's radio-choice sections … whether Audio and Subtitles appear at all is the panel's decision" → "The card menus' radio-choice sections … each opens with [current] on the value already chosen — or the first, before one is". Replace `TvAudioSection`, `TvSubtitleSection`, `TvFramingSection` (lines 54-85):

  ```kotlin
  @Composable
  internal fun TvAudioSection(
      options: List<AudioOption>,
      onChosen: (AudioOption) -> Unit,
      current: FocusRequester? = null,
  ) {
      TvSettingsHeading("Audio")
      val landing = options.firstOrNull { it.selected } ?: options.firstOrNull()
      for (option in options) {
          TvChoiceRow(label = option.text, selected = option.selected, onClick = { onChosen(option) }, focusRequester = current?.takeIf { option == landing })
      }
  }

  @Composable
  internal fun TvSubtitleSection(
      options: List<SubtitleOption>,
      onChosen: (String) -> Unit,
      current: FocusRequester? = null,
  ) {
      TvSettingsHeading("Subtitles")
      val landing = options.firstOrNull { it.selected } ?: options.firstOrNull()
      for (option in options) {
          TvChoiceRow(label = option.label, selected = option.selected, onClick = { onChosen(option.value) }, focusRequester = current?.takeIf { option == landing })
      }
  }

  @Composable
  internal fun TvFramingSection(
      framing: Framing,
      onChosen: (Framing) -> Unit,
      current: FocusRequester? = null,
  ) {
      TvSettingsHeading("Framing")
      for (option in Framing.entries) {
          TvChoiceRow(label = option.label, selected = option == framing, onClick = { onChosen(option) }, focusRequester = current?.takeIf { option == framing })
      }
  }
  ```

  `TvSubtitleStyleSection.kt` — add `import androidx.compose.ui.focus.FocusRequester`; KDoc line 25 "The settings panel's" → "The subtitle style menu's"; the sync-row comment (lines 52-57) "the panel leaves about 270dp inside its margins … widening the panel" → "the menu leaves about 270dp inside its padding … widening the menu"; the signature gains `current: FocusRequester? = null,` last; the size loop (lines 46-48) becomes:

  ```kotlin
      val landing = CUE_SIZES.firstOrNull { it.percent == sizePercent } ?: CUE_SIZES.first()
      for (option in CUE_SIZES) {
          TvChoiceRow(label = option.label, selected = option.percent == sizePercent, onClick = { onSizeChosen(option.percent) }, focusRequester = current?.takeIf { option == landing })
      }
  ```

  `TvToolGroup.kt` — whole file:

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Row
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.layout.boundsInRoot
  import androidx.compose.ui.layout.onGloballyPositioned
  import androidx.compose.ui.semantics.contentDescription
  import androidx.compose.ui.semantics.semantics
  import androidx.compose.ui.semantics.stateDescription
  import androidx.compose.ui.unit.roundToIntRect
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import player.speedLabel

  /**
   * The controls that do not move the film, kept together at the end of the
   * marks row: Notes while the title has any, the card's tools
   * ([TvCardTools]), and, last, the statistics toggle, which only reports.
   *
   * One group, so that where the row has to wrap on a narrowed stage the
   * tools go to the next line together.
   */
  @Composable
  internal fun TvToolGroup(
      focus: TvPlayerFocus,
      extras: TvPlayerExtras,
      bands: TvStageBands,
  ) {
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
          extras.onToggleNotes?.let { toggle ->
              TvOverlayButton(
                  text = "Notes",
                  style = TvTypeScale.body,
                  enabled = true,
                  onClick = toggle,
                  modifier = Modifier.focusRequester(focus.notes),
                  padding = Spacing.medium,
              )
          }
          TvCardTools(focus = focus, extras = extras, bands = bands)
          TvGlyphButton(
              glyph = "ⓘ",
              description = if (extras.statsShown) "Hide playback statistics" else "Show playback statistics",
              enabled = true,
              onClick = extras.onToggleStats,
              padding = Spacing.medium,
          )
      }
  }

  /**
   * The card's tools, in the web's order: CC, which turns subtitles on or off
   * in one press, and ▾ beside it for the languages and their style; then
   * Speed, Audio — only with more than one track to choose from — and
   * Framing, each naming what is chosen and opening a short menu of the rest
   * above itself. CC with no regular track is drawn but dimmed, still there
   * to be read with nothing to turn on, and ▾ the same with no track at all.
   *
   * [each] is laid on every one of them: where the remote goes from the row.
   */
  @Composable
  internal fun TvCardTools(
      focus: TvPlayerFocus,
      extras: TvPlayerExtras,
      bands: TvStageBands,
      each: Modifier = Modifier,
  ) {
      val choices = extras.choices
      val on = choices.subtitlesOn
      TvGlyphButton(
          glyph = if (on) "CC ●" else "CC ○",
          description = "Subtitles",
          enabled = choices.ccVisible,
          onClick = extras.onToggleSubtitles,
          modifier = each.focusRequester(focus.cc).semantics { stateDescription = if (on) "On" else "Off" },
          padding = Spacing.small,
      )
      TvGlyphButton(
          glyph = "▾",
          description = "Subtitle options",
          enabled = choices.ccVisible || choices.subtitleStyleVisible,
          onClick = { extras.onOpenMenu(TvCardMenu.Subtitles) },
          modifier = each.focusRequester(focus.subtitleOptions).opens(bands, TvCardMenu.Subtitles, TvCardMenu.SubtitleStyle),
          padding = Spacing.small,
      )
      TvMenuTool(speedLabel(choices.speed), "Speed", each.focusRequester(focus.speed).opens(bands, TvCardMenu.Speed)) { extras.onOpenMenu(TvCardMenu.Speed) }
      if (choices.audioOptions.isNotEmpty()) {
          TvMenuTool("Audio", "Audio", each.focusRequester(focus.audio).opens(bands, TvCardMenu.Audio)) { extras.onOpenMenu(TvCardMenu.Audio) }
      }
      TvMenuTool(choices.framing.label, "Framing", each.focusRequester(focus.framing).opens(bands, TvCardMenu.Framing)) { extras.onOpenMenu(TvCardMenu.Framing) }
  }

  @Composable
  private fun TvMenuTool(
      label: String,
      description: String,
      modifier: Modifier,
      onClick: () -> Unit,
  ) {
      TvOverlayButton(
          text = label,
          style = TvTypeScale.body,
          enabled = true,
          onClick = onClick,
          modifier = modifier.semantics { contentDescription = description },
          padding = Spacing.medium,
      )
  }

  /** Reports where this tool is, so each of [menus] opens just above it. Written only on a change: the bands are read during layout. */
  private fun Modifier.opens(
      bands: TvStageBands,
      vararg menus: TvCardMenu,
  ): Modifier =
      onGloballyPositioned { at ->
          val bounds = at.boundsInRoot().roundToIntRect()
          for (menu in menus) if (bands.openers[menu] != bounds) bands.openers[menu] = bounds
      }
  ```

  `TvPlayerControls.kt`:
  - `TvPlayerFocus` — remove `val settings = FocusRequester()`; add `val cc`, `val subtitleOptions`, `val speed`, `val audio`, `val framing` (`= FocusRequester()` each) and:
    ```kotlin
        /** The tool [menu] is opened from. */
        fun openerOf(menu: TvCardMenu): FocusRequester =
            when (menu) {
                TvCardMenu.Subtitles, TvCardMenu.SubtitleStyle -> subtitleOptions
                TvCardMenu.Speed -> speed
                TvCardMenu.Audio -> audio
                TvCardMenu.Framing -> framing
            }
    ```
  - `TvPlayerExtras` — replace the fields `speed`, `onOpenSettings`, `hasSubtitles`, `subtitlesOn`, `onToggleSubtitles` (lines 60-67) with:
    ```kotlin
        /** What the tools read and change: subtitles, speed, audio and framing. */
        val choices: PlayerChoices = PlayerChoices.Default,
        val onToggleSubtitles: () -> Unit = {},
        val onOpenMenu: (TvCardMenu) -> Unit = {},
    ```
    (import `player.PlayerChoices`).
  - `below`'s `else -> focus.settings` (line 175) → `else -> focus.cc`.
  - The bottom band's `.onGloballyPositioned { bands.barTop = it.boundsInRoot().top }` (line 149) becomes:
    ```kotlin
                    .onGloballyPositioned { at ->
                        bands.barTop = at.boundsInRoot().top
                        bands.card = at.boundsInRoot().roundToIntRect()
                    }
    ```
    (import `androidx.compose.ui.unit.roundToIntRect`).
  - line 179: `TvToolGroup(focus = focus, extras = extras, bands = bands)`.

  `TvPlayerControlsBridge.kt` — replace `TvControlsView`, `TvControlsActions` and `TvPlayerControlsForViewModel` (lines 18-90):

  ```kotlin
  /** What the controls read from the screen's own state, beside the player itself. */
  internal class TvControlsView(
      val marks: PlayerMarksState?,
      val held: Boolean,
      val upNext: UpNextUiState,
      val statsShown: Boolean,
      /** What the tools read: subtitles, speed, audio and framing. */
      val choices: PlayerChoices,
      /** Whether the run has an episode list to open; ☰ is left out without one. */
      val hasEpisodes: Boolean = false,
  )

  /** What pressing the controls does to the screen's own state rather than to the player. */
  internal class TvControlsActions(
      val onToggleStats: () -> Unit,
      val onAddToList: () -> Unit,
      val onKids: () -> Unit,
      val onOpenMenu: (TvCardMenu) -> Unit,
      /** One menu giving way to another in its place: CC ▾'s "Style…". */
      val onSwitchMenu: (TvCardMenu) -> Unit,
      val onCloseMenu: () -> Unit,
      val onOpenEpisodes: () -> Unit,
      val onCloseEpisodes: () -> Unit,
      val onPickEpisode: (String) -> Unit,
      val onToggleNotes: (() -> Unit)?,
      val onSeekBarFocused: (Boolean) -> Unit,
  )

  /**
   * [TvPlayerControls] over the shared ViewModel: the marks and statistics,
   * the tools and the standing "Play next". The up-next card is not among
   * them but floats over the stage ([TvPlayerStage]); its Play now is the
   * same step forward as "Play next", its Cancel the ViewModel's own, which
   * leaves that standing button in place.
   */
  @Composable
  internal fun TvPlayerControlsForViewModel(
      player: Player,
      set: MediaSet?,
      focus: TvPlayerFocus,
      viewModel: PlayerViewModel,
      view: TvControlsView,
      actions: TvControlsActions,
      bands: TvStageBands,
  ) {
      TvPlayerControls(
          player = player,
          set = set,
          focus = focus,
          extras =
              TvPlayerExtras(
                  marks = view.marks,
                  markActions = TvMarksActions(viewModel::toggleWatchlist, actions.onKids, actions.onAddToList),
                  statsShown = view.statsShown,
                  onToggleStats = actions.onToggleStats,
                  totals = viewModel.totals,
                  held = view.held,
                  choices = view.choices,
                  onToggleSubtitles = viewModel::toggleSubtitles,
                  onOpenMenu = actions.onOpenMenu,
                  hasNext = view.upNext.hasNext,
                  nextTitleLine = view.upNext.titleLine,
                  onPlayNext = viewModel::playNext,
                  onToggleNotes = actions.onToggleNotes,
                  upNextShown = view.upNext.phase != UpNextPhase.HIDDEN,
                  onOpenEpisodes = actions.onOpenEpisodes.takeIf { view.hasEpisodes },
              ),
          onSeekBarFocused = actions.onSeekBarFocused,
          bands = bands,
      )
  }
  ```

  (import `player.PlayerChoices`; drop nothing else.)

  `TvPlayerStage.kt` — whole file:

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.layout.BoxScope
  import androidx.compose.foundation.layout.BoxWithConstraints
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.widthIn
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.CompositionLocalProvider
  import androidx.compose.runtime.DisposableEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableFloatStateOf
  import androidx.compose.runtime.mutableStateMapOf
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.layout.boundsInRoot
  import androidx.compose.ui.layout.onGloballyPositioned
  import androidx.compose.ui.platform.LocalDensity
  import androidx.compose.ui.unit.IntRect
  import androidx.compose.ui.unit.dp
  import androidx.media3.common.Player
  import designsystem.Overscan
  import designsystem.Spacing
  import model.MediaSet
  import playback.TimedCue
  import player.EpisodeList
  import player.PlayerChoices
  import player.PlayerViewModel
  import player.UpNextPhase
  import player.UpNextUiState

  /**
   * Where the stage's bands are, in root coordinates, as they last reported
   * themselves: the top of the controls along the bottom ([barTop]) and
   * their whole extent ([card]), the bottom of the title and statistics
   * along the top ([topBottom]), the up-next card's top ([cardTop]), the
   * episode list's left edge ([panelLeft]), and each menu's tool
   * ([openers]). Each is what one band tells the others so none is drawn
   * over another, and so a menu opens over the tool that opened it.
   */
  internal class TvStageBands {
      var barTop by mutableStateOf<Float?>(null)
      var topBottom by mutableStateOf<Float?>(null)
      var cardTop by mutableStateOf<Float?>(null)
      var panelLeft by mutableStateOf<Float?>(null)
      var card by mutableStateOf<IntRect?>(null)
      val openers = mutableStateMapOf<TvCardMenu, IntRect>()
  }

  /** What the stage draws beside the controls: the film and its subtitles, whether the controls are up, the open menu, and the episode list while it is open. */
  internal class TvStagePicture(
      val cues: List<TimedCue>,
      val choices: PlayerChoices,
      val barShown: Boolean,
      val menu: TvCardMenu? = null,
      val sidebar: EpisodeList? = null,
  )

  /**
   * The film and everything over it, in three bands: along the top what is
   * playing, along the bottom the controls, and floating between them the
   * subtitles and the up-next card — each kept clear of the others by where
   * they report themselves ([bands]). A menu opens over the controls, above
   * its tool; the episode list stands down the right while it is open, and
   * what floats moves over to leave it room.
   */
  @Composable
  internal fun BoxScope.TvPlayerStage(
      player: Player,
      set: MediaSet?,
      focus: TvPlayerFocus,
      viewModel: PlayerViewModel,
      view: TvControlsView,
      actions: TvControlsActions,
      picture: TvStagePicture,
      bands: TvStageBands,
  ) {
      val shown = picture.barShown
      // The card stands just above the controls, where a lifted cue would go,
      // so while it shows the cue lifts clear of the card too.
      val lowest = listOfNotNull(bands.barTop, bands.cardTop).minOrNull()
      val room = TvCueRoom(barTop = lowest.takeIf { shown }, ceiling = bands.topBottom.takeIf { shown }, besideLeft = bands.panelLeft)
      TvVideoWithSubtitles(player, picture.cues, picture.choices, room)
      // Everything over the picture is a control that holds still under focus.
      CompositionLocalProvider(LocalFlatControls provides true) {
          if (shown) TvPlayerControlsForViewModel(player, set, focus, viewModel, view, actions, bands)
          if (shown && view.upNext.phase != UpNextPhase.HIDDEN) {
              TvUpNextOverStage(view.upNext, focus, viewModel::playNext, viewModel::cancelUpNext, bands, besideSidebar = picture.sidebar != null)
          }
          picture.menu?.let { menu ->
              TvCardMenuOverlay(menu, picture.choices, viewModel, bands, onClose = actions.onCloseMenu, onSwitch = actions.onSwitchMenu)
          }
          picture.sidebar?.let { list ->
              TvEpisodeSidebar(
                  list = list,
                  onPick = actions.onPickEpisode,
                  onClose = actions.onCloseEpisodes,
                  modifier = Modifier.align(Alignment.CenterEnd).onGloballyPositioned { bands.panelLeft = it.boundsInRoot().left },
              )
              DisposableEffect(Unit) { onDispose { bands.panelLeft = null } }
          }
      }
  }

  /**
   * The up-next card floating over the picture, as the web floats its own
   * and the phone clamps its own: at the bottom-right, just above the
   * controls ([TvStageBands.barTop]), never inside them. As wide as the
   * stage can spare, up to the card's own limit, and to the left of the
   * episode list while that is open, so the countdown stays in view while a
   * viewer picks.
   */
  @Composable
  private fun BoxScope.TvUpNextOverStage(
      state: UpNextUiState,
      focus: TvPlayerFocus,
      onPlayNow: () -> Unit,
      onCancel: () -> Unit,
      bands: TvStageBands,
      besideSidebar: Boolean,
  ) {
      var stageBottom by remember { mutableFloatStateOf(0f) }
      val lift = with(LocalDensity.current) { bands.barTop?.let { (stageBottom - it).coerceAtLeast(0f).toDp() } ?: Overscan.vertical }
      BoxWithConstraints(
          modifier =
              Modifier
                  .matchParentSize()
                  .onGloballyPositioned { stageBottom = it.boundsInRoot().bottom }
                  .padding(end = if (besideSidebar) TvSidebarWidth else 0.dp),
      ) {
          TvUpNextCard(
              state = state,
              playNow = focus.upNext,
              onPlayNow = onPlayNow,
              onCancel = onCancel,
              modifier =
                  Modifier
                      .align(Alignment.BottomEnd)
                      .padding(end = Overscan.horizontal, bottom = lift + Spacing.small)
                      .widthIn(max = (maxWidth - Overscan.horizontal * 2).coerceAtLeast(0.dp))
                      .onGloballyPositioned { bands.cardTop = it.boundsInRoot().top },
          )
      }
      DisposableEffect(Unit) { onDispose { bands.cardTop = null } }
  }
  ```

  `TvPlayerRemote.kt` line 18: `internal enum class TvControlsLanding { PlayPause, SeekBar, Opener }`.

  `TvPlayerScreenEffects.kt` — delete the `landing == TvControlsLanding.Settings -> focus.settings.requestFocus()` branch; KDoc "While a menu, the settings panel or the episode list is open" → "While a menu or the episode list is open".

  `TvPlayerKeys.kt` — `ClosePanel` doc (lines 44-45) → "Closes the open menu, or the episode list, and only that: the controls stay up behind it."; the `[panelOpen]` paragraph (lines 90-96) → "[panelOpen] is a menu over the controls or the episode list down the right, which answers before anything else: Back closes it, and the D-pad and Centre are ordinary focus movement and selection inside it — a Left meant for the next choice must not skip the film. The dedicated media keys keep their meaning, as they do everywhere: a viewer can pause to look at a subtitle size without closing the menu first."

  `TvPlayerScreen.kt`:
  - replace `var settingsOpen by rememberSaveable { mutableStateOf(false) }` with `var menu by rememberSaveable { mutableStateOf<TvCardMenu?>(null) }`; `val panelOpen = menu != null || sidebarShown`;
  - add after `panelOpen`:
    ```kotlin
        val closePanel = {
            if (menu != null) menu = null else sidebarOpen = false
            landing = TvControlsLanding.Opener
        }
    ```
  - `TvPlayerOverlaysReset(..., closePanel = { menu = null; sidebarOpen = false })`;
  - `TvPlayerBack(..., onClosePanel = closePanel, ...)`;
  - `TvControlsView(marks, held, upNext, statsShown, choices, hasEpisodes = episodes != null)`;
  - `TvControlsActions(onToggleStats = …, onAddToList = …, onKids = …, onOpenMenu = { opened -> focus.opener = focus.openerOf(opened); menu = opened }, onSwitchMenu = { menu = it }, onCloseMenu = closePanel, onOpenEpisodes = { focus.opener = focus.episodes; sidebarOpen = true }, onCloseEpisodes = closePanel, onPickEpisode = { id -> landing = TvControlsLanding.PlayPause; sidebarOpen = false; viewModel.playFromRun(id) }, onToggleNotes = …, onSeekBarFocused = …)` (the `onOpenSettings` and `onPlayNext` arguments go);
  - `TvStagePicture(subtitleCues, choices, barShown, menu, episodes.takeIf { sidebarShown })`;
  - KDoc: "The gear among the tools opens the phone's playback settings as a panel to one side ([TvPlayerSettingsPanel])" → "The tools open small menus above themselves ([TvCardMenuOverlay]); ☰ opens the run's episodes down the right ([TvEpisodeSidebar])"; "Back closes the panel" → "Back closes a menu or the episode list".

  Delete `TvPlayerSettingsPanel.kt`.

- [ ] **Step 4: Run, expect PASS**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.*'` — all green. Then `grep -rn "TvPlayerSettingsPanel\|TvSettingsPanelTag\|Playback settings\|TvControlsLanding.Settings" android/ui-tv/src` → nothing.

- [ ] **Step 5: Commit**

  ```bash
  git add android/ui-tv/src/main/kotlin/ui/tv/player/TvCardMenu.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvToolGroup.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvSettingsChoices.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvSubtitleStyleSection.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControls.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControlsBridge.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerStage.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerRemote.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreenEffects.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeys.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt android/ui-tv/src/test/kotlin/ui/tv/player/
  git rm android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerSettingsPanel.kt
  git commit -m "feat(tv): small menus above the player's tools replace the settings panel"
  ```

---

### Task 5: The control card, and marks and Notes along the top

The bottom band becomes the 760 dp card in three rows; ↺ ⏮ ⓘ ☰ join the transport; marks and Notes move to the top bar; ⏮ and the remote's Previous go through `viewModel.previous()`.

**Files:**
- Modify `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControls.kt` (whole file), `TvTransport.kt` (whole file), `TvToolGroup.kt` (`TvToolGroup` only), `TvPlayerTopBar.kt` (whole file), `TvMarksRail.kt` (whole file), `TvPlayerControlsBridge.kt` (`TvPlayerControlsForViewModel` extras), `TvPlayerScreen.kt` (whole file)
- Delete `TvPlayerRun.kt`
- Tests: edit `TvPlayerScreenHarness.kt`, `TvPlayerScreenTest.kt`, `TvPlayerMarksTest.kt`, `TvPlayerNotesTest.kt`, `TvPlayerLessonNotesTest.kt`, `TvPlayerRunOverlaysTest.kt`, `TvPlayerUpNextTest.kt`, `TvPlayerRunKeysTest.kt`, `TvPlayerCaptionsKeyTest.kt`; create `TvPlayerBackOrderTest.kt`

**Interfaces:**
- Consumes: `fun PlayerViewModel.previous()`, `fun PlayerViewModel.restart()`, `UpNextUiState.inRun/hasPrevious/hasNext` (phase 03); `TransportIcons.Previous`, `TransportIcons.Next` (`ui-common/src/main/kotlin/ui/player/TransportIcons.kt`); `rememberSeekBackButtonState`/`rememberSeekForwardButtonState`/`rememberPlayPauseButtonState` (media3, as `TvTransport.kt:17-19`); `TvSeekBar(…, up: FocusRequester?)` (`TvSeekBar.kt:49-58`); `endsLine` (`feature/player/src/main/kotlin/EndsAt.kt:57`); `TvCardTools` (task 4).
- Produces:
  ```kotlin
  internal val TvCardWidth: Dp // 760.dp
  @Composable internal fun TvPlayerTopBar(set: MediaSet?, marks: PlayerMarksState?, markActions: TvMarksActions, onToggleNotes: (() -> Unit)?, focus: TvPlayerFocus, modifier: Modifier = Modifier)
  @Composable internal fun TvMarksRail(marks: PlayerMarksState?, actions: TvMarksActions, first: FocusRequester, down: FocusRequester, modifier: Modifier = Modifier)
  @Composable internal fun TvTransport(player: Player, focus: TvPlayerFocus, extras: TvPlayerExtras, modifier: Modifier = Modifier)
  internal class TvPlayerExtras(marks, markActions, statsShown, onToggleStats, totals, held = false, choices = PlayerChoices.Default,
      onToggleSubtitles = {}, onOpenMenu = {}, upNext: UpNextUiState = UpNextUiState(), onRestart = {}, onPrevious = {}, onPlayNext = {},
      onOpenEpisodes: (() -> Unit)? = null, onToggleNotes: (() -> Unit)? = null)
  // harness: internal fun toTool(target) — Up into the tools; internal fun toTopBar(target)
  ```

- [ ] **Step 1: Write the failing tests**

  `TvPlayerScreenHarness.kt` — replace `toTool` with:

  ```kotlin
      /** From the controls as they open, on play/pause: up into the row of tools, then Right along it until [target] holds the remote. */
      internal fun toTool(target: SemanticsMatcher) {
          press(Key.DirectionUp)
          along(target)
      }

      /** From play/pause: up through the tools and the seek bar to the controls along the top, then Right along them to [target]. */
      internal fun toTopBar(target: SemanticsMatcher) {
          repeat(3) { press(Key.DirectionUp) }
          along(target)
      }
  ```

  `TvPlayerScreenTest.kt` — add imports `androidx.compose.ui.test.getBoundsInRoot`, `androidx.compose.ui.test.hasContentDescription`, `androidx.compose.ui.unit.dp`, `androidx.compose.ui.unit.width`; append:

  ```kotlin
      @Test
      fun theCardIsCentredAtMostItsWidthAndStandsOffTheBottom() {
          val card = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
          assertEquals(TvCardWidth, card.width)
          assertEquals(100.dp, card.left)
          assertEquals(508.dp, card.bottom)
      }

      @Test
      fun upFromTheTransportReachesTheToolsThenTheSeekBarAndDownComesBack() {
          press(Key.DirectionUp)
          compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
          press(Key.DirectionUp)
          compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
          press(Key.DirectionDown)
          compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
          press(Key.DirectionDown)
          compose.onNodeWithContentDescription("Pause").assertIsFocused()
      }

      @Test
      fun restartGoesBackToTheTopAndKeepsPlaying() {
          toTransport(hasContentDescription("Restart"), Key.DirectionLeft)
          press(Key.DirectionCenter)

          assertEquals(0L, fixture.positionMs)
          assertTrue(fixture.isPlaying)
      }

      /** A title opened on its own has no run: no ⏮, no ⏭, no ☰ — hidden, not disabled. */
      @Test
      fun aTitleWithNoRunHasNoPreviousNextOrEpisodes() {
          compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
          compose.onNodeWithContentDescription("Next").assertDoesNotExist()
          compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
          compose.onNodeWithContentDescription("Restart").assertExists()
          compose.onNodeWithContentDescription("Stats").assertExists()
      }
  ```

  `TvPlayerUpNextTest.kt` — add imports `androidx.compose.ui.test.hasContentDescription`, `androidx.compose.ui.test.onAllNodesWithContentDescription`; line 92 → `compose.onNodeWithContentDescription("Next").assertExists()`; append:

  ```kotlin
      @Test
      fun previousOnTheCardStepsBackThroughTheRun() {
          toTransport(hasContentDescription("Previous"), Key.DirectionLeft)
          press(Key.DirectionCenter)

          assertEquals(listOf("set-zero"), TvPlayerTestActivity.switches)
      }

      @Test
      fun nextOnTheCardStepsForward() {
          toTransport(hasContentDescription("Next"))
          press(Key.DirectionCenter)

          assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
      }
  ```

  `TvPlayerMarksTest.kt` — KDoc "where Down from the transport lands" → "where Up from the seek bar lands"; add import `androidx.compose.ui.test.hasText` (present) and `androidx.compose.ui.test.hasContentDescription` (present). Replace:
  - `downFromTheTransportReachesTheRailAndUpComesBack` (lines 36-43):
    ```kotlin
        @Test
        fun upFromTheSeekBarReachesTheMarksAndDownComesBack() {
            repeat(3) { press(Key.DirectionUp) }
            compose.onNodeWithText("My List").assertIsFocused()

            press(Key.DirectionDown)
            compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
        }
    ```
  - in `theRailCarriesTheThreeMarksAndWatchlistWrites` line 50 `press(Key.DirectionDown)` → `toTopBar(hasText("My List"))`;
  - in `theKidsMarkOpensTheChoiceAndFromSixMarksFromSix` lines 59-60 and `theKidsChoiceHoldsTheControlsAndCancelChangesNothing` lines 78-79 → `toTopBar(hasText("Not for kids"))`;
  - `theStatisticsToggleEndsTheRowOfTools` (lines 93-105):
    ```kotlin
        @Test
        fun theStatsButtonTogglesTheOverlay() {
            compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
            toTransport(hasContentDescription("Stats"))

            press(Key.DirectionCenter)
            compose.onNodeWithTag(TvStatsOverlayTag).assertExists()
            compose.onNodeWithText("buffer").assertExists()
            compose.onNodeWithContentDescription("Stats").assertIsFocused()

            press(Key.DirectionCenter)
            compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
        }
    ```
  - in `addToListFilesTheTitleAndHoldsTheControlsUntilItCloses` lines 109-111 and `aWriteThatCannotBeConfirmedIsSaidAndThenGoesByItself` lines 136-138 → `toTopBar(hasText("Add to list"))`;
  - in `TvPlayerKidsProfileMarksTest.aKidsProfileHasNoKidsMark` lines 167-168 → `toTopBar(hasText("My List"))` then `press(Key.DirectionRight)`.

  `TvPlayerNotesTest.kt` — `openNotes` KDoc "down to the tools" → "up to the top"; line 56 → `toTopBar(hasText("Notes") and hasClickAction())`.

  `TvPlayerLessonNotesTest.kt` — replace `withTheNotesOpenEveryControlFitsBesideThemAndIsReached`:

  ```kotlin
      /** The narrowest the stage gets: every control still has width, inside it, and the remote reaches each. */
      @Test
      fun withTheNotesOpenEveryControlFitsBesideThemAndIsReached() {
          val notesLeft = compose.onNodeWithTag(TvNotesTag).getBoundsInRoot().left
          val controls =
              listOf("Restart", "Back 15 seconds", "Pause", "Forward 15 seconds", "Stats", "Subtitles", "Subtitle options", "Speed", "Framing")
                  .map { hasContentDescription(it) } + (hasText("Notes") and hasClickAction())
          for (control in controls) {
              val bounds = compose.onNode(control).getBoundsInRoot()
              assertTrue(bounds.width > 0.dp && bounds.right <= notesLeft, "$control spans ${bounds.left}..${bounds.right}, the notes start at $notesLeft")
          }
          press(Key.DirectionRight)
          compose.onNodeWithContentDescription("Forward 15 seconds").assertIsFocused()
          press(Key.DirectionLeft)
          toTool(hasContentDescription("Framing"))
          press(Key.DirectionUp)
          compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
          press(Key.DirectionUp)
          along(hasText("Notes") and hasClickAction())
      }
  ```

  `TvPlayerRunOverlaysTest.kt` — `openAddToList` (lines 147-155):

  ```kotlin
      /** Up to the marks along the top, across to Add to list, and pressed. */
      private fun openAddToList() {
          toTopBar(hasText("Add to list"))
          press(Key.DirectionCenter)
          compose.onNodeWithText("☐ Favourites").assertIsFocused()
      }
  ```

  (import `androidx.compose.ui.test.hasText`); in `theCardDoesNotTakeTheRemoteFromTheSeekBar` line 113 `press(Key.DirectionUp)` → `repeat(2) { press(Key.DirectionUp) }`.

  `TvPlayerRunKeysTest.kt` — KDoc "— and where Previous goes in the run" → drop; replace the two `previousInRun` tests (lines 44-54) with:

  ```kotlin
      /** Where Previous goes is the run's own answer, shared with the phone; here it is only ever the remote's, never the session's. */
      @Test
      fun previousIsTakenWithTheNotesOpenAndTheUpNextCardUp() {
          assertEquals(TvKeyAction.Previous, tvKeyAction(Key.MediaPrevious, controlsShowing = true, focusInControls = false, upNextShown = true, notesOpen = true))
      }
  ```

  and rename `nextAndPreviousStepThroughTheRunWithTheSettingsPanelOpen` → `nextAndPreviousStepThroughTheRunWithAMenuOpen`, `backClosesTheSettingsPanelBeforeCancellingTheUpNextCard` → `backClosesAMenuBeforeCancellingTheUpNextCard`.

  `TvPlayerCaptionsKeyTest.kt` — rename `withTheSettingsPanelOpen` → `withAMenuOrTheEpisodesOpen`.

  `TvPlayerBackOrderTest.kt` (new):

  ```kotlin
  package ui.tv.player

  import androidx.compose.ui.input.key.Key
  import androidx.compose.ui.test.assertIsFocused
  import androidx.compose.ui.test.hasContentDescription
  import androidx.compose.ui.test.onNodeWithContentDescription
  import androidx.compose.ui.test.onNodeWithTag
  import androidx.compose.ui.test.onNodeWithText
  import io.mockk.verify
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import org.robolectric.annotation.Config

  /**
   * Back on a television, one thing at a time, front to back: an open menu,
   * then the episode list, then the statistics, then the controls — and only
   * then is the title left. Each close hands the remote back to what opened it.
   */
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [35], qualifiers = "w960dp-h540dp")
  class TvPlayerBackOrderTest : TvPlayerScreenHarness() {
      override val run = THREE_TITLE_RUN

      override fun makeFixture() = runFixture()

      @Test
      fun backClosesTheEpisodesThenAMenuThenTheStatisticsThenTheControls() {
          toTransport(hasContentDescription("Stats"))
          press(Key.DirectionCenter)
          compose.onNodeWithTag(TvStatsOverlayTag).assertExists()

          along(hasContentDescription("Episodes"))
          press(Key.DirectionCenter)
          compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
          back()
          compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Episodes").assertIsFocused()
          compose.onNodeWithTag(TvStatsOverlayTag).assertExists()

          press(Key.DirectionUp)
          along(hasContentDescription("Speed"))
          press(Key.DirectionCenter)
          compose.onNodeWithTag(TvCardMenuTag).assertExists()
          back()
          compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
          compose.onNodeWithContentDescription("Speed").assertIsFocused()
          compose.onNodeWithTag(TvStatsOverlayTag).assertExists()

          back()
          compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
          compose.onNodeWithTag(TvSeekBarTag).assertExists()

          back()
          compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
          verify(exactly = 0) { fixture.media.stop() }

          back()
          compose.onNodeWithText("Library").assertExists()
      }
  }
  ```

- [ ] **Step 2: Run, expect FAIL**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests 'ui.tv.player.TvPlayerScreenTest' --tests 'ui.tv.player.TvPlayerUpNextTest' --tests 'ui.tv.player.TvPlayerBackOrderTest'`
  Expected: compilation FAIL — `TvCardWidth` unresolved; once stubbed, FAIL on missing "Restart", "Previous", "Stats" and on Up landing on the marks rail.

- [ ] **Step 3: Implement**

  `TvPlayerControls.kt` — whole file:

  ```kotlin
  // media3 marks its extension surface @UnstableApi and may change it in any
  // minor release; see CacheProvider for why the version is pinned rather
  // than floored, and why this is androidx's opt-in and not Kotlin's.
  @file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

  package ui.tv.player

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.widthIn
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.layout.boundsInRoot
  import androidx.compose.ui.layout.onGloballyPositioned
  import androidx.compose.ui.platform.testTag
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.roundToIntRect
  import androidx.media3.common.Player
  import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
  import androidx.tv.material3.Text
  import designsystem.Overscan
  import designsystem.Palette
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import model.MediaSet
  import playback.PlaybackTotals
  import player.PlayerChoices
  import player.PlayerMarksState
  import player.READOUT_TICK_MS
  import player.UpNextPhase
  import player.UpNextUiState
  import player.clockTime
  import player.endsLine
  import ui.player.SCRIM_ALPHA
  import ui.player.playerCard

  /** The focus stops the player screen moves the remote between — each attached for as long as its control is drawn, never added or dropped by a condition. */
  internal class TvPlayerFocus {
      val playPause = FocusRequester()
      val seekBar = FocusRequester()
      val cc = FocusRequester()
      val subtitleOptions = FocusRequester()
      val speed = FocusRequester()
      val audio = FocusRequester()
      val framing = FocusRequester()
      val episodes = FocusRequester()
      val marks = FocusRequester()
      val upNext = FocusRequester()
      val notes = FocusRequester()
      val retry = FocusRequester()
      val notesRegion = FocusRequester()

      /** The control a menu or the episode list was opened from: where the remote goes back to when it closes. */
      var opener: FocusRequester = playPause

      /** The tool [menu] is opened from. */
      fun openerOf(menu: TvCardMenu): FocusRequester =
          when (menu) {
              TvCardMenu.Subtitles, TvCardMenu.SubtitleStyle -> subtitleOptions
              TvCardMenu.Speed -> speed
              TvCardMenu.Audio -> audio
              TvCardMenu.Framing -> framing
          }
  }

  /** What the controls show beyond the player's own state, and what pressing them does. */
  internal class TvPlayerExtras(
      val marks: PlayerMarksState?,
      val markActions: TvMarksActions,
      val statsShown: Boolean,
      val onToggleStats: () -> Unit,
      val totals: () -> PlaybackTotals,
      /** Whether this device holds the title in full, which the statistics' buffer row reports as "cached". */
      val held: Boolean = false,
      /** What the tools read and change: subtitles, speed, audio and framing. */
      val choices: PlayerChoices = PlayerChoices.Default,
      val onToggleSubtitles: () -> Unit = {},
      val onOpenMenu: (TvCardMenu) -> Unit = {},
      /** The run as ⏮ and ⏭ walk it, and whether the up-next card floats above the card. */
      val upNext: UpNextUiState = UpNextUiState(),
      val onRestart: () -> Unit = {},
      val onPrevious: () -> Unit = {},
      val onPlayNext: () -> Unit = {},
      /** Opens the episode list; null with no list to open, which leaves ☰ out. */
      val onOpenEpisodes: (() -> Unit)? = null,
      /** Opens and closes the notes column; null while the title has none, which leaves the Notes button out. */
      val onToggleNotes: (() -> Unit)? = null,
  )

  /** Finds the controls' two bands in a test: what is playing along the top, and the card along the bottom. */
  internal const val TvTopBandTag = "tv-player-top-band"
  internal const val TvBottomBandTag = "tv-player-bottom-band"

  /** The card's width on a television: the web's card, narrowed to what reads across a room without turning the head. */
  internal val TvCardWidth = 760.dp

  /** How far the card stands off the bottom: clear of the overscan margin, with room to breathe. */
  private val TvCardInset = 32.dp

  /**
   * The controls over the picture. Along the top, what is playing and the
   * marks and Notes beside it, with the playback statistics under them when
   * they are on. At the bottom, one card in three rows: where the film is and
   * when it ends, the tools, and the transport — the web's card, filled
   * rather than frosted (see `PlayerCardSurface.kt` for why Android draws no
   * blur).
   *
   * The remote lands on play/pause. Up goes to the tools, then the seek bar,
   * then the top — or to the up-next card while it floats above the card,
   * since that is what is waiting on an answer. Every hop between the rows is
   * named rather than left to geometry: the rows are different lengths, and a
   * nearest-neighbour search from the end of one lands on whatever happens to
   * sit above it.
   *
   * Each band reports where it is to [bands] — the card its top and its
   * extent, the top band its bottom — so what floats between them keeps clear
   * of both, and a menu opens just above the tool that opened it.
   */
  @Composable
  internal fun TvPlayerControls(
      player: Player,
      set: MediaSet?,
      focus: TvPlayerFocus,
      extras: TvPlayerExtras,
      onSeekBarFocused: (Boolean) -> Unit,
      bands: TvStageBands,
  ) {
      val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)
      val positionMs = progress.currentPositionMs.coerceAtLeast(0L)
      val durationMs = progress.durationMs.coerceAtLeast(0L)
      val above =
          when {
              extras.upNext.phase != UpNextPhase.HIDDEN -> focus.upNext
              extras.marks != null -> focus.marks
              extras.onToggleNotes != null -> focus.notes
              else -> null
          }

      Box(modifier = Modifier.fillMaxSize()) {
          Column(
              modifier =
                  Modifier
                      .align(Alignment.TopStart)
                      .fillMaxWidth()
                      .onGloballyPositioned { bands.topBottom = it.boundsInRoot().bottom }
                      .testTag(TvTopBandTag),
          ) {
              TvPlayerTopBar(
                  set = set,
                  marks = extras.marks,
                  markActions = extras.markActions,
                  onToggleNotes = extras.onToggleNotes,
                  focus = focus,
                  modifier =
                      Modifier
                          .fillMaxWidth()
                          .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                          .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
              )
              if (extras.statsShown) {
                  TvStatsOverlay(
                      player = player,
                      totals = extras.totals,
                      held = extras.held,
                      modifier = Modifier.padding(start = Overscan.horizontal, top = Spacing.medium),
                  )
              }
          }
          Column(
              modifier =
                  Modifier
                      .align(Alignment.BottomCenter)
                      .padding(start = Overscan.horizontal, end = Overscan.horizontal, bottom = TvCardInset)
                      .widthIn(max = TvCardWidth)
                      .fillMaxWidth()
                      .onGloballyPositioned { at ->
                          bands.barTop = at.boundsInRoot().top
                          bands.card = at.boundsInRoot().roundToIntRect()
                      }.testTag(TvBottomBandTag)
                      .playerCard()
                      .padding(Spacing.medium),
              verticalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
              TvPlayerClock(
                  positionMs = positionMs,
                  durationMs = durationMs,
                  ends = endsLine(set?.durationSecs, positionMs, durationMs, player.playbackParameters.speed, System.currentTimeMillis()),
              )
              TvSeekBar(
                  positionMs = positionMs,
                  durationMs = durationMs,
                  focusRequester = focus.seekBar,
                  down = focus.cc,
                  up = above,
                  onFocusChanged = onSeekBarFocused,
              )
              TvToolGroup(focus = focus, extras = extras, bands = bands)
              TvTransport(player = player, focus = focus, extras = extras)
          }
      }
  }

  /** Where the film is, when it will end by the clock on the wall, and how long it runs — the phone's clock row, with the web's end time between. */
  @Composable
  private fun TvPlayerClock(
      positionMs: Long,
      durationMs: Long,
      ends: String,
  ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = clockTime(positionMs), style = TvTypeScale.body, color = Palette.Text)
          Text(text = ends, style = TvTypeScale.body, color = Palette.Figures)
          Text(text = clockTime(durationMs), style = TvTypeScale.body, color = Palette.Text)
      }
  }
  ```

  `TvToolGroup.kt` — replace only `TvToolGroup` (keep `TvCardTools`, `TvMenuTool`, `opens`; drop the now-unused `focusRequester`-for-Notes code path):

  ```kotlin
  /**
   * The card's middle row: its tools ([TvCardTools]), wrapping rather than
   * running off a stage the notes column has narrowed. From anywhere along
   * it, Up is the seek bar and Down is play/pause — named, since the row is
   * shorter than the ones either side of it.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  internal fun TvToolGroup(
      focus: TvPlayerFocus,
      extras: TvPlayerExtras,
      bands: TvStageBands,
  ) {
      val between =
          Modifier.focusProperties {
              this.up = focus.seekBar
              this.down = focus.playPause
          }
      FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
          itemVerticalAlignment = Alignment.CenterVertically,
      ) {
          TvCardTools(focus = focus, extras = extras, bands = bands, each = between)
      }
  }
  ```

  (imports: add `androidx.compose.foundation.layout.ExperimentalLayoutApi`, `androidx.compose.foundation.layout.FlowRow`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.ui.focus.focusProperties`; drop `androidx.compose.foundation.layout.Row`.)

  `TvTransport.kt` — whole file:

  ```kotlin
  // media3 marks its extension surface @UnstableApi and may change it in any
  // minor release; see CacheProvider for why the version is pinned rather
  // than floored, and why this is androidx's opt-in and not Kotlin's.
  @file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

  package ui.tv.player

  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.ExperimentalLayoutApi
  import androidx.compose.foundation.layout.FlowRow
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.focusProperties
  import androidx.compose.ui.focus.focusRequester
  import androidx.media3.common.Player
  import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
  import androidx.media3.ui.compose.state.rememberSeekBackButtonState
  import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
  import designsystem.Spacing
  import ui.player.TransportIcons

  /**
   * The card's bottom row: ↺, ⏮, back fifteen, play/pause, forward fifteen,
   * ⏭, then ⓘ and ☰ — the web's transport, read through the same media3
   * state holders the phone reads, so a label cannot come to say one thing
   * and do another and nothing about the player is carried through the
   * ViewModel.
   *
   * ⏮ and ⏭ walk the run, the same switch up next takes; with no run they
   * are left out rather than drawn dead, and at either end the one with
   * nowhere to go is dimmed. ↺ is the only restart: ⏮ never means "back to
   * the start of this one". ☰ is there while the run has an episode list.
   *
   * Up from any of them reaches the tools, at CC — the first of them.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  internal fun TvTransport(
      player: Player,
      focus: TvPlayerFocus,
      extras: TvPlayerExtras,
      modifier: Modifier = Modifier,
  ) {
      val playPause = rememberPlayPauseButtonState(player)
      val seekBack = rememberSeekBackButtonState(player)
      val seekForward = rememberSeekForwardButtonState(player)
      val run = extras.upNext
      val toTools = Modifier.focusProperties { this.up = focus.cc }

      FlowRow(
          modifier = modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
          itemVerticalAlignment = Alignment.CenterVertically,
      ) {
          TvGlyphButton(glyph = "↺", description = "Restart", enabled = true, onClick = extras.onRestart, modifier = toTools, padding = Spacing.small)
          if (run.inRun) {
              TvIconButton(icon = TransportIcons.Previous, description = "Previous", enabled = run.hasPrevious, onClick = extras.onPrevious, modifier = toTools)
          }
          TvGlyphButton(
              glyph = "−${seekBack.seekBackAmountMs / 1_000}",
              description = "Back ${seekBack.seekBackAmountMs / 1_000} seconds",
              enabled = seekBack.isEnabled,
              onClick = seekBack::onClick,
              modifier = toTools,
              padding = Spacing.small,
          )
          TvIconButton(
              icon = if (playPause.showPlay) TransportIcons.Play else TransportIcons.Pause,
              description = if (playPause.showPlay) "Play" else "Pause",
              enabled = playPause.isEnabled,
              onClick = playPause::onClick,
              modifier = toTools.focusRequester(focus.playPause),
          )
          TvGlyphButton(
              glyph = "+${seekForward.seekForwardAmountMs / 1_000}",
              description = "Forward ${seekForward.seekForwardAmountMs / 1_000} seconds",
              enabled = seekForward.isEnabled,
              onClick = seekForward::onClick,
              modifier = toTools,
              padding = Spacing.small,
          )
          if (run.inRun) {
              TvIconButton(icon = TransportIcons.Next, description = "Next", enabled = run.hasNext, onClick = extras.onPlayNext, modifier = toTools)
          }
          TvGlyphButton(glyph = "ⓘ", description = "Stats", enabled = true, onClick = extras.onToggleStats, modifier = toTools, padding = Spacing.small)
          extras.onOpenEpisodes?.let { open ->
              TvGlyphButton(glyph = "☰", description = "Episodes", enabled = true, onClick = open, modifier = toTools.focusRequester(focus.episodes), padding = Spacing.small)
          }
      }
  }
  ```

  `TvMarksRail.kt` — whole file:

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.ExperimentalLayoutApi
  import androidx.compose.foundation.layout.FlowRow
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.focus.focusProperties
  import androidx.compose.ui.focus.focusRequester
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import model.KidsVerdict
  import player.PlayerMarksState
  import player.kidsLabel
  import player.listLabel

  /**
   * The phone's three kept controls — My List, Kids, Add to list — along the
   * top beside what is playing, where the web's slim top bar keeps them:
   * "this is where a viewer is when they find out what a film actually is"
   * (`player.js`). Down from any of them goes to the seek bar ([down]).
   *
   * Absent with nothing open, as on the phone. The Kids mark is absent on a
   * kids profile — a child does not approve titles for itself — and dimmed
   * but still focusable on a rated title, whose rating decided and is still
   * worth reading. [first] is My List, the one mark always here while there
   * are any. Wraps rather than running off a stage the notes have narrowed.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  internal fun TvMarksRail(
      marks: PlayerMarksState?,
      actions: TvMarksActions,
      first: FocusRequester,
      down: FocusRequester,
      modifier: Modifier = Modifier,
  ) {
      if (marks == null) return
      val toCard = Modifier.focusProperties { this.down = down }

      FlowRow(
          modifier = modifier,
          horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.End),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
          itemVerticalAlignment = Alignment.CenterVertically,
      ) {
          MarkButton(label = listLabel(marks), onClick = actions.onToggleWatchlist, modifier = toCard.focusRequester(first))
          if (marks.canMarkKids) {
              MarkButton(label = kidsLabel(marks), onClick = actions.onKids, enabled = marks.kidsVerdict == KidsVerdict.UNRATED, modifier = toCard)
          }
          MarkButton(label = "Add to list", onClick = actions.onAddToList, modifier = toCard)
      }
  }

  /**
   * What the marks do. Kids and Add to list only open their dialogs: those
   * live with the screen rather than the rail, so they outlast the controls
   * fading behind it.
   */
  internal data class TvMarksActions(
      val onToggleWatchlist: () -> Unit,
      val onKids: () -> Unit,
      val onAddToList: () -> Unit,
  )

  @Composable
  private fun MarkButton(
      label: String,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      enabled: Boolean = true,
  ) {
      TvOverlayButton(text = label, style = TvTypeScale.body, enabled = enabled, onClick = onClick, modifier = modifier, padding = Spacing.medium)
  }
  ```

  `TvPlayerTopBar.kt` — whole file:

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.focusProperties
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.tv.material3.Text
  import designsystem.Palette
  import designsystem.Spacing
  import designsystem.TvTypeScale
  import model.MediaSet
  import player.PlayerMarksState
  import player.technicalLine
  import player.titleLine

  /**
   * The slim bar along the top: what is playing and what the file is — the
   * shared [titleLine] over [technicalLine], set quieter because it answers a
   * question a viewer only sometimes has — and beside it the marks and
   * Notes, the web's top bar. The title is only read; the marks and Notes
   * are pressed, one press up from the seek bar, and Down from them goes
   * straight back to it.
   */
  @Composable
  internal fun TvPlayerTopBar(
      set: MediaSet?,
      marks: PlayerMarksState?,
      markActions: TvMarksActions,
      onToggleNotes: (() -> Unit)?,
      focus: TvPlayerFocus,
      modifier: Modifier = Modifier,
  ) {
      Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
              set?.let {
                  Text(text = titleLine(it), style = TvTypeScale.title, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  // As stored, not shouted: the web prints the container and codecs in the case the index recorded them.
                  technicalLine(it).takeIf(String::isNotEmpty)?.let { line ->
                      Text(text = line, style = TvTypeScale.body, color = Palette.Figures, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
              }
          }
          TvMarksRail(marks = marks, actions = markActions, first = focus.marks, down = focus.seekBar)
          onToggleNotes?.let { toggle ->
              TvOverlayButton(
                  text = "Notes",
                  style = TvTypeScale.body,
                  enabled = true,
                  onClick = toggle,
                  modifier = Modifier.focusProperties { down = focus.seekBar }.focusRequester(focus.notes),
                  padding = Spacing.medium,
              )
          }
      }
  }
  ```

  `TvPlayerControlsBridge.kt` — in `TvPlayerControlsForViewModel` replace the `TvPlayerExtras(...)` arguments from `onOpenMenu = …` to the end with:

  ```kotlin
                  onOpenMenu = actions.onOpenMenu,
                  upNext = view.upNext,
                  onRestart = viewModel::restart,
                  onPrevious = viewModel::previous,
                  onPlayNext = viewModel::playNext,
                  onOpenEpisodes = actions.onOpenEpisodes.takeIf { view.hasEpisodes },
                  onToggleNotes = actions.onToggleNotes,
  ```

  (imports `player.previous`, `player.restart`; drop `player.UpNextPhase` if unused.) KDoc "the tools and the standing \"Play next\"" → "the tools and the transport, ⏮ and ⏭ included".

  `TvPlayerScreen.kt` — whole file:

  ```kotlin
  package ui.tv.player

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableIntStateOf
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.saveable.rememberSaveable
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.input.key.KeyEvent
  import androidx.compose.ui.input.key.KeyEventType
  import androidx.compose.ui.input.key.onPreviewKeyEvent
  import androidx.compose.ui.input.key.type
  import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import designsystem.Overscan
  import model.MediaSet
  import player.PlayerUiState
  import player.PlayerViewModel
  import player.UpNextPhase
  import player.controlsMayShow
  import player.playFromRun
  import player.previous
  import player.retry
  import player.toggleSubtitles
  import ui.player.KeepScreenOnWhile
  import ui.player.PlayerLifecycle
  import ui.player.PlayerNavigationEffects

  /**
   * A title playing on a television, driven by the remote — the phone's
   * `PlayerScreen` with keys where the phone has taps: the same lifecycle
   * ([PlayerLifecycle]), screen-on rule, picture and subtitles, and a card of
   * controls that fades by the same shared rule on the same clock — never
   * while paused, a menu is open or the episode list is.
   *
   * Every key is read once, here, before anything focused sees it, and
   * answered by [tvKeyAction]'s table through [TvPlayerRemote]; while the
   * controls are away [TvPlayerKeyHolder] holds the remote. The tools open
   * small menus above themselves ([TvCardMenuOverlay]); ☰ opens the run's
   * episodes down the right ([TvEpisodeSidebar]); notes open in a column
   * beside the picture ([TvNotesBeside]). Back closes a menu, then the
   * episodes, then the up-next card, then the notes, then the statistics,
   * then the controls ([TvPlayerBack]).
   *
   * [set] is the catalogue's entry for [setId], for the top bar and the end
   * time; null while the catalogue has none. [run] is what the title plays
   * into — up next, ⏮ and ⏭ and the remote's Next and Previous walk it, and
   * [onSwitch] moves the library to another title of it. [handPicked] says
   * [run] is a list or the Kids wall, which takes nothing ahead.
   */
  @Composable
  fun TvPlayerScreen(
      setId: String,
      set: MediaSet?,
      run: List<String>,
      handPicked: Boolean,
      onBack: () -> Unit,
      onSwitch: (setId: String, run: List<String>) -> Unit,
      viewModel: PlayerViewModel = hiltViewModel(),
  ) {
      val state by viewModel.state.collectAsStateWithLifecycle()
      val player by viewModel.player.collectAsStateWithLifecycle()
      val marks by viewModel.marks.collectAsStateWithLifecycle()
      val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
      val held by viewModel.held.collectAsStateWithLifecycle()
      val choices by viewModel.choices.collectAsStateWithLifecycle()
      val subtitleCues by viewModel.subtitleCues.collectAsStateWithLifecycle()
      val upNext by viewModel.upNext.collectAsStateWithLifecycle()
      val notes by viewModel.notes.collectAsStateWithLifecycle()
      val episodes by viewModel.episodes.collectAsStateWithLifecycle()
      val upNextShown = upNext.phase != UpNextPhase.HIDDEN
      val notesOpen = notes?.open == true
      val failed = state is PlayerUiState.Failed
      PlayerLifecycle(viewModel)
      PlayerNavigationEffects(viewModel, setId, run, set?.fsk, handPicked, onSwitch)
      // The countdown drops playing and the wait for the next title's buffer
      // pauses on purpose; neither is a viewer looking away.
      KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing || upNextShown || upNext.awaitingStart)

      var controlsShown by remember { mutableStateOf(true) }
      var landing by remember { mutableStateOf(TvControlsLanding.PlayPause) }
      var onSeekBar by remember { mutableStateOf(false) }
      // Every press restarts the fade: a viewer working the remote is using the controls.
      var presses by remember { mutableIntStateOf(0) }
      // Saved: a configuration change is not a viewer asking for the numbers,
      // a menu, the episodes or a dialog to go.
      var statsShown by rememberSaveable { mutableStateOf(false) }
      var choosingList by rememberSaveable { mutableStateOf(false) }
      var choosingKids by rememberSaveable { mutableStateOf(false) }
      var menu by rememberSaveable { mutableStateOf<TvCardMenu?>(null) }
      var sidebarOpen by rememberSaveable { mutableStateOf(false) }
      // A switch to a title with no run takes its list away, and the sidebar with it.
      val sidebarShown = sidebarOpen && episodes != null
      val panelOpen = menu != null || sidebarShown
      val focus = remember { TvPlayerFocus() }
      val closePanel = {
          if (menu != null) menu = null else sidebarOpen = false
          landing = TvControlsLanding.Opener
      }
      TvPlayerOverlaysReset(
          setId,
          marks == null,
          player == null,
          closeList = {
              choosingList = false
              choosingKids = false
          },
          closePanel = {
              menu = null
              sidebarOpen = false
          },
      )
      TvControlsAutoHide(controlsShown, state, presses, held = choosingList || choosingKids || upNextShown, menuOrSidebarOpen = panelOpen, onHide = { controlsShown = false })
      // Up with the card and left up after it, so the remote lands on it rather than on a picture it is being taken from.
      LaunchedEffect(upNextShown) { if (upNextShown) controlsShown = true }

      val barShown = (controlsShown || upNextShown) && controlsMayShow(state) && player != null
      val bands = remember { TvStageBands() }
      val root = remember { FocusRequester() }
      val notesFocus = remember { TvNotesFocus() }
      val remote =
          remember {
              TvPlayerRemote(
                  show = { to ->
                      landing = to
                      controlsShown = true
                  },
                  onNext = viewModel::playNext,
                  onPrevious = viewModel::previous,
                  onToggleSubtitles = viewModel::toggleSubtitles,
              )
          }
      TvRemoteFollowsControls(barShown, panelOpen, upNextShown, landing, root, focus, failed, notesOpen = { notesOpen }, busy = { choosingList || choosingKids || onSeekBar })
      TvNotesFollow(notesOpen, barShown, notesFocus, root, focus, busy = { panelOpen }, failed = { failed })
      TvPlayerBack(
          barShown = barShown,
          onSeekBar = onSeekBar,
          panelOpen = panelOpen,
          upNextShown = upNextShown,
          notesOpen = notesOpen,
          statsShown = statsShown,
          onClosePanel = closePanel,
          onCancelUpNext = viewModel::cancelUpNext,
          onCloseNotes = { notesFocus.closeFromBack(viewModel::toggleNotes) },
          onHideStats = { statsShown = false },
          onHideControls = { controlsShown = false },
          onLeave = onBack,
      )

      Box(
          modifier =
              Modifier
                  .fillMaxSize()
                  .background(Color.Black)
                  // Not focusable itself: see TvPlayerKeyHolder for why the one that holds the remote must never be an ancestor.
                  .onPreviewKeyEvent { event ->
                      if (event.type == KeyEventType.KeyDown) presses++
                      remote.onKey(event, player, barShown, onSeekBar, controlsMayShow(state), panelOpen, upNextShown, notesOpen)
                  },
      ) {
          TvPlayerKeyHolder(root, canHold = !barShown)
          TvNotesBeside(notes, focus.notesRegion, notesFocus, button = focus.notes.takeIf { barShown }) {
              player?.let { current ->
                  TvPlayerStage(
                      player = current,
                      set = set,
                      focus = focus,
                      viewModel = viewModel,
                      view = TvControlsView(marks, held, upNext, statsShown, choices, hasEpisodes = episodes != null),
                      actions =
                          TvControlsActions(
                              onToggleStats = { statsShown = !statsShown },
                              onAddToList = { choosingList = true },
                              onKids = { choosingKids = true },
                              onOpenMenu = { opened ->
                                  focus.opener = focus.openerOf(opened)
                                  menu = opened
                              },
                              onSwitchMenu = { menu = it },
                              onCloseMenu = closePanel,
                              onOpenEpisodes = {
                                  focus.opener = focus.episodes
                                  sidebarOpen = true
                              },
                              onCloseEpisodes = closePanel,
                              onPickEpisode = { id ->
                                  landing = TvControlsLanding.PlayPause
                                  sidebarOpen = false
                                  viewModel.playFromRun(id)
                              },
                              onToggleNotes = notes?.let { { notesFocus.toggleFromButton(notesOpen, viewModel::toggleNotes) } },
                              onSeekBarFocused = { onSeekBar = it },
                          ),
                      picture = TvStagePicture(subtitleCues, choices, barShown, menu, episodes.takeIf { sidebarShown }),
                      bands = bands,
                  )
              }
              // A dialog window's own keys: the remote's media keys still reach the film through it, as through a menu.
              val dialogKeys = { event: KeyEvent -> remote.onKey(event, player, controlsShowing = true, onSeekBar = false, canControl = controlsMayShow(state), panelOpen = true) }
              if (choosingList) TvAddToListOverPlayer(marks = marks, notice = actionNotice, viewModel = viewModel, onDismiss = { choosingList = false }, keys = dialogKeys)
              if (choosingKids) TvKidsChoiceOverPlayer(marks = marks, viewModel = viewModel, onDismiss = { choosingKids = false }, keys = dialogKeys)
              TvActionNotice(
                  notice = actionNotice,
                  onGone = viewModel::dismissActionNotice,
                  modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
              )
              TvPlayerStatus(state, focus.retry, onRetry = viewModel::retry)
          }
      }
  }
  ```

  Delete `TvPlayerRun.kt` (its Previous rationale now lives on `PlayerViewModel.previous()` in phase 03).

- [ ] **Step 4: Run, expect PASS**

  `cd android && ./gradlew -q :ui-tv:testDebugUnitTest` — whole module green. `wc -l android/ui-tv/src/main/kotlin/ui/tv/player/*.kt | sort -n | tail -5` — none above ~200. `grep -rn "previousInRun\|TvRunSteps\|Play next:" android/ui-tv/src` → nothing.

- [ ] **Step 5: Commit**

  ```bash
  git add android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControls.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvTransport.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvToolGroup.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerTopBar.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvMarksRail.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControlsBridge.kt android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt android/ui-tv/src/test/kotlin/ui/tv/player/
  git rm android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerRun.kt
  git commit -m "feat(tv): the player's controls become one card, with marks and Notes along the top"
  ```

---

## Next steps

- Phase 06: copy "Deliberate TV differences" into `docs/system-architecture.md`; walk the TV box (`-s 192.168.0.35:5555`) looking and navigating only — card, focus on play/pause, Speed menu opened and closed with Back (never choose), sidebar opened and closed (never pick), focused buttons not growing.

## Unresolved questions

- None blocking. If phase 04 records the TV card's tokens in the root `DESIGN.md`, it should cite `TvCardWidth` (760 dp) and the 32 dp inset from this phase rather than re-deciding them.
