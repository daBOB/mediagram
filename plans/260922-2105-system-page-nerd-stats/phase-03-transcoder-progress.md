# Phase 3: Transcoder progress

## Context links
- `web/src/transcode/args.ts:62`: `["-hide_banner", "-loglevel", "error", "-y"]`, so stderr carries no stats today
- `web/src/transcode/ffmpeg.ts:43-44`: `Bun.spawn`, `stdout: "ignore"`; `:65` returns `Running`
- `web/src/transcode/registry.ts:45-60` (`Runner`/`Running`), `:144-163` (`start`), `:201-211` (`list()` drops `copyVideo`/`hevcCopy`), `:271-285` (`sessionId` mode string)
- `web/src/routes.ts:582-604`: `copyVideo`/`hevcCopy` decided from `copyVideoAs`
- Tests: `web/test/transcode-args.test.ts`, `transcode-registry.test.ts`

## Overview
Priority P2 · pending. Per session: speed (× realtime), fps, output position, CPU %, segments written, and mode (re-encode / copy / HEVC copy). Since startup: how many sessions each mode has started. The encoder is already a startup fact (`facts.encoder`).

## Key insights
- The installed ffmpeg supports `-progress` and `-stats_period` (checked with `ffmpeg -h full`). `-progress pipe:1` writes `key=value` blocks (`fps=`, `out_time_us=`, `speed=1.53x`, `progress=continue|end`) to stdout, independent of `-loglevel error`. That is the interface meant for programs, and it is sturdier than parsing stderr.
- `-stats_period 2` means one block every 2s, the same cadence as the panel. Parsing costs almost nothing.
- CPU: read `/proc/<pid>/stat` utime+stime (fields 14 and 15, in clock ticks, `100` on Linux) on each progress block, and take the difference from the last one to get CPU% since the previous block. This happens off the byte path. Off Linux it is `null`.
- Segments: count `*.ts|*.m4s` in the session directory with an async `readdir` at poll time (at most 4 sessions, no `stat`). The count is exact for copy mode too, where segment boundaries follow the source keyframes.
- DRY: `sessionId` already works out the mode string (`registry.ts:281`). Extract `modeOf(spec)` and use it in both places.

## Requirements
- Snapshot `transcodes.sessions[i]` gains:
  `mode: "encode"|"copy"|"hevc-copy"`, `speed: number|null`, `fps: number|null`, `outSeconds: number|null`, `cpuPercent: number|null`, `segments: number|null`
- `transcodes.started: { encode, copy, hevcCopy }`
- The `Running` interface gains an optional `progress(): TranscodeProgress | null`, so test runners stay valid.

## Architecture
```
args.ts: + "-progress","pipe:1","-stats_period","2"
ffmpeg.ts: stdout "pipe" → parseProgressBlock (transcode/progress.ts) → last; on block: cpuTicks(pid) → cpuPercent
registry.ts: start() → startedBy[modeOf(spec)]++ ; list() adds mode + process.progress?.()
live-facts.ts: per session await countSegments(directory)   (status/dir-bytes.ts)
```

## Related code files
- Create:
  - `web/src/transcode/progress.ts`: pure `parseProgress(text, carry)` (returns the finished blocks and the leftover text), `cpuTicksFromStat(text)`, and an async `readCpuTicks(pid)`. About 70 lines
  - `web/test/transcode-progress.test.ts`
- Modify:
  - `web/src/transcode/args.ts` + `web/test/transcode-args.test.ts`: new expected args
  - `web/src/transcode/ffmpeg.ts`: pipe stdout, keep the last progress, add `progress()`. Stays under 130 lines
  - `web/src/transcode/registry.ts`: `modeOf`, the `started` tally, and `list()` fields. Net change of about +12 lines on a file already over the limit. It is not split here, which is an accepted exception
  - `web/src/status/dir-bytes.ts`: add `countSegments(dir)`
  - `web/src/status/live-facts.ts`, `web/src/status/snapshot.ts`, and the tests `transcode-registry.test.ts`, `status-dir-bytes.test.ts`, `status-snapshot.test.ts`
- Delete: none

## Implementation steps
1. `progress.ts`: split on `\n`, gather `key=value` until `progress=`, and return `{speed, fps, outSeconds}`. `speed=N/A` gives null. Keep the partial line between chunks.
2. `args.ts`: append the progress flags before the output. Update the args test.
3. `ffmpeg.ts`: `stdout: "pipe"`, then an async loop like the stderr one (`:52-63`) that updates `last`. On each block, read `/proc/pid/stat` and compute `cpuPercent = Δticks / (100 × Δseconds) × 100`. Errors are swallowed; a lost reading never fails a playback.
4. `registry.ts`: `modeOf(spec)`, used by `sessionId` too. Add `started` counters, incremented in `start()` after `runner.start`. `list()` adds `copyVideo`, `hevcCopy`, `mode` and `progress`. Add a `started` getter.
5. `dir-bytes.ts`: `countSegments(dir)` (readdir, suffix filter, 0 when missing).
6. `live-facts.ts`: map the sessions and `await` the segment counts in parallel. Session ids and directories still stay out of the snapshot.
7. Tests:
   - Progress parsing: a multi-block chunk, a chunk split mid-line, `N/A`
   - The `/proc` stat parse, including a process name containing spaces and `)`. Parse from the last `)`
   - Registry tally and `list().mode`, with the existing fake runner
   - `countSegments`

## Todo
- [ ] progress parser + tests
- [ ] args flags + test update
- [ ] runner stdout + cpu
- [ ] registry modeOf/tally/list
- [ ] countSegments + test
- [ ] snapshot wiring + test

## Success criteria
- Unit tests pass.
- Stub harness (real ffmpeg on fully held sets): during a conversion, `speed` > 0, `segments` rises between polls, and `mode` matches the "copied" note the player shows.
- A copy of an h264 MKV reports a speed much higher than 1×.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Piping stdout that is never drained blocks ffmpeg | M×H | The loop drains stdout until EOF, the same pattern as stderr. The stub test must run a full conversion to its end |
| An old ffmpeg without `-stats_period` refuses the args | L×H | Verified locally. Record the minimum (ffmpeg ≥ 4.4) in `docs/running-the-player.md` |
| The `/proc` layout differs (not Linux) | M×L | `null`, and the row is left out |

## Security
Only process metrics are exposed. The PID stays on the server.

## Next steps
Phase 4.
