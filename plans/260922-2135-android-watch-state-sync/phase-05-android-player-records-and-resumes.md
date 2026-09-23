# Phase 05 — Player records and resumes

## Context links

- `web/public/lib/player.js:489-516` (`saveProgress`, 10 s timer), `:1034` (pause), `:1052` (pagehide flush), `:808` (resume on open)
- `web/public/lib/resume-point.js:14-116` (`resumeAt`, `isFinished`, `watchedFraction`, `trustedRuntime`)
- `android/feature/player/src/main/kotlin/PlayerHandle.kt:12-36`, `DefaultPlayerHandle.kt:110-182` (`openOn` at 177-182 starts at 0)
- `android/feature/player/src/main/kotlin/PlayerViewModel.kt:44-100`
- `android/ui-mobile/src/main/kotlin/PlayerScreen.kt:52-65`, `LibraryFlow.kt:114`
- Phase 01 `resume-point.json`

## Overview

- Priority: P1. Status: code complete, tablet validation pending. Blocked by 04.
- The phone records where a viewer is, marks a title watched at the end, and opens a title where it was left — the same rules as the web, pinned by the shared fixture.

## Key insights

- **Same rule set, one port:** `ResumePoint.kt` in `core:data` (both features depend on it; `core:model` has no test setup). Its tests read `resume-point.json`.
- **Runtime:** `trustedRuntime(catalogued = MediaSet.durationSecs, observed = ExoPlayer duration, direct = true)`. Android never transcodes (`docs/system-architecture.md` §8), so the observed duration is the file's and may be believed, exactly as the web believes it for direct play.
- **Finishing** = `clearProgress` + `setWatched(true)` (only if not already watched), same moment — the tombstone the merge relies on (`merge.ts:1-20`).
- **When to save:** web = 10 s timer + pause + pagehide. Phone = 10 s while playing + pause + leaving the player + activity `ON_STOP`. Read ExoPlayer on main, write on IO.
- `PlayerUiState` keeps carrying no position (`PlayerUiState.kt:7-10`); the resume point is a start argument, not a second playhead.
- Rotation re-calls `open` for the same set and is ignored by `DefaultPlayerHandle.open` (`:122`), so no double seek.

## Requirements

- Functional: resume at `resumeAt(progress)` or 0; save rules above; watched on finish; `WatchSync.soon()` when the player is left.
- Non-functional: a failed write never surfaces; saving never blocks the player thread; no save while `STATE_IDLE`/errored (no position to trust).

## Architecture

```
PlayerScreen ─open(setId)─► PlayerViewModel ─► repository.snapshot → ResumePoint.resumeAt → handle.open(setId, startMs)
ExoPlayer ticks/pause/leave/ON_STOP ─► ProgressRecorder.save(at, observedDuration)
     ─► isFinished? clearProgress+setWatched : setProgress ─► repository (IO) ─► core
PlayerViewModel.stop() ─► recorder.save(final) ─► WatchSync.soon()
```

## Related code files

- Create: `core/data/src/main/kotlin/ResumePoint.kt`, `core/data/src/test/kotlin/ResumePointFixtureTest.kt` (reads `web/test/fixtures/watch-state/resume-point.json` found by walking up from the working dir; skipped via `Assume` when absent, like `shared_playable_sql.rs:19-24`), `feature/player/src/main/kotlin/ProgressRecorder.kt`, `feature/player/src/test/kotlin/ProgressRecorderTest.kt`.
- Modify: `feature/player/src/main/kotlin/PlayerHandle.kt` (`open(setId, startAtMs)`, `positionMs()`, `durationMs()`), `DefaultPlayerHandle.kt` (`setMediaItem(item, startAtMs)`), `PlayerViewModel.kt` (resume, 10 s ticker while Playing, save on pause/stop/clear), `ui-mobile/src/main/kotlin/PlayerScreen.kt` (ON_STOP observer → `viewModel.save()`), existing fakes in `feature/player/src/test`, `core/data/build.gradle.kts` (`testImplementation(libs.kotlinx.serialization)` for fixture parsing).
- Delete: none.

## Implementation steps

1. `ResumePoint.kt` (four functions, constants named as in JS) + fixture test.
2. `PlayerHandle` extension; `DefaultPlayerHandle.openOn(player, setId, startAtMs)`; update fakes.
3. `ProgressRecorder` (pure: takes repository + clock-free inputs); tests: glance not saved as resume, credits → watched + cleared, unknown runtime keeps position, already-watched not re-stamped.
4. `PlayerViewModel`: inject repository, recorder, `WatchSync`; `open` computes start; ticker `while (Playing) { delay(10_000); save() }`.
5. `PlayerScreen`: `LifecycleEventObserver` ON_STOP → `save()`.
6. Tests + lint; rebuild not needed (no Rust change); `./gradlew :app:installDebug`.

## Todo

- [x] ResumePoint.kt + fixture test
- [x] PlayerHandle start position + position/duration
- [x] ProgressRecorder + tests
- [x] PlayerViewModel wiring
- [x] PlayerScreen ON_STOP save
- [ ] tablet checks below

## Success criteria (tablet)

- Watch 4 min of an episode, press Home, swipe the app from recents, reopen, play: resumes within ~10 s of where it was.
- 15 s then leave: starts from 0 next time.
- Seek to the last 30 s, let it end: next open starts at 0, and (after 06) the card shows watched.
- Within 5 min (or on leaving the player), the web player's Continue shelf shows the phone's position (Q2 on).

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Kill without ON_STOP loses ≤10 s | H×L | Accepted; 10 s timer bounds it, same as web |
| Observed duration is 0/unknown early | M×M | `trustedRuntime` → 0 → "unknown" → never judged finished |
| Seek-to-start races `prepare` | L×M | `setMediaItem(item, startMs)` before `prepare()` is the media3-supported path |

## Security

None beyond 04: positions are local rows.

## Next steps

06 reads the snapshot to draw Continue, Next up, progress rules and ticks.
