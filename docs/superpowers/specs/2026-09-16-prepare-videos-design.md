---
title: "Prepare videos for upload"
date: 2026-09-16
status: approved
affects: mediagram (media/, commands/prepare.rs)
---

# Prepare videos for upload

`mediagram prepare` makes a file fit in a single upload part by dropping
audio and subtitle tracks nobody in this household wants. The video stream is
copied untouched, so the picture is bit-identical and a season takes minutes
rather than hours.

## 1. Why this exists

Splitting works — a 6.53 GiB film uploaded as two parts and verified — but one
file per episode is simpler for a player than two, and most of the excess here
is not picture at all. A measured episode:

| Stream | Count | Bitrate |
|---|---|---|
| Video, h264 1920x1038 | 1 | ~10 Mbps |
| Audio, eac3/ac3 5.1 | 9 | 3.84 Mbps |
| Subtitles, subrip | 42 | small |

Seven of the nine audio tracks are languages this library does not need. Across
the measured season:

```
episode   size   audio subs  drop      after    fits one part?
S01E01   4.31G     9    42   2688k     3.48G    yes
S01E07   4.43G     9    42   2688k     3.57G    yes
S01E10   5.01G     9    42   2688k     4.04G    NO
season  39.8 GB -> 32.1 GB, saving 7.7 GB
```

Nine of ten drop under the threshold. The tenth is reported, not degraded, and
uploads as two parts.

## 2. Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Drop tracks only; never re-encode | Lossless, fast, and sufficient here. Re-encoding would degrade files that never needed it |
| 2 | Keep German and English audio and subtitles by default | Matches this library's dual-language releases. Overridable per run |
| 3 | The threshold is `part_size` (3.5 GiB), not 4 GB | The goal is one part per file. Aiming at 4 GB would still leave files splitting |
| 4 | Files already under the threshold are skipped | Nothing is destroyed without cause |
| 5 | Replace the original in place | User's choice: no extra disk. Made deliberate by decision 6 |
| 6 | Dry run by default; `--replace` to actually overwrite | The operation is irreversible, so it should be typed on purpose |
| 7 | A separate command, not automatic inside `add` | The result is inspectable before anything reaches Telegram |
| 8 | A file that still exceeds the threshold is reported | Splitting already works; silently re-encoding would be worse |

## 3. Behaviour

```
mediagram prepare <path|dir> [--replace] [--audio ger,eng] [--subs ger,eng]
```

1. Collect video files (the extensions `course::plan` already recognises,
   skipping our own `.faststart.` temporaries).
2. Probe each with `ffprobe`: streams, their types, languages and bitrates,
   plus container duration.
3. Plan: keep every video stream, keep audio and subtitle streams whose
   language is in the keep set, drop the rest. Estimate the result as
   `size - (dropped audio bitrate x duration / 8)`. Subtitle savings are not
   estimated: text tracks are small and their bitrate is usually absent.
4. Print a table of file, current size, tracks dropped, estimated size, and
   whether it will fit one part.
5. Without `--replace`, stop there.
6. With `--replace`, for each file that needs it:
   - `ffmpeg -i <src> -map ... -c copy <src>.prepared.mkv`
   - verify the output (§4)
   - rename over the original, which is atomic on one filesystem
   - on any failure, delete the temporary and leave the original untouched

A file with no droppable tracks, or already under the threshold, is skipped
with a reason.

## 4. Verifying before replacing

The original is only replaced once the new file passes every check:

| Check | Why |
|---|---|
| Output exists and is non-empty | ffmpeg can exit 0 having written nothing useful |
| Probes cleanly | A truncated file often still has a header |
| Has a video stream | A bad `-map` can drop the picture entirely |
| Keeps the expected audio languages | Proves the keep set was applied, not silently emptied |
| Duration within 1s of the source | Catches a truncated copy, the most likely silent failure |
| Smaller than the source | If it grew, something was misunderstood |

Any failure is reported per file and the run continues with the next one.

## 5. Module layout

| File | Role |
|---|---|
| `media/streams.rs` | ffprobe JSON to typed streams. The only IO |
| `media/prepare_plan.rs` | Pure: keep set, drop list, size estimate, verdict |
| `commands/prepare.rs` | Orchestration: walk, table, ffmpeg, verify, replace |

The planner being pure is what makes the dry-run table and the real run agree
by construction, which matters when the real run is irreversible.

## 6. Testing

- Planner (pure): a file with only wanted languages (skipped); one with seven
  droppable tracks; a file already under the threshold; a file that stays over
  after pruning; missing language tags; missing bitrates; zero duration.
- Stream parsing: fixture JSON from the real episode, including 42 subtitle
  tracks and a stream with no `bit_rate`.
- Verification: each check in §4 rejects the case it exists for.
- Replacement: on a temporary tree, the original survives a simulated failure
  and is replaced on success.

## 7. Risks

| Risk | Mitigation |
|---|---|
| Irreversibly dropping a wanted track | Dry run by default; the table names every dropped language before anything is written |
| A truncated output replacing a good original | Six checks in §4, duration being the sharpest |
| Interruption mid-replace | The temporary is written first and renamed last; an interrupted run leaves the original intact and a stray `.prepared.mkv` |
| Estimates being wrong | The estimate only decides what to attempt. The real size is measured after the copy, and a file that stays over the threshold is reported |

## 8. Out of scope

Re-encoding, hardware encoders, per-file interactive selection, and changing
container formats. If a library ever needs them, they are a separate feature
with their own decision about quality.
