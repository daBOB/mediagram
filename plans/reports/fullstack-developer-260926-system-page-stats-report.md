# System page nerd stats — implementation report

Plan: `plans/260922-2105-system-page-nerd-stats/` (phases 1–6, all done)
Branch: `feat/system-page-stats` (worktree `/home/andre/Workspace/mediagram-system-stats`)
Commits: `7a1a33ff` .. `904699d8` (6 commits, one per phase)

## What shipped

**Phase 1 — plumbing + host.** `status/live-facts.ts` (new) replaces the
inline `live()` closure in `index.ts`; `live` is now async. New `host` group:
`rssBytes`, `heapBytes`, `loopLagMs` (10s rolling window via
`status/loop-lag.ts`), `disks` (`status/disk-free.ts`, deduped by device),
`bun`. `memoryBytes` moved from the snapshot's top level into `host.rssBytes`
(breaking JSON, pre-release, welcomed).

**Phase 2 — Telegram link.** `telegram/measured-client.ts` subclasses
`TelegramClient`, overriding `invoke` to count requests/bytes/latency per DC
into `telegram/link-stats.ts` (ring buffer, nearest-rank p50/p95). Flood waits
counted from the "Sleeping for Ns" log line and from a thrown
`FloodWaitError`; reconnects from `UpdateConnectionState`. The default
logger's terminal output is reproduced exactly (colors, format) since setting
`.handler` replaces it. `teleproto@1.229.0` pin has a dedicated test.

**Phase 3 — transcoder progress.** `transcode/progress.ts` parses ffmpeg's
`-progress pipe:1` output and reads `/proc/<pid>/stat` for CPU deltas.
`transcode/registry.ts` gained `modeOf`/`started` tally/`list()` extensions;
split `session-identity.ts` (mode + id hash) out of it to stay under its
ratchet ceiling. `status/dir-bytes.ts` gained `countSegments`.

**Phase 4 — playback reports.** New `POST /api/status/playback`:
`status/playback-reports.ts` (validator + 15s-TTL/16-viewer store),
`public/lib/playback/playback-report.js` (client builder + 5s report loop).
`adapt-playback.js` gained `health()`. `player.js` wiring kept to a handful
of lines by moving the field-building into the new client module — it was
already at its file-size ceiling.

**Phase 5 — rendering.** Two new blocks (Telegram link, Watching now) and
extended Conversion/This player rows, in new `status-link-lines.js` /
`status-session-lines.js` (split out since `status-lines.js` was near its
own limit). Verified with real screenshots against the stub harness.

**Phase 6 — docs + parity.** `docs/web-player.md` documents all four groups
and the POST contract; a new "Differences from Android" subsection records
link/host as owed and Conversion as a deliberate difference (matching
Android's own already-recorded "No Conversion block"). ffmpeg 4.4 minimum
noted in `docs/running-the-player.md`. Changelog entry at the top of
`docs/project-changelog.md`. Version left untouched (all three manifests
already agree at `0.59.0`) per the dispatching task's explicit instruction.

## Files touched

New: `web/src/status/{live-facts,loop-lag,disk-free,playback-reports}.ts`,
`web/src/telegram/{link-stats,measured-client}.ts`,
`web/src/transcode/{progress,session-identity}.ts`,
`web/public/lib/playback/playback-report.js`,
`web/public/lib/status/{status-link-lines,status-session-lines}.js`, and
their test files.

Modified: `web/src/index.ts`, `web/src/status/{routes,snapshot,facts}.ts`,
`web/src/telegram/client.ts`, `web/src/transcode/{args,ffmpeg,registry}.ts`,
`web/public/lib/playback/{player,streaming/adapt-playback}.js`,
`web/public/lib/status/{status-lines,status-view}.js`,
`web/scripts/preview.ts` (wired a `status` route + `PlaybackReports` into
the stub harness — it had neither before phase 1), plus the pre-existing
test suites their signature changes touched (`status-http`, `status-snapshot`,
`transcode-registry`, `transcode-runtime`, `browser-application`,
`adapt-playback`, `status-lines`, `status-dir-bytes`).

Docs: `docs/web-player.md`, `docs/running-the-player.md`,
`docs/project-changelog.md`.

Visuals: `plans/260922-2105-system-page-nerd-stats/visuals/*.png`
(desktop + phone screenshots, one with a live "Watching now" row).

## Deviations from the plan (all noted in the phase files too)

- Code had moved since 2026-09-22: `refuseUnsafeBrowserWrite` was already
  extracted to `src/http/browser-write.ts` (phase 4 step 1 was a no-op);
  `player.js`/`registry.ts` were at different sizes than the plan cited, both
  already at (not "over") their ratchet ceilings, leaving zero headroom —
  handled by splitting new modules out rather than growing them, per this
  task's explicit "split, don't raise ceilings" instruction, which overrides
  the plan's own "accepted exception" language for `registry.ts`.
- `parseProgress`'s carry semantics were corrected from the plan's
  description during testing: it now retains every line since the last
  completed block, not only a trailing partial line, or a chunk boundary
  landing between whole lines (not mid-line) silently dropped data.
- `host.bun` is composed in `buildSnapshot` from `StartupFacts.runtime.bun`
  rather than inside `live-facts.ts`'s per-request read, since it is fixed
  for the process's life.
- Disk-free labels ("cache"/"conversions") are computed at render time in
  `status-link-lines.js` by comparing paths against `cache.dir`/`transcodes.dir`
  already in the snapshot, rather than plumbing a label through the server.
- Docs land in `docs/web-player.md` (where the System page is actually
  documented) rather than `docs/system-architecture.md:333-345`, which does
  not contain an `/api/status` section — it just points to `web-player.md`.
- Version bump skipped entirely on the dispatching task's explicit
  instruction (the lead bumps at merge); all three manifests already agree
  at `0.59.0` so there was nothing to reconcile.
- No Android/core follow-up plan stub opened — plan question 1 leaves that
  as a user decision; the "owed" record is written in docs and changelog
  regardless of when such a plan exists.

## Tests / verification

- `bun test`: 2058 pass, 0 fail (150 files). The `telegram-stream-cancel` and
  `state-store` console noise printed during the run is pre-existing,
  unrelated to this work (confirmed via `git stash` against the original
  `a03a2bae` tree before any of these changes).
- `bun run lint`: clean. `bunx tsc --noEmit -p .`: clean.
- `test/code-standards.test.ts` (the 200-line ratchet): clean; no ceiling
  raised, two files (`registry.ts`, `index.ts`) actually shrank below their
  prior ceilings.
- No Rust files touched anywhere in this branch (`git diff --name-only
  a03a2bae..HEAD | grep -c '\.rs$'` → 0), so `cargo test --workspace` was not
  run — nothing for it to catch.
- Stub harness (`cd web && bun run scripts/preview.ts`, no Telegram) used for
  every phase's manual check: `curl` against `/api/status` and
  `POST /api/status/playback` directly (204/403/415/400 all verified), and
  the rendered page in a headless browser (`gstack`'s `browse`) at desktop
  (1400×1200) and phone (390×844) widths — screenshots in
  `plans/260922-2105-system-page-nerd-stats/visuals/`. The real player was
  never started, per the hard constraint.

## Unverified / not exercised

- **A real ffmpeg conversion's live progress** (`speed > 0`, `segments`
  rising between polls) was not exercised in the stub harness: `scripts/
  preview.ts` has no media source or transcode registry wired at all
  ("Media is the one thing a preview cannot serve without Telegram"), and
  wiring a full held-cache-plus-encoder path into it was judged out of scope
  for this task. Covered instead by `transcode-runtime.test.ts`'s fake-ffmpeg
  progress/CPU tests, which exercise the same parsing and wiring logic.
- **`MeasuredClient.invoke` against a live Telegram connection** — per the
  plan's own risk note, this needs a live client and was left to a manual
  check "on the next real run, which the user starts". `LinkStats` and
  `countingLogger` are covered directly instead.
- The Android/core follow-up (per-DC link stats, host memory/disk) is
  recorded as owed but not scoped into a plan; that is an explicit open
  decision for the user (plan question 1).

## Status

**Status:** DONE
**Summary:** All 6 phases implemented, tested, and committed on
`feat/system-page-stats`; System page shows all four new groups, verified
against the stub harness with screenshots; docs and changelog updated;
version deliberately left for the lead to bump at merge.
**Concerns/Blockers:** None blocking. Two items are genuinely unverified
(live ffmpeg progress, live-Telegram `invoke` measuring) for the reasons
above — both are covered by unit tests exercising the same code paths, and
both require infrastructure (a real conversion, a real MTProto session) this
task's hard constraints correctly kept out of reach.
