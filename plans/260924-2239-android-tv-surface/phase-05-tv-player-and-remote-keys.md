# Phase 5: TV player and remote keys

**Context:** [plan.md](plan.md) · phone refs: `ui/player/*` · web refs: `lib/playback/player-keys.js`, `player-hud.js`, `resume-point.js` · shared: `PlayerLifecycle` (`:ui-common`), `controlsMayShow`/`controlsShouldFade` (`feature:player`)

## Overview

- **Priority:** High
- **Status:** done — f994577..7f15d11 (4 tasks + merge of main at 2d7dfd8)
- **Deliverable:** play from a title page and control playback entirely with the remote — including hardware media keys — with the phone's marks and stats available.

## Key insights

- Phone player is touch-only (tap toggles controls, drag scrub). TV needs a **key model**. The web already has one as a pure table (`keyAction`); TV gets the same shape: a pure `tvKeyAction(key, controlsShowing, focusInControls) → Action`, unit-tested, and a thin `onPreviewKeyEvent` that applies it.
- Proposed mapping (web meanings where a key exists on a remote):

| Key | Controls hidden | Controls showing |
|---|---|---|
| Centre / Enter | play-pause + show controls | activate focused control |
| Play/Pause, Play, Pause (media) | play-pause | play-pause |
| Left / Right, Rewind / FF (media) | −10 s / +10 s (web ←/→), show controls briefly | move focus between controls (Left/Right); media keys still seek |
| Up / Down | show controls, focus the seek bar | move between seek bar, transport row, marks rail |
| Back | leave player (saves, as phone) | hide controls |
| Next / Previous (media) | next / previous in `playOrder` if the phone has it; else ignore | same |

- **Seek bar on TV** = focusable progress; Left/Right on it steps ±10 s (repeat accelerates), no drag.
- Controls fade by the shared `controlsShouldFade` (web: 2.6 s, never while paused); phone constant reused.
- Transport comes from media3 `ui-compose` state holders, exactly as phone — no player state in the VM.
- Lifecycle is the shared `PlayerLifecycle`: Home mid-play saves; leaving stops; one player screen at a time (singleton ExoPlayer).
- Marks rail (Watchlist, Kids, Add to list) from `PlayerViewModel.marks`, hidden Kids mark on kids profiles as phone. Stats overlay reuses the moved stat-line builders.
- **Up next / autoplay**: check phone first (plan Open Question 3). TV mirrors phone; if phone lacks it, log the gap in phase 6 docs.
- MediaSession not added: media keys reach the focused activity as key events. Background/ambient control is out of scope.
- **Carried from phase 1:** two public `clockTime`s exist — `catalog.clockTime(seconds: Double)` and `player.clockTime(ms: Long)` — both implementing the web's `clockTime`. Merge them into one before the TV player reads time, so the surfaces cannot format time differently.

## Requirements

- Functional: every remote key in the table does its row; no key leaves the viewer stuck (Back always escapes).
- Non-functional: key table 100 % unit-covered; overlay never covers subtitles when hidden.

## Related code files

- Create: `android/ui-tv/src/main/kotlin/ui/tv/player/{TvPlayerScreen.kt,TvPlayerKeys.kt,TvTransport.kt,TvSeekBar.kt,TvMarksRail.kt,TvStatsOverlay.kt}`, tests `TvPlayerKeysTest.kt`, `TvPlayerScreenTest.kt`
- Modify: `TvLibrary.kt` (player branch)

## Implementation steps

Every task also binds to this file's **Key insights** and the plan's Global Constraints. Phone player reference: `android/ui-mobile/src/main/kotlin/ui/player/` (`PlayerScreen.kt`, `PlayerControls.kt`, `PlayerControlParts.kt`, `PlayerScreenParts.kt`, `PlayerMarks.kt`, `AddToListDialog.kt`, `PlaybackStatsOverlay.kt`) and its `LibraryFlow.kt` player branch. Shared: `android/feature/player/src/main/kotlin/` (`PlayerViewModel`, `ControlsVisibility.kt`, `PlayerClock.kt`, `TechnicalLine.kt`, `PlaybackStatRows.kt`, `PlayerMarksState.kt`, `KidsLabel.kt`), `android/ui-common/.../ui/player/{PlayerLifecycle.kt,KeepScreenOnWhile.kt}`. Web key reference: `web/public/lib/playback/player-keys.js`. TV building blocks: `ui/tv/TvFocus.kt`, `TvTextRow.kt`, `setup/TvConfirmDialog.kt`, `catalog/TvDialog*`, `catalog/TvLibrary*.kt` (player branch is a stub today).

### Task 1: One clock and the key table
- [ ] **1.1** Merge `catalog.clockTime(seconds: Double)` and `player.clockTime(ms: Long)` into one implementation (the web's `clockTime`), one owner module, the other surface-neutral caller delegating or switching over; phone output unchanged (its tests pass). No `*ViewModel.kt`/`*UiState.kt` edits beyond import changes.
- [ ] **1.2** TDD `tvKeyAction(key, controlsShowing, focusInControls) → TvKeyAction` in `ui/tv/player/TvPlayerKeys.kt` covering every row of the key table × both states; test first (FAIL), then implement (PASS). Next/Previous: check whether the phone has a play order; if not, they map to "ignore" and the test says so.
- [ ] **1.3** Commits — `refactor(android): one clock format for both surfaces`, `feat(android): remote key model for television playback`.

### Task 2: Player screen, transport and seek bar
- [ ] **2.1** `TvPlayerScreen(setId, fsk, onBack)`: `PlayerSurface` + `PlayerLifecycle` + `KeepScreenOnWhile` exactly as phone `PlayerScreen`; root `onPreviewKeyEvent` → `tvKeyAction` → media3 commands / focus moves / show-hide; controls fade by `controlsShouldFade` with the phone's constants.
- [ ] **2.2** `TvTransport` (−10 / play-pause / +10 with media3 `ui-compose` button states), `TvSeekBar` (focusable; Left/Right ±10 s, held key repeats accelerate; no drag), top bar with title + `technicalLine`, time + ends-at as phone.
- [ ] **2.3** Replace the player stub in `TvLibrary` with `TvPlayerScreen`; Back per the table (hide controls first, then leave via `leaveFrom`).
- [ ] **2.4** Robolectric: key-driven show/hide; Back hides then leaves; lifecycle test mirroring `PlayerLifecycleTest`.
- [ ] **2.5** Commit — `feat(android): watch on a television with the remote`.

### Task 3: Marks, add-to-list and stats
- [ ] **3.1** `TvMarksRail` (Watchlist, Kids, Add to list) from `PlayerViewModel` marks exactly as phone `PlayerMarks.kt`, Kids mark hidden on kids profiles; Add to list via a TV dialog (lists + new list) matching phone `AddToListDialog`.
- [ ] **3.2** `TvStatsOverlay` from the shared stat-row builders, toggled from the controls as phone; `actionNotice` as a transient banner.
- [ ] **3.3** Robolectric tests: rail reachable by Down from transport; kids profile hides the Kids mark; stats toggle.
- [ ] **3.4** Commit — `feat(android): marks and playback stats on the television player`.

### Task 4: Emulator check on `TV test` profile
- [ ] **4.1** Switch to `TV test` via the masthead. Play one short H.264 title if available. Exercise every key in the table with `adb shell input keyevent` (incl. `MEDIA_PLAY_PAUSE`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`).
- [ ] **4.2** Home key mid-play → relaunch → resume offered.
- [ ] **4.3** Report every title played and any preference changed; reset preferences. Plays land on `TV test`'s Continue — expected.

## Todo list
- [ ] Key table + tests
- [ ] Screen, transport, seek bar, marks, stats
- [ ] Lifecycle parity
- [ ] Emulator key pass on test profile

## Success criteria
Every row of the key table verified on the emulator; Back always escapes; position saved on Home.

## Risk assessment
| Risk | Mitigation |
|---|---|
| Emulator x86 decoding of HEVC/HDR files fails | Choose an H.264 title for the check; a decode failure on the emulator is not a TV bug — note it |
| `onPreviewKeyEvent` swallows keys the focused button needs | Table's "controls showing" column passes Centre through; tested |
| Streaming throughput on emulator differs | Not measured here; memory notes byte-path cost is latency-bound |

## Security considerations
None beyond phone.

## Next steps
Phase 6.
