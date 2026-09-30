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
