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

- [ ] `UpstreamTraffic` (core/playback): foreground fetches counted and
      metered; a background fetch waits until no foreground fetch runs.
- [ ] Player factory reads through `foreground`, preload writer through `background`.
- [ ] One shared instance via `SharedPlaybackModule`.
- [ ] Tests: background waits for foreground; rate window.

## Phase 2: Android ⓘ rows

- [ ] `download`: `14.2 Mbit/s · 2 in flight` / `idle`
- [ ] `on disk`: `926 MB of 2.4 GB · 39%`
- [ ] `next`: `<title> · 34% · waiting for playback` / `· 11.6 Mbit/s`
- [ ] Phone and TV through the shared `playbackStats`.

## Phase 3: web ⓘ parity

- [ ] `GET /api/download/:setId`: on-disk bytes, gate traffic, preload in progress.
- [ ] `player-stats.js` polls it while the panel is open; same three rows.

## Phase 4: ship

- [ ] Version bump (minor), check.sh, merge, sideload the box, verify ⓘ on the box.
