# Phase 06 — Uploader: at set completion, extract tracks + sidecars and send one bundle

## Context links
- Where it runs: `crates/mediagram/src/upload/session/item.rs:80-147` (`Session::finish`): `link.open()` at `:105-111` (returns transport **and** remote), `run_set` at `:112-121`, `complete` at `:136`, `report_deletion` at `:139-141`; document sets come from `record_document_set` (`:67-73`, kind `doc`). `add` plans in the foreground and finishes in a background `finish-set` process (`commands/add.rs:18-31`); `add-show`/`add-course`/`resume` finish inside their session.
- What completion removes: `upload/pipeline.rs` deletes the faststart remux (`completed.temp`); `index/lifecycle.rs:55-69` forgets `source:`/`tmp:` (keys `:77-83`, `record_source` `:32-44`, `forget` `:72-75`); `upload/plan.rs:14-20,47` (`Source`), `upload/prepare_set.rs:138-144` (records the source; summaries stored at planning).
- Probe: `media/probe.rs:24-41` (no `disposition`, no `title`), `media/streams.rs:22-32,52-83`, `media/classify.rs:49-81` (`lang_code`), `media/prepare/plan.rs:165` (`PICTURE_SUBTITLES`, made `pub(crate)` by phase 07a).
- Sidecars today: `course/sidecars.rs:1-121` (exact `<stem>.vtt` `:39`, both `.vtt` and `.srt` sit beside lessons `:3-7`, `.faststart` strip `:51-58`, lang = first audio or `und` `:110`).
- Send: `ChannelRemote::send_document` (phase 02). Record: new `index/subtitles.rs`. Remove: `remove/plan.rs:44-60`, `commands/remove.rs:44`.
- `tempfile` is a workspace dependency, today only a dev-dependency (`crates/mediagram/Cargo.toml:55-56`).
- Samples: `reports/codebase-survey-subtitles.md` (Arcane: SDH only in the title; Band of Brothers: forced SRT + PGS); Boardwalk Empire S1–S5 (`<media drive>/<series folder>/…`): untitled, unflagged German ASS with 2–12 cues per ~55-minute episode — the forced shape of this library (red team: assumption-destroyer F2).
- Red team: scope-critic F1/F3, failure-mode F2, assumption-destroyer F2/F6/F7.

## Overview
Priority P1. Effort 1.5d. Version: next **minor**. Status: pending. Code depends on phases 02 (`send_document`, tables) and 07a (`PICTURE_SUBTITLES` visibility).
**Gate (rollout):** phases 03, 04 and 05 are live on every client (the web player has been restarted, and the tablet and TV box are installed). Both uploader machines are on phase 02 or later, verified by the caption of their own push.

## Key decisions
1. **Where it runs:** `subtitles::attach` is called in `finish` once `complete` is true, before `report_deletion`, and never for kind `doc`. It runs in the process that uploads, which for `add` is the background `finish-set` process. Nothing happens at planning; there is no staging folder, no sweep and no lock. Extraction reads only complete sets, and the terminal never waits for it.
2. **Which file:** the original the person named. Completion has already deleted the remux and forgotten the source keys, so planning now records the original too:
   - `Source.original` is stored as meta `orig:<set>`; `lifecycle` owns the key and `forget` clears it.
   - `finish` reads it before `run_set`.
   - Sets planned by older builds fall back to `source_of`. If that is a remux that has since vanished, `attach` warns and the backfill heals the set later.
3. **Failure never fails the upload:** any error gives one warning, and the set stays complete without a bundle.
   - A crash between completion and recording has the same result.
   - Recovery is `subtitles backfill <folder>` or `--redo <set>` (phase 07). That send mode stays in scope whatever 07a decides about historical titles.
4. **One ffmpeg pass per file:** `ffmpeg -v error -nostdin -i ORIGINAL -map 0:<i> -c:s webvtt -f webvtt <tmp>/<i>.vtt …`.
   - Outputs go into a `tempfile::TempDir`, never beside the source, where a crash would leave files the sidecar rule adopts. `tempfile` moves from dev-dependencies to dependencies.
   - Handles subrip, ass/ssa (styling dropped), mov_text, webvtt and text.
   - Picture codecs (`PICTURE_SUBTITLES`) are skipped and counted as a known gap.
5. **Which tracks** (`select.rs`, pure):
   - **Language:** from the tag (`lang_code`), else from the title (`german|deutsch` → de, `english|englisch` → en), else the track is skipped. Only `de`/`en` are kept. Titles matching `comment|kommentar` are skipped.
   - **Forced**, in this order:
     1. `disposition.forced`;
     2. a title matching `forced|erzwungen`;
     3. cue density, for embedded de/en tracks without an explicit flag or title: `FORCED_MAX_CUES_PER_HOUR = 10.0` (runtime from the probe) or `FORCED_MAX_SHARE_OF_DENSEST = 0.25` (of the same language's densest track).
     Sidecars follow their name tokens only, because lesson transcripts can be short.
   - **SDH:** `disposition.hearing_impaired`, or a title matching `\bSDH\b|\bCC\b|hearing|hörgeschädigt`. Forced wins when both are claimed.
   - **Duplicates:** at most one track per (lang, forced, sdh). A sidecar beats an embedded track, a `default` embedded track beats a later one, and a `.vtt` sidecar beats an `.srt`, so a lesson with both converts nothing.
6. **Sidecars:** files in the video's folder named `<stem>`, then optional `[ ._-]`-separated tokens, then `.vtt` or `.srt`.
   - Every token must be a language (`de|deu|ger|german|deutsch`, `en|eng|english|englisch`) or a flag (`forced`, `sdh`, `cc`, `hi`). Any other token means the file belongs to another video, so `Lesson 1` never takes `Lesson 10.srt`.
   - No language token → the first audio language, else `und`. Other languages are skipped with a warning.
   - `.srt` is converted to WebVTT by ffmpeg, with `-sub_charenc CP1252` when the file is not UTF-8. `.vtt` is kept when it is valid UTF-8 and starts with `WEBVTT`.
7. **Labels:** "German", "German (Forced)", "English (SDH)", and "Subtitles" for `und`.
8. **Summaries** are unchanged: stored at planning by `course/sidecars.rs`.

## Requirements
- `probe.rs`: `RawStream.disposition {default, forced, hearing_impaired}`, `RawTags.title`; `Stream` gains `title`, `forced`, `sdh`, `default`.
- `crates/mediagram/src/subtitles/` (new; each ≤ 200 lines): `select.rs` (rules, density, labels), `sidecars.rs` (discovery), `extract.rs` (one pass, SRT conversion, VTT check, 4 MiB per track), `attach.rs` (`attach(conn, remote, set, original) -> Result<Option<Attached>>`: probe → sidecars → select → extract → density → bundle → `send_document` → record), `mod.rs`.
- `index/subtitles.rs` (new): `record(conn, set_id, file_ref, tracks)` in one transaction — replace tracks, upsert the file row (`uploaded_at = now`), delete the set's inline `assets` subtitle rows, `pins::owe_publish`; `bundle_message(conn, set_id)`.
- `lifecycle.rs` `orig:` key + `original_of`; `upload/plan.rs` `Source.original`; `prepare_set.rs` passes `&new.file`.
- `item.rs`: take the remote from `link.open()`; read `original_of` before `run_set`; after `complete`, `attach` unless kind `doc`; print "subtitles: German (Forced), German, English (SDH)" or the warning.
- `course/sidecars.rs`: summary half only (subtitles no longer land in `assets`).
- `remove`: `plan_removal` also deletes the set's bundle message.
- Docs: `docs/mlib-spec.md` (what the uploader writes), `docs/system-architecture.md` (upload flow), `README.md` (sidecar naming), changelog.

## Architecture
```
add / add-show / add-course / resume
  planning (foreground): inspect(original) → resolve → remux? → record_planned(+ orig:<set>, summary)
  finish (upload process): original_of → run_set → complete
     └ kind ≠ doc: attach(original): ffprobe → sidecars → select → 1 ffmpeg pass (TempDir) → density
          → bundle → remote.send_document("#mlib-subs v=1") → index/subtitles::record (tx, publish owed)
     └ report_deletion (after attach, so --delete-source never outruns extraction)
  session end → publish (existing)
remove <set> → part messages + bundle message deleted; rows cascade
```

## Related code files
- Modify: `crates/mediagram/Cargo.toml` (`tempfile` to dependencies), `crates/mediagram/src/{lib.rs,media/probe.rs,media/streams.rs,upload/session/item.rs,upload/plan.rs,upload/prepare_set.rs,index/lifecycle.rs,index/mod.rs,course/sidecars.rs,remove/plan.rs,commands/remove.rs}`; tests using `Stream` literals or `Source`.
- Create: `crates/mediagram/src/subtitles/{mod,select,select_tests,sidecars,sidecars_tests,extract,attach,attach_tests}.rs`, `crates/mediagram/src/index/{subtitles,subtitles_tests}.rs`, ffprobe JSON fixtures (Arcane, Band of Brothers, Boardwalk shapes) under `crates/mediagram/tests/fixtures/`, `plans/260930-0303-subtitles-for-films-and-series/reports/forced-cue-density-<date>.md` (measurements).

## Implementation steps
1. Probe + `Stream` fields; parse tests on the three fixtures.
2. `select.rs` + tests:
   - Arcane → German (Forced), German, English (SDH).
   - Band of Brothers → German (Forced) only, with 4 picture tracks counted.
   - Boardwalk → German (Forced) by density; an untitled, unflagged full German track (~600 cues/h) → German.
   - An untagged, untitled track → skipped; commentary → skipped; a duplicate triple → the `default` track wins; a sidecar beats an embedded track; `.vtt` beats `.srt`.
3. Measure density on at least five more reachable German-dub sources (Boardwalk seasons, a flagged release, a full German track). Write the numbers into `reports/forced-cue-density-<date>.md` and adjust the named constants if needed.
4. `sidecars.rs` + tests: `x.vtt`, `x.srt`, both together (`.vtt` kept, no conversion), `x.de.srt`, `x.en.forced.srt`, `x German.srt`, `x.English.SDH.srt`, `x.fr.srt` (skipped), `x 10.srt` for stem `x 1` (not taken), `.faststart` stem.
5. `extract.rs`, with ffmpeg-gated tests:
   - an mkv with subrip + ass gives two VTTs from one run;
   - a CP1252 SRT with umlauts gives UTF-8 VTT;
   - outputs appear only inside the temp dir.
6. `index/subtitles.rs`; `lifecycle` `orig:`; `attach.rs` with `FakeChannel`:
   - sent once, recorded, inline rows deleted, publish owed;
   - a send failure leaves no row and the upload outcome unchanged.
7. `item.rs` wiring. Tests: a document set is skipped; a remuxed set extracts from the original after the remux is gone; `--delete-source` deletes only after `attach`.
8. `remove` deletes the bundle message.
9. `scripts/check.sh`; bump by pattern; docs; changelog.
10. Operator (lead): reinstall on both uploaders. Then add one real title end to end, chosen with the user: a new upload, preferably shaped like Boardwalk (an unflagged German sign track).

## Todo
- [ ] probe/streams fields
- [ ] select rules incl. density constants + labels + tests
- [ ] density measured on ≥ 5 sources; report written
- [ ] sidecar discovery incl. `.vtt` over `.srt`
- [ ] one-pass extraction in a temp dir
- [ ] record, `orig:` key, `attach`, `finish` hook, remove
- [ ] check.sh, docs, manifests, changelog
- [ ] end-to-end upload of one real title

## Success criteria
- `scripts/check.sh` green; ffmpeg-gated tests run on this box.
- `mediagram add` returns to the prompt with no extraction delay; the background `finish-set` log shows "subtitles: …" after "set … added".
- End-to-end (the channel's first bundle; also the bundle-path check for phases 03/04):
  - **Web (API only):** `curl -s localhost:8770/api/sets | jq '.[] | select(.setId=="<id>") | .subtitles'` lists the tracks. `…/subtitles/<n>.vtt` returns WebVTT, and a repeat request is answered from memory.
  - **Tablet and TV box, test profile:**
    - With German audio, the forced lines show while subtitles are "Off".
    - The picker lists only the regular tracks.
    - CC and the captions key switch German on and off; with German off again, the forced lines are back.
    - After a preload of that title, in airplane mode, subtitles still load.
- `mediagram remove --dry-run <id>` lists the bundle message.

## Tests
| Level | What |
|---|---|
| Unit | select (three shapes, density), labels, sidecar names, record transaction, attach with fake channel, `finish` ordering |
| ffmpeg-gated | one-pass extraction; CP1252 SRT; temp-dir only |
| Manual E2E | one real upload across web/tablet/TV |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| Extraction adds a full read per completed set | Certain × Low | It happens in the upload process after the upload finishes, never in the foreground, and only when a wanted text track exists (probe first). For NFS sources (<NAS share>) the extra read goes over the network |
| Density threshold mislabels a sparse full track as forced | Low × Med | Explicit flags and titles are checked first; the constants are named and measured (step 3); the relative rule needs a denser same-language track |
| Wrong sidecar attached | Low × Med | Strict token rule; tests with numbered neighbours |
| Crash between completion and record | Low × Low | Set complete without a bundle; `backfill`/`--redo` heals it (phase 07) |
| FLOOD_WAIT on sends | Low × Low | One send per completed set; existing flood-wait retry |

## Security
Paths come from the walk, not from file contents; ffmpeg runs without a shell; temp files live in a private temp dir; sizes are capped before storing; the caption carries only the set id.

## Rollback
Revert the build. Recorded bundles stay valid and readers keep reading them; the `orig:` meta keys are ignored by older builds.

## Next
Phase 07 moves the lesson subtitles and runs the backfill in the scope the user picked from 07a's measurements (07a has run by then).
