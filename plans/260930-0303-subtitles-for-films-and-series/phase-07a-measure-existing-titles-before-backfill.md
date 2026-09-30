# Phase 07a — Measure what the existing library can gain, before any film/series backfill

## Context links
- Red team: assumption-destroyer F1 (Critical: of 2,834 sets whose `slang` lists de/en, **63** have a source reachable from this machine; **0 of 2,028** mp4 sets match; the old remux kept **no** subtitle stream), F5 (fallback never compared the name; drive roots), failure-mode F1 (shrinking candidate pool).
- User decision 2026-09-30: **measure first** — matcher dry run + header probe of the uploaded copies, on both uploader machines; phase 07's film/series scope is decided from the counts; lesson move-inline stays in scope.
- Channel follower copy at review time: subtitled sets in mp4 2,016 (2.43 TB), in mkv 126 (0.34 TB) (user's figures). Later the same day (`sqlite3 -readonly ~/.cache/mediagram-channel-index/current/library.db`): ep mp4 2,073 (2.44 TB), ep mkv 133 (0.35 TB), movie mkv 680 (9.24 TB), movie mp4 12; 2,898 complete movie/ep/docu sets list de/en. The channel keeps growing — take counts from the run, not from here.
- Walk and parse: `crates/mediagram/src/media/video_files.rs:15-31` (`collect_videos`), `crates/mlib-spec/src/filename.rs:72` (`parse_filename`: show/title, SxxEyy, year); probe: `media/streams.rs:48-83` (kind, language, codec — enough to count text tracks), `media/prepare/plan.rs:165` (`PICTURE_SUBTITLES`).
- Header probe: `reports/uploaded-stream-headers.sh` (phase 01) over `mediagram serve`.
- Sources are deletable by design (`add --delete-source`, `commands/add.rs:18`; `prepare --out/--replace` rewrites files, `media/prepare/paths.rs:31-47`).
- Mount check: `<second data drive>` answered `Input/output error` on 2026-09-30 — confirm mounts before a run.

## Overview
Priority P1. Effort 0.5d + two operator runs. Version: next **minor** (new `subtitles backfill --dry-run`). Status: pending.
Depends only on existing code and phase 01's script: lands right after 01, in parallel with 02–05 and before 06, so the user decides phase 07's scope early.

## Key decisions
- **The matcher is final here; phase 07 reuses it unchanged.**
  - The pool is all complete `movie|ep|docu` sets, whether or not they already have a bundle.
  - Exact size match with a unique set → matched. A size shared by several sets → ambiguous, never sent.
  - The fallback applies only to an `.mp4` whose size equals **no** set's `total`. It needs the parsed name to agree (show + SxxEyy for episodes, title and year if present for films), duration within ±2 s, and exactly one candidate. It is listed apart from the matches and never sent without an explicit per-file opt-in (phase 07).
  - Two files claiming one set → both are rejected and listed.
- **Dry run means no ffmpeg extraction and no Telegram:** a header `ffprobe` of each matched source counts its de/en text tracks and its picture-only tracks.
- **Header probe of the uploaded copies:** phase 01's script gets a `subs` mode and reads only the `moov`/Matroska headers through `serve`.
- **Both runs on both machines**, as decided, each after `pull-index`. The header probe covers the whole channel from either machine, so the second run is a cross-check whose totals should agree.

## Requirements
- `mediagram subtitles backfill <FOLDER>... --dry-run` (the flag is required until phase 07 adds sending):
  - Walks the folders; matches; probes the matched sources' headers.
  - Prints a TSV per file: `file  verdict(matched|fallback|ambiguous|conflict|unmatched)  set_id  kind  show/title  de_en_text  picture_only`.
  - Prints totals per kind/container and in bytes: matched sets; matched sets whose source has de/en text; fallback candidates; ambiguous; conflicts; unmatched files.
  - Also lists sets with de/en in `slang` that no file matched.
- `reports/uploaded-stream-headers.sh subs`:
  - Candidates are complete `movie|ep|docu` sets whose `slang` has `de` or `en`.
  - Per set: the uploaded copy's de/en **text** subtitle tracks, its picture tracks, and its container.
  - Output: TSV + totals.
- Outputs (plan dir):
  - `reports/backfill-dry-run-<machine>-<date>.tsv`, `reports/uploaded-subs-<machine>-<date>.tsv`;
  - `reports/backfill-measurements-<date>.md` with the decision table below; `reports/rollout-log.md` links it;
  - the media folders each machine actually used. They are the runbook's list for phase 07, never drive roots, never course/lecture trees (`andre/Videos`, `courses`, Udemy/Pluralsight) or `VR`.
- The decision table for the user, one row per bucket, each with sets, TB to read and machine:
  | Bucket | Meaning | Option |
  |---|---|---|
  | S | source found, source has de/en text | backfill from source (phase 07) |
  | U | no source, uploaded copy still has de/en text | extract from the Telegram copy (whole-file download through `serve`), or skip |
  | N | neither | no subtitles possible (known gap) |
  | P | picture-only | known gap (no OCR) |

## Architecture
```
folders ─collect_videos→ file ─size→ unique set? ─yes→ matched → ffprobe header → de/en text? picture?
                              └no, mp4, size ∉ totals → name+SxxEyy/year agree, ±2 s, 1 candidate → fallback (listed only)
channel sets (slang de/en) ─serve /sets/<id>/stream─ ffprobe header (moov / Tracks) → uploaded text tracks
both TSVs → buckets S/U/N/P per machine → user decides phase 07 scope
```

## Related code files
- Create: `crates/mediagram/src/commands/subtitles/{mod.rs,match_source.rs,match_source_tests.rs,dry_run.rs}`.
- Modify: `crates/mediagram/src/commands/mod.rs`, `crates/mediagram/src/cli.rs`, `crates/mediagram/src/media/prepare/plan.rs` (`PICTURE_SUBTITLES` → `pub(crate)`, one line; phase 06 then uses it as is), `plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh` (add `subs` mode), `docs/project-changelog.md`.
- Disjoint from phases 01 (`media/remux.rs`) and 02 (`index/`, `channel_index/`). Runs after 01 (it extends 01's script) and lands before 06 (06 reads the `PICTURE_SUBTITLES` it exposes).

## Implementation steps
1. `match_source.rs` (pure) + tests:
   - exact unique match;
   - a size shared by two sets → ambiguous;
   - a set that already has a bundle is still matchable;
   - an mp4 whose size equals some `total` → no fallback;
   - fallback accepted only on name + SxxEyy (episode) or title + year (film) + ±2 s + unique;
   - an unrelated lecture mp4 of the same duration → not matched;
   - two files for one set → conflict.
2. `dry_run.rs`: walk, match, header probe of sources, TSV + totals.
3. CLI (`subtitles backfill --dry-run`, flag required).
4. Script `subs` mode.
5. `scripts/check.sh`; bump by pattern; changelog.
6. Operator runs:
   - **This machine:** reinstall, `pull-index`, check the mounts, then run the dry run over the media folders (`<media drive>/<series folder>`, `<NAS share>`, the film/series folders on `<second data drive>`) and `uploaded-stream-headers.sh subs`.
   - **Other machine:** the same with its media folders (under `<other machine's media folder>`).
   - Fill the decision table in `reports/backfill-measurements-<date>.md`, link it from `reports/rollout-log.md`, and ask the user for phase 07's film/series scope.

## Todo
- [ ] matcher + tests
- [ ] dry-run command
- [ ] script `subs` mode
- [ ] check.sh, manifests, changelog
- [ ] runs on this machine (TSVs saved)
- [ ] runs on the other machine (TSVs saved)
- [ ] decision table in `reports/`, linked from `reports/rollout-log.md`; user's scope recorded in phase 07

## Success criteria
- Both TSV pairs and the measurements report saved under `reports/`, linked from `reports/rollout-log.md`; the two header-probe totals agree (± sets pushed in between).
- The user has chosen phase 07's film/series scope (or "none"), recorded in phase 07.

## Tests
| Level | What |
|---|---|
| Unit | matcher cases above |
| Manual | dry runs + header probes on both machines |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| Walking drive roots floods the fallback list with lectures | Med × Low | Media folders only; fallback needs a name match; listed, never sent |
| Header probes read more than headers | Low × Low | `-probesize 65536 -analyzeduration 0`; mp4 uploads are faststart, Matroska tracks sit at the head |
| A flaky mount hides sources | Med × Low | Mount check first; re-run is cheap |
| Running beside an upload | Low × Med | Script refuses while an upload process runs; the dry run sends nothing |

## Security
Read-only on the index (`sqlite3 -readonly`) and on sources; serve is loopback; nothing is sent or pinned.

## Rollback
Nothing persisted but report files.

## Next
Phase 07: film/series scope from these counts; lesson move-inline regardless.
