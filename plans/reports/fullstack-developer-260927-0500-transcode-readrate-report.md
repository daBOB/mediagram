# Transcode read-rate cap — implementation report

Work context: `/home/andre/Workspace/mediagram-finish` (worktree, branch `fix/finish-in-one-write`). Not committed — lead commits.

## What changed

A conversion now paces its ffmpeg read at up to `READ_RATE` (2x real time)
after a `READ_BURST_SECONDS` (30s) burst, via `-readrate`/`-readrate_initial_burst`,
placed as input options right before `-i` (after `-ss`/`-noaccurate_seek` when
seeking). This only happens when a one-time startup probe finds the running
ffmpeg accepts the flags (5.0+ / 6.1+); an older ffmpeg runs exactly as before
and the player logs once that reads are unpaced and why.

**Shape chosen:** a `pacedReads: boolean` field, threading the same path
`encoder: Encoder` already does — `detectPacedReads()` at startup
(`web/src/index.ts`) → `FfmpegOptions.pacedReads` (`web/src/transcode/ffmpeg.ts`)
→ `TranscodeRequest.pacedReads` (`web/src/transcode/args.ts`) → the two flags
in `transcodeArgs`. `READ_RATE`/`READ_BURST_SECONDS` live in `args.ts` (where
the flags are built) and are imported into `encoders.ts` so the probe tests
the exact values used in production, not a copy that could drift.

`detectPacedReads` swallows any error from the probe (including a `spawn`
that itself throws, e.g. no ffmpeg on `PATH`) and resolves `false` rather than
reject — required so it can run in the same startup `Promise.all` as
`detectEncoder` without one probe's failure taking down the other.

## Files changed

- `web/src/transcode/args.ts` (+22 lines, 198 total): `TranscodeRequest.pacedReads`,
  exported `READ_RATE`/`READ_BURST_SECONDS` with why-comments, the `-readrate`/
  `-readrate_initial_burst` push before `-i`.
- `web/src/transcode/encoders.ts` (+30, 107 total): `detectPacedReads(io)`, same
  probe pattern as `detectEncoder`/`works`, imports the two constants from `args.ts`.
- `web/src/transcode/ffmpeg.ts` (+3, 151 total): `FfmpegOptions.pacedReads: boolean`,
  passed into the `transcodeArgs` call in `start()`.
- `web/src/index.ts` (+20 net, but file grows by 6 lines to 441 — see ceiling
  note below): `detectPacedReads` added to `StartupIo`/`startupIo`, run
  alongside `detectEncoder` in the existing startup `Promise.all`, logged once
  (`reads: paced` / `reads: unpaced (ffmpeg predates -readrate)`), and passed
  into the `FfmpegRunner` constructor.
- `web/test/transcode-args.test.ts` (+28): paced-reads on/off/seek-order tests.
- `web/test/transcode-runtime.test.ts` (+48): `detectPacedReads` probe tests
  (true/false/throwing-spawn/alongside-`detectEncoder`) plus one test that
  `FfmpegOptions.pacedReads` reaches the actual ffmpeg command.
- `web/test/code-standards.test.ts`: bumped the `src/index.ts` ratchet ceiling
  435 → 441 with a dated note — the file was already sitting at its old
  ceiling and the new startup wiring needed six more lines.
- `web/test/application-startup.test.ts`, `application-media-endpoint.test.ts`,
  `application-media-shutdown.test.ts`, `audio-shutdown.test.ts`,
  `cache-shutdown.test.ts`: every one of these calls `startPlayer(...)` with a
  `detectEncoder` override to keep startup hermetic; four of them additionally
  `spyOn(Bun, "spawn")` to fake ffmpeg/ffprobe for their own assertions
  (`inputs`, spawned-child counts, etc). Without a matching `detectPacedReads`
  override, the real probe would run during `startPlayer` and, in the
  spawn-spying tests, would show up as a spurious recorded "transcode" input
  and spawn a real child process — breaking `expect(inputs).toEqual([])` in
  `application-media-endpoint.test.ts` in particular. Added
  `detectPacedReads: async () => false` next to each existing `detectEncoder`
  override (one test in `application-startup.test.ts`, the one whose override
  intentionally throws to prove the encoder probe is skipped entirely, needs
  no matching override — the code path never reaches either probe).
- `docs/running-the-player.md`: one added sentence near the ffmpeg-version
  paragraph — 6.1+ paces conversions, older ffmpeg still works unpaced.
- `docs/project-changelog.md`: new top entry `## 0.68.8 — a conversion no
  longer pulls the whole film at once`.
- `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`: `0.68.7` →
  `0.68.8`. `Cargo.lock` regenerated via `cargo check -q -p mediagram` (5
  workspace crates' version fields only).

## Tests

Mutation-checked the new tests by hand (temporarily breaking the
implementation, confirming failure, then restoring):
- `READ_RATE` changed 2→3: 3 tests failed across `transcode-args.test.ts` and
  `transcode-runtime.test.ts` (args test, FfmpegRunner passthrough test, probe test).
- Guard `if (request.pacedReads === true)` replaced with `if (true)`: 2
  "unchanged by default" tests failed with the flags leaking into every command.
- `detectPacedReads`'s `try/catch` removed: the two tests covering a throwing
  probe failed with the raw `ffmpeg ENOENT` error instead of resolving `false`.

All three reverts confirmed clean afterward.

## Verification

- `bunx tsc --noEmit -p .` — clean.
- `bunx eslint public --max-warnings 0` — clean (untouched directory).
- `bun test` — **2181 pass, 0 fail**, 171 files (was 2180 before this work;
  net +9 new tests, and 2 existing test files gained a one-line
  `detectPacedReads` stub each without changing their assertions).
- `cargo check -q -p mediagram` — clean, `Cargo.lock` updated.

## Unresolved questions

None.

**Status:** DONE
