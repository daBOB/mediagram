# Playback before preload, and download facts in ⓘ

Branch `fix/playback-before-preload`, worktree `../mediagram-download-facts`.

## Why

2026-10-21 the TV box starved over and over on Immer wieder Jim S1E1
(2.36 GB, 21 min, 15 Mbit/s). The box's own cache shows why
(span timestamps under `cache/mlib`):

| time | what | rate |
|---|---|---|
| 19:17:13–19:29:47 | E1 playing, 926 MB | 9.9 Mbit/s (needs 15) |
| 19:17:14–19:44:55 | E2 series preload, whole file | 11.6 Mbit/s |
| 19:44:55–20:06:06 | E3 preload, alone | 15.1 Mbit/s |

The Android `SeriesPreloader` fetches at full speed beside playback. The web
server's preload runs the download gate's background lane and only takes
slots playback is not using (`web/src/telegram/download-gate.ts`); the
Android port never got that rule. Divergence = defect in the newer surface.

Not changed: read-ahead width. E3 alone at one fetch in flight reached
15.1 Mbit/s, which matches `BITS_PER_SECOND_PER_FETCH` (16). The box's link
tops out around 17–20 Mbit/s in total, so more fetches in flight would not
buy throughput there.

## Phase 1: Android, playback goes first

- [x] `UpstreamTraffic` (core/playback): foreground fetches counted and
      metered; a background fetch waits until no foreground fetch runs.
- [x] Player factory reads through `foreground`, preload writer through `background`.
- [x] One shared instance via `SharedPlaybackModule`.
- [x] Tests: background waits for foreground; rate window.

## Phase 2: Android ⓘ rows

- [x] `download`: `14.2 Mbit/s · 2 in flight` / `idle`
- [x] `on disk`: `926 MB of 2.4 GB · 39%`
- [x] `next`: `<title> · 34% · waiting for playback` / `· 11.6 Mbit/s`
- [x] Phone and TV through the shared `playbackStats`.

## Phase 3: web ⓘ parity

- [x] `GET /api/download/:setId`: on-disk bytes, gate traffic, preload in progress.
- [x] `player-stats.js` polls it while the panel is open; same three rows.

## Phase 4: ship

- [x] Version bump (0.138.0, then 0.138.1 after review), merge to main, sideload box (release) and tablet caad49da (debug); both launch clean.
- [ ] Viewer walk on the box: warm-up line, buffer band, ⓘ rows (left to the user — devices are on profile A).

## Added mid-way (user, 2026-10-21 23:50)

Warm-up before every start, until the buffer is full (user chose this of three options), and the buffer fill level in the controls. Android: gate on every open except watch together; leader + "Warming up · 0:12 of 0:50"; seek-bar band. Web: hand-opened titles never auto-start, so only unattended starts warm up (documented in `docs/web-player.md`).

## Review (code-reviewer, `reports/code-reviewer-261022-0014-…`)

Fixed: H1 preload slipped in between one-at-a-time player fetches (now waits for a quiet second), M1 open title's own preload held the cache lock (now cancelled on open), L1 uninterruptible wait, L2 web leader over a failed start, L3 web on-disk could read 100%, L4 web next row across shows, L5 stale comments.
Open: M2 — warm-up also holds titles already held on disk (asked the user). L6 phone band paints over the thumb gap (cosmetic). Web gate has the same between-fetch gap as H1, unchanged: web preload is paced to 1 req/s.
