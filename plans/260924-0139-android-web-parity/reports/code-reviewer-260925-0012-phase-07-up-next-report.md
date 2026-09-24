# Phase 07 Up next and queues: code review

Scope: the uncommitted diff against 2720641 plus the untracked files (UpNext/Autoplay/UpNextState/UpNextController/UpNextAsync, DefaultPlayerHandleOps, RunFor, the queue plumbing, UpNextCard/PlayAllButton). No code was edited. Every main file is under 200 lines; the largest are UpNextController 198 and PlayerViewModel 190.

## High

**H1. After an in-player switch, the screen's `setId` still names the old title. A rotation or process restore reopens the episode that just finished.**
- Where: `PlayerScreen.kt:71`, `UpNextController.kt:176` (onSwitch goes to `PlayerViewModel.open`), `LibraryFlowBranches.kt:36-43`.
- What happens: `onSwitch` calls `viewModel.open(next)` directly. The PLAYER frame in `LibraryPositions` keeps the old id. `LaunchedEffect(setId)` is keyed on that old id and runs again on every rotation, so it calls `open(oldId, run, oldFsk)`.
- Why it reloads: `sameTitle` compares against the session, which now holds `next`, so it is false. `handle.open` reloads the old title. That title was saved as finished, so `resumeAt` is null and it starts from 0.
- Scenario: S1E3 ends and autoplays into S1E4. The viewer turns the phone. S1E3 starts again from the top. If this happens while the gate is waiting, `startTitle` cancels the gate as well.
- Process death is the same: the saved frame restores S1E3. The fsk and run handed to the screen also belong to the old title.
- Fix: make the switch go through navigation. Give PlayerScreen an `onSwitch(setId, run)` that replaces the top PLAYER frame (`at.replacePlayer(next, run)`). The VM's `open` then runs from the `LaunchedEffect` as usual. Alternatively, key the effect on `viewModel.openSetId`, but the frame must still be updated for process restore.

**H2. The autoplay gate's 60-second threshold can never be met, so every unattended start of a normal-length title waits the full 45 seconds.**
- Where: `Autoplay.kt:13` together with `core/playback/.../PlayerFactory.kt:63`.
- What happens: `buildPlayer` uses media3's default `DefaultLoadControl`. Its maximum buffer is 50,000 ms, so `bufferedPosition - position` stops near 50 s. `READY_SECONDS = 60` is never reached. The "all remaining buffered" branch only applies to titles under about 50 s.
- Scenario: on a fast link the next episode sits paused for 45 s every time. The success criterion "never starts into a stall < 45 s" passes only by accident.
- Fix: also treat "the loader has stopped because the buffer is full" as ready, for example `!player.isLoading && ahead > 0` exposed through the handle. Or cap the threshold at the load control's maximum buffer minus a margin. Keep the web constant in the pure function and add the extra condition beside it.

## Medium

**M1. The gate is not cancelled when the viewer starts playback, so it can later override a pause.**
- Where: `UpNextController.kt:83-91` (`onPlayingChanged`) and `UpNextAsync.kt:65-82`.
- Web behaviour: `player.js:1066` has `video.addEventListener("play", stopWaitingToStart)`.
- Android behaviour: `gateJob` is cancelled only by `startTitle`, `switchTo` and `stop`.
- Scenario: the gated switch is waiting. The viewer taps play, then pauses 10 s later. The next 500 ms poll satisfies the gate (on patience or buffer) and calls `handle.play()`, which resumes against the viewer's pause.
- Fix: in `onPlayingChanged(true)`, cancel `gateJob` and set it to null.

**M2. The screen can sleep during the countdown and gate wait, and the next title then starts with the screen off.**
- Where: `PlayerScreenLifecycle.kt:39` (`KeepScreenOnWhile(isPlaying)`), `PlayerScreen.kt:72`.
- How it happens:
  - `STATE_ENDED` makes `isPlaying` false, which drops `FLAG_KEEP_SCREEN_ON` at the start of the 10 s countdown.
  - The last user touch was about an episode ago, so the display times out almost at once.
  - `ON_STOP` only saves; nothing pauses. So `switchTo` and then the gate's `handle.play()` start the next episode's audio with the screen locked.
  - Because of H2, the wait is usually 45 s, which makes this more likely.
- Web comparison: a browser tab has no equivalent.
- Fix: keep the screen on while `upNext.phase != HIDDEN` or a gate is pending. Alternatively, cancel the countdown and gate on `ON_STOP`.
- Needs checking on the device.

**M3. Kids "Marked by hand" tiles do not play into the marked-by-hand run the way the web does.**
- Where: `KeptWall.kt:154-161`.
- Web behaviour: `app.js:552` has `setGrid(byHand, (set) => play(set, byHand))`, so every tile plays into `byHand`.
- Android behaviour: the tile calls `onOpenTitle(item.setId)` with no run, so the next title comes from `runFor`. That is the item's show, or nothing for a film. Only the new "Play all" carries the run.
- Scenario: a hand-marked film has "Next" on the web and nothing on Android. A hand-marked episode continues into its show instead of the next marked item.
- Parity note: the web has no Play all on the Kids wall. The spec asked for one on Android. Record that as a deliberate difference, or drop it.

**M4. The run is worked out once, when the title opens. If the catalog is not Ready yet, it stays empty.**
- Where: `LibraryFlowBranches.kt:41`, `PlayerScreen.kt:71`, `PlayerViewModel.kt:128-131`.
- What happens: if the catalog is Loading when a PLAYER frame is restored (process death, cold resume), `runFor` returns empty. `startTitle(id, emptyList())` runs once, and the `LaunchedEffect` is keyed only on `setId`. When the catalog arrives, the run is never re-supplied, so that title has no next and no button. Rotation afterwards takes the `sameTitle` path, so it does not recover either.
- Fix: accept a changed run for the same title (`upNextController.updateRun`), or key the effect on `setId to run`, making sure `sameTitle` skips the reload.

## Low

**L1. The switch may write progress for the next title at its start position.**
- Where: `UpNextController.kt:166-178` together with `PlayerViewModel.kt:172-175`.
- What happens: on a Play now while playing, `session.open(next)` runs before `openReal`. `setMediaItem` fires `onIsPlayingChanged(false)` synchronously, so `session.onPlayingChanged(false)` calls `save()` against `next` at `startAtMs`, with an unknown duration. When that is 0, a progress row at 0 s can put the next episode on Continue before it has been watched.
- To check: whether `setProgress(at = 0)` is filtered anywhere.
- Fix: have the switch call `session.clear()` before `open`, or have `save()` skip `at < 1 s`.

**L2. While paused, the phase ticker does not run, so a seek into or out of the last 30 s does not update the card until play.**
- Where: `UpNextController.kt:83`.
- The web re-evaluates on `timeupdate`, which fires on seeks as well. Cosmetic.

**L3. The up-next card and the transport bar may overlap.**
- Where: `PlayerScreen.kt:151-156`.
- Both are aligned to `BottomCenter`, and the card forces `controlsShown = true` (`:99`). The card can cover the play/pause row. Check on the device.

## Verified OK
- The next title matches the web.
  - `runFor` returns empty for films. Otherwise it finds the first collection by show name in Series then Tutorials, the same order as `[...series, ...tutorials]`.
  - `playOrder` crosses season and folder boundaries.
  - Watched episodes are not skipped (`nextInQueue`, as in `library.js:231`).
  - List rows and Play all use the list's run.
- The countdown and cancel match the web.
  - Cancel is per title and lasts for the Activity-scoped VM, like the web's in-memory `Set`.
  - The standing button follows `hasNext`, not the phase.
  - Play now and the standing button start as soon as possible. Only the countdown goes through the gate.
- The ending title is saved as finished. `switchTo` calls `session.save()` while the player is still `STATE_ENDED` (position = duration), and the `isPlaying=false` at the end has saved it already.
- Timers do not leak. The countdown cancelling itself inside `onFinished` does no harm: no suspension follows, and the gate launches in `viewModelScope`. `stop()` cancels all four jobs.
- `playWhenReady=false` is not left behind. `stop()` cancels the gate and `handle.stop()` goes to IDLE, so the next manual `open` reloads through `openReal(playWhenReady=true)`.
- Rotation mid-countdown does not restart the timers. The same-set reopen takes the republish path, and both jobs live in the VM. Rotation after a switch is H1.

## Tests
- `UpNextControllerTest` drives the controller through a fake `onSwitch` that never re-enters `startTitle`. The real re-entrant path is not exercised anywhere: countdown, then `VM.open`, then `startTitle`, which cancels the countdown from inside it. `FakePlayerHandle.emitEnded` is defined but unused, and there is no VM-level test of a switch.
- No test covers the gate being cancelled by playback starting (M1), or the save of the ending title at the switch.
- `FakePlayerHandle.open` does not emit the synchronous `isPlaying=false` the real `setMediaItem` does, which is how L1 hides.

## Recommended order
H1, H2, M1, M2, M3, M4, then add the VM-level switch test using `emitEnded`.

## Unresolved
- The Kids Play all has no web counterpart. Keep it as a documented difference, or drop it?
- Is `setProgress(at=0)` filtered anywhere (L1)?

**Status:** DONE_WITH_CONCERNS
**Summary:** The next-title logic and the cancel/button behaviour match the web. Two high issues: a switch leaves the frame on the old id, so a rotation replays the finished episode, and the 60 s gate cannot be met under the default 50 s load control. Four medium issues.
