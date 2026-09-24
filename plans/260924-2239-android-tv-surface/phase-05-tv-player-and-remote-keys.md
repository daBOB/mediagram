# Phase 5: TV player and remote keys

**Context:** [plan.md](plan.md) · phone refs: `ui/player/*` · web refs: `lib/playback/player-keys.js`, `player-hud.js`, `resume-point.js` · shared: `PlayerLifecycle` (`:ui-common`), `controlsMayShow`/`controlsShouldFade` (`feature:player`)

## Overview

- **Priority:** High
- **Status:** pending
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

### Task 1: Key table (TDD)
- [ ] **1.1** Write `TvPlayerKeysTest` covering every row × both states first; run → FAIL.
- [ ] **1.2** Implement `tvKeyAction` pure. Run → PASS.
- [ ] **1.3** Commit — `feat(android): remote key model for television playback`.

### Task 2: Screen
- [ ] **2.1** `TvPlayerScreen`: `PlayerSurface` + `PlayerLifecycle` + `KeepScreenOnWhile`; `onPreviewKeyEvent` → `tvKeyAction` → media3 commands / focus moves.
- [ ] **2.2** `TvTransport` (−10 / play-pause / +10 with media3 button states), `TvSeekBar`, title + `technicalLine` top bar, time + ends-at.
- [ ] **2.3** `TvMarksRail`, `TvStatsOverlay`; `actionNotice` as a transient banner (no snackbar button to chase).
- [ ] **2.4** Robolectric: show/hide via keys; Back hides then leaves; lifecycle test mirrors `PlayerLifecycleTest`.
- [ ] **2.5** Commit — `feat(android): watch on a television with the remote`.

### Task 3: Emulator check on `TV test` profile
- [ ] **3.1** Play one short, fully-cached title if available (per memory: first frame 2–4 s cold). Exercise every key in the table with `adb shell input keyevent` (incl. `KEYCODE_MEDIA_PLAY_PAUSE`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`).
- [ ] **3.2** Home key mid-play → relaunch → resume offered.
- [ ] **3.3** Report every title played and any preference changed; reset preferences. The play lands on `TV test`'s Continue — expected; remove it via the phone's/web's normal UI if the user wants.

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
