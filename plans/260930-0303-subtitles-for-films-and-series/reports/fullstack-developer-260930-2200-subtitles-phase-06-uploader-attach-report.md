# Phase 06 report: uploader attaches subtitle bundles at set completion

## Executed
Plan `260930-0303-subtitles-for-films-and-series`, phase 06. Status: done except the real-title end-to-end upload (lead, with the user). No Telegram operation was run; every test uses `FakeChannel`.

## What exists now
- `subtitles::attach(conn, remote, set_id, &Input) -> Result<Option<Attached>>`, `Input::{File(PathBuf), Url(String)}`. ffprobe/ffmpeg get the path or the URL in the same argument position; sidecar discovery runs for `File` only. `attach_and_report` is the never-failing wrapper `finish` calls (prints `subtitles: German (Forced), German, …` or `warning: no subtitles attached to <id>: …`).
- Modules (each < 200 lines): `select.rs` (per-stream rules, language/forced/SDH claims, picture count), `arrange.rs` (density, duplicates, order, labels), `sidecars.rs` (strict-token discovery, `.vtt` over `.srt`), `extract.rs` (one ffmpeg pass into a `TempDir`, per-track fallback when the pass fails, CP1252 SRT, 4 MiB cap), `attach.rs`.
- `index/subtitles.rs`: `record` (one tx: replace tracks, upsert file row with `uploaded_at = now`, delete inline subtitle `assets` rows, `owe_publish`), `bundle_message`.
- `finish` (`upload/session/item.rs`): takes the remote from `link.open()`, reads `original_of` (fallback `source_of`) before `run_set`, attaches after `complete` unless kind `doc`, then `report_deletion` (so `--delete-source` never outruns extraction).
- `probe`/`streams`: `Stream` gains `title`, `default`, `forced`, `hearing_impaired`.
- `lifecycle`: `orig:<set>` key, `record_original`, `original_of`; `forget` clears it.
- `course/sidecars.rs`: summary half only; `Sidecars` has no `subtitle` now.
- `remove`: `plan_removal(set, parts, bundle: Option<i64>)`; `commands/remove.rs` passes `bundle_message`. Rows cascade.
- Density constants: `FORCED_MAX_CUES_PER_HOUR = 120.0` (spec guess was 10), `FORCED_MAX_SHARE_OF_DENSEST = 0.25`. See `forced-cue-density-260930.md` (78 tracks measured, Boardwalk Empire S1-S5 + Black Sails S4; shows/seasons only).
- Docs: `docs/mlib-spec.md` (what the uploader writes), `docs/system-architecture.md` (flow + module map), `README.md` (sidecar naming).

## Deviations from the spec
1. `Source.original` (plan.rs) not added: `record_document.rs` also builds `Source` and is outside my ownership. `prepare_set.rs` records the original inside the existing `also` closure (same transaction as the set) instead. `plan.rs` is untouched.
2. Density threshold 120/h instead of 10/h, from measurement (10/h misses 20 of 52 German Boardwalk tracks).
3. The spec's "four picture tracks counted" for Band of Brothers: counted as de/en picture tracks only (other-language pictures are not a gap we can name). Fixture is hand-authored to the survey shape, not a real ffprobe dump.
4. `tests/support/media.rs` added (+ `pub mod media;`) for ffmpeg-made mkv fixtures; `tests/support/channel.rs` untouched.

## Verification
- `cargo clippy --all-targets --all-features -- -D warnings`: clean.
- `cargo test --all` (CARGO_INCREMENTAL=0): all green, ffmpeg-gated tests ran on this box. New: 48 unit tests in `subtitles::*` and `index::subtitles`, `tests/subtitles_attach.rs` (7), `tests/upload_attaches_subtitles.rs` (4), plus lifecycle/remove additions.
- Not run: web/android suites (out of ownership), the real upload.

## Concerns for the lead
- **Orphan bundle message on redo:** `attach` replaces the index row but `ChannelRemote` has no delete, so a `--redo` leaves the previous bundle message in the channel. Phase 07's redo should delete the old message (id from `bundle_message` before calling `attach`).
- **Race on an already-held planned set:** when `add`'s foreground session finds its planned set completed by another process, it may delete the source while that other process is still extracting. Narrow (two processes on one set); not addressed.
- **Junk-source noise:** existing session tests now trigger an ffprobe on junk bytes and print a warning; harmless.
- A near-silent film with a single unflagged, untitled full German track would be labelled forced (documented ceiling in `arrange.rs` and the density report).
- Nothing in this crate yet deletes the old `assets` subtitle rows of already-complete lessons; that is phase 07's move-inline.

## Review fixes (second commit)
- B1: `upload_session.rs` `planned()` builds `Kind::Doc` sets; 15/15 runs of the binary green.
- M1: a stream whose ffmpeg run says "Invalid UTF-8" (exit 0, or exit 69 when most lines are such) is re-read alone with `-sub_charenc CP1252` before `-i`; other streams are never touched by it. File inputs only. Test patches a CP1252 SubRip track into an mkv beside a UTF-8 one.
- M2: no per-track retry for `Input::Url` (a set missing a track is healed by the backfill); the per-track retry stays for files, where a re-read is local. `-rw_timeout` 60 s (`NETWORK_RW_TIMEOUT_US`) on ffprobe and ffmpeg for http(s) URLs only (a `file://` URL does not take it).
- L1: `complete` keeps `orig:<set>`; `finish` clears it after `attach_and_report`; the already-complete delete branch keeps the file while it is recorded. Tested both ways.
- L2: a record failure names the orphaned message id. L3: picture-only files print the picture count.

## Changelog entry
**Added**

- At set completion (after the last part is up, before `--delete-source`), the uploader now reads a film's, episode's or lesson's German and English text subtitles and sends them as one `#mlib-subs` bundle per set, recorded in `subtitle_files` and `subtitle_tracks` with a publish owed. Tracks come from the file the person named, read in one ffmpeg pass into a private temp folder: embedded `subrip`, `ass`/`ssa`, `mov_text`, `webvtt` and `text` streams, and sidecars named `<video>[ ._-]<language and flag words>.vtt|.srt` (`de|deu|ger|german|deutsch`, `en|eng|english|englisch`; `forced`, `sdh`, `cc`, `hi`; any other word means another video's file; `.vtt` beats `.srt`; non-UTF-8 SRT is read as Windows-1252). Forced is the flag, a `forced`/`erzwungen` title, or an embedded track with no forced flag or forced title (and not SDH) and at most 120 cues an hour (measured: signs-only German tracks run 1-86, full tracks 442 and up) or a quarter of its language's densest track; SDH is the flag or a title naming SDH, CC, hearing or hörgeschädigt. Picture subtitles are skipped and counted. A failure costs only the bundle, never the upload.
- `subtitles::attach` takes a file or a URL, so the backfill can read the uploaded copy through the player's loopback server; sidecars are found for files only.
- Planning records the file the person named (`orig:<set>`) because completion deletes the remux and forgets the source.
- `remove` deletes a set's bundle message along with its parts.

**Changed**

- A lesson's `.vtt` no longer lands in `assets` at planning; it travels in the bundle like every other subtitle. Summaries are unchanged.
