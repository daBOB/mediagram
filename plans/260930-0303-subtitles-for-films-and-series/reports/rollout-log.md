# Rollout log — subtitles

## 2026-09-30 — phase 07a measurement (this machine)

Build: phase 07a branch (0.85.0) against a scratch copy of the channel index (v12 + the two v13 tables, applied to the copy only); uploader reinstalled at 0.83.0 (main) and `pull-index` (+1,543 sets, local 6,548; backup `library.before-channel-merge-260930-0830-*.db`).

**Local sources** (`backfill --dry-run` over this machine's series folders, 13 s): 144 episode files matched (mkv), **62 with de/en text**; 16 unmatched files. The second data drive was missing (btrfs `<missing disk>`, I/O errors) — not walked. Per-file output kept out of the repo (it names local paths).

**Uploaded copies** (`uploaded-stream-headers.sh subs`, ~26 min, headers only): 3,058 de/en-subtitled sets checked, **2,856 already carry de/en text tracks** in their uploaded copy (per-set output kept out of the repo)

| kind/container | sets | de/en text | picture-only | none |
|---|---|---|---|---|
| ep/mp4 | 2,165 | 2,155 | 0 | 10 |
| ep/mkv | 201 | 201 | 0 | 0 |
| movie/mkv | 680 | 491 | 188 | 1 |
| movie/mp4 | 12 | 9 | 0 | 3 |

**Extraction cost probe:** one 4.6 GB ep/mp4 through a loopback `serve`: a 647-cue English track in **65 s**, ~350–700 MiB read (mp4's sample index lets ffmpeg range-read the subtitle samples). MKV needs full reads (no per-stream index).

## Decision (user, 2026-09-30)

Film/series backfill **extracts from the uploaded copies through the channel**: MP4 titles first (~2,164, ≈1 min each, ≈1.5 days paced in the background), then MKV titles (~692: 201 episodes + 491 films, full reads, several TB, days), resumable and paced; local sources used where present. Picture-only titles (188 films) stay without subtitles. Phase 07 Part B must be re-planned around channel-side extraction before it is built.

## 2026-09-30 23:40 — v13 live; TV box on 0.92.0

- Other uploader: ≥ 0.84.7 (newer-schema guard) per the user; it pushed a v12 index at 22:26 (so < 0.85). Its next pull/publish is now refused until it runs ≥ 0.85 (upgrade to 0.92.0).
- This machine: uploader reinstalled 0.83.0 → 0.92.0. `pull-index` +553 sets, 3 shows, 21 credits (backup `library.before-channel-merge-260930-2136-*.db`, pulled message 12106); local index schema 13. `push-index` → **message 12130** (v13, first v13 index in the channel; no subtitle bundles yet, 1,266 inline rows).
- Web player: `bun --watch` dev player reloaded onto each merge; runs 0.92.0 code, healthy (0 failed reads).
- TV box `192.168.0.35:5555`: native core rebuilt, `installBenchmark` 0.84.6 → 0.92.0, `compile -m speed`. On its "TV test" profile: launches on the v13 index; Settings index has five sections (Profile = "TV test", System "0.92.0 · all current"); Profile pane shows Subtitles Off/German/English (Off), focus walk in and out without selecting. Geldhochschule 7 (`und` inline track): `keyevent 175` → cues on, controls shown, CC ●, focus on Pause; again → CC ○; CC button by D-pad → ● and back ○, focus stays on CC. Played for the test: Geldhochschule lesson 7 (resumed from its Continue position to its end) and a few seconds of the next lesson ("Trading · Tools"), both on "TV test". Storage read "70 MB of 8.0 GB" before the walk — not touched.
- Not done yet: tablet `caad49da` (not connected); forced-only title check (needs a bundle); one real end-to-end upload; move-inline + backfill runbook (phase 07); cross-device preference check (phase 08).

## 2026-10-01 08:10 — both uploaders on v13; first bundles live

- 02:24: a v12 index replaced this machine's v13 push (12130): an upload on the other machine finished on a pre-guard build and published. No data lost (no bundles existed yet); 58 sets came with it.
- Other machine now 0.92.1 (`main` 202ca23a, a prepare fix on top of 0.92.0). Its 04:28 push is **v13**, 7,202 sets, **8 bundles** (The Deuce S3E1–8, one English track each) — gate "other uploader at schema ≥ 13 by its own push" met, and phase 06 works on a real upload.
- Web (live player, API): S3E1 lists `English`; `/subtitles/0.vtt` → 200, WebVTT, 53,845 B, 0.24 s first (Telegram), 3 ms repeat (memory).
- This machine: uploader reinstalled 0.92.0 → 0.92.1.
- 08:15 `pull-index` (+101 sets, 8 bundles taken): **pre-move snapshot = message 12287**. `move-inline --dry-run`: 1,266 sets / 1,266 inline rows would move.

## 2026-10-01 — move-inline done; channel backfill spot check

- **move-inline** (this machine, 0.92.1, started 08:17, ~45 min): moved 1,266, mismatched 0 → published message **13597**. Channel copy afterwards: 0 inline subtitle rows, 1,302 bundles (incl. the other machine's new uploads; its later v13 push kept them all). Web: a Geldhochschule lesson serves its VTT from the bundle. TV box ("TV test"): Geldhochschule 8 shows cues from the bundle with the captions key (inline rows no longer exist, so this is the Android bundle path on a real bundle).
- **backfill --channel --dry-run:** 2,502 MP4 + 1,051 other candidates.
- **backfill --channel --limit 20** (10:12–11:10): bundled 20, nothing 0, skipped 0, failed 0 → published message **13722**. Star City S1 and Drops of God S1 episodes: German + English (+ SDH), and a real 2-cue "English (Forced)" track (on-screen captions) found by flag/title. **≈ 2.9 min per set** (4 tracks per set here), not the ~1 min the 07a single-track probe measured → MP4 remainder ≈ 5 days unattended; MKV (full reads) longer.
