# Phase 07 report: move-inline and backfill sending modes (code)

No Telegram operation was run; every test uses `FakeChannel`, and the channel-mode test serves fake bytes through the real router over a local `ByteSource`. Runbook runs are the lead's.

## What exists
- `subtitles move-inline [--dry-run] [--no-push]`: per set with inline `assets` subtitle rows and no bundle, builds the bundle (track n = n-th row by raw `lang`; lang normalised to `de`/`en`; labels German/English/Subtitles; source `sidecar`, codec `vtt`), `send_document`, `download`, compares sha256 and decoded tracks, then `record`. Mismatch: nothing recorded, the stray message deleted best-effort, reported; stop after three.
- `subtitles backfill <FOLDER>... [--dry-run] [--accept-fallback FILE]... [--redo SET]... [--no-push]`: folder survey (07a matcher, unchanged, now `dry_run::survey`) then `send_matches`: size matches go to `attach`; fallback matches only when the file is named.
- `subtitles backfill --channel [--mkv] [--limit N] [--redo SET]... [--dry-run]`: `candidates` (complete, kind != doc, slang has de/en, no `subtitle_files` row, no `subs-none:<set>`; MP4 first; anything not MP4 only with `--mkv`; redo sets first), loopback server (`loopback.rs`: `serve::routes::router` on `127.0.0.1:0`, read-only index, `TelegramSource` from the sending `Tg`), `attach(Input::Url(.../sets/<id>/stream))`. No de/en text track: `subs-none:<set>`. Five failures in a row stop the run.
- Shared (`session.rs`): refuses `upload_slots` > 1, holds the upload lock for the run, one `Tg`, 2 s pace, Ctrl-C flag checked between sets, publish every 100 recorded sets and at the end (also after a failed body, so recorded work is published), `--no-push`.
- `send_set`: existing bundle means skipped unless redo; redo skipped if the bundle was recorded at/after the run start; redo clears `subs-none`, records the new bundle, then deletes the old message (warns if that fails; attach returning nothing leaves the old one).
- Seam: `ChannelRemote::delete_message` (Telegram impl via `with_retry` + `delete_messages`, the call `remove` uses; FakeChannel removes the message). FakeChannel gained `corrupt_downloads`.
- `index::assets::{inline_subtitles, sets_with_inline_subtitles}`, `index::subtitles::bundle_uploaded_at`, `subtitles::track_label` re-export.
- Docs: README (new section), `docs/system-architecture.md`. CLI help text updated.

## Tests (all green; clippy -D warnings clean)
- `tests/subtitles_move_inline.rs`: rows gone only after matching read-back, numbering (deu before eng) and summaries kept; mismatch leaves rows; stops after three; existing bundle untouched; dry runs (move-inline, channel) leave assets/subtitle_files/meta unchanged; slot check.
- `tests/subtitles_backfill.rs` (ffmpeg-gated except the first): bundled set skipped without reading the source; fallback unsent unless named; redo replaces and deletes the old message, second redo in the run skipped.
- `tests/subtitles_backfill_channel.rs`: candidate order/filters (MP4 before MKV, `subs-none`, bundled, doc, non-de/en, redo); end-to-end through the real router and a fake byte source: bundle from served bytes, `subs-none` set for a file without de/en, not a candidate afterwards.

## Decisions and deviations
1. Non-MP4 containers (not just MKV) need `--mkv`; the spec named only MKV.
2. `--mkv`/`--limit` without `--channel` are refused by hand: clap `requires` on a bool flag with its implicit default was not enforced.
3. `--limit` counts sets actually attempted (not skipped ones).
4. Folder-mode fallback is sent only via `--accept-fallback`; `--accept-fallback` conflicts with `--channel`.
5. Resume: a run's "start" is per process; a restart skips sets by their existing bundle (non-redo) and re-does a redo list from the top.
6. `cargo fmt` is not clean repo-wide (I ran it once, reverted all unrelated files); only `send_document` signatures in touched files were reformatted.
7. Coordinator notes honoured: no per-track retry assumed for `Input::Url`. The phase 06 review fixes were not yet on the branch (`git merge` said up to date); the lead should merge again and re-run clippy/tests.

## Concerns
- `tests/upload_session.rs` flaked once in a full run (known lock-fd inheritance, phase 06); two further full runs were clean.
- Ctrl-C: tokio's handler is installed after the first press, a second press does nothing; the run ends after the set in hand.
- Channel mode assumes `meta` `subs-none:*` keys are local-only (they are not in `merge`); the other machine never runs `--channel`, per the runbook.

## Review fixes
Rebased onto main (phase 06 squashed as cd9701c7); `-rw_timeout` 60 s applies to the channel reads (ffprobe and ffmpeg both get `network_args` for the loopback URL).
- H1: for `Input::Url` ffmpeg runs with `-xerror`; `attach` fails (nothing recorded, no `subs-none`, counted failed) when any wanted stream's text is missing; a lone stream that fails as not UTF-8 gets one `-sub_charenc CP1252` re-read (several streams: failure, since the culprit is unknown). ffprobe failure already propagated as an error. `send_set` also treats an attach that only counted unread picture tracks as Nothing (it used to be counted as bundled, and on redo would have queued the old message for deletion). Test: a source that errors at an offset (halfway, and just short of the end) gives failed 1, no bundle, no mark. The tiny fixture also fails without `-xerror` (ffmpeg or ffprobe errors on it), so the test guards the outcome, not the flag alone. Ceiling: an oversized (over 4 MiB) track on a URL is now a repeating failure.
- M1: ffmpeg/ffprobe children get their own process group and `kill_on_drop`; a second Ctrl-C exits 130.
- M2: replaced bundle messages are queued in the session and deleted after a successful publish; without one (`--no-push`, failed publish) they are listed and kept. There is no command that deletes a single message, so the list is for deleting by hand.
- Low: early stops (3 mismatches, 5 failures in a row) end with an error; move-inline publishes once at the end (`publish_every` is a session parameter); redo skips and warns when the old bundle's chat differs from the current channel; channel candidates exclude sets with inline subtitle rows; the publish error is printed even when the body also failed.

## Changelog entry
**Added**

- `mediagram subtitles move-inline` gives each lesson whose subtitles sat inline in the index a `#mlib-subs` bundle (track numbers unchanged) and removes the rows only after the bundle, read back from the channel, matches what was sent; three mismatches stop the run.
- `mediagram subtitles backfill` now sends. From folders it matches files to uploaded sets by size (a name-and-duration match is sent only when its file is named with `--accept-fallback`); `--channel` reads the German and English text tracks from the copy in the channel through a loopback server on the same Telegram session (MP4 first, `--mkv` for the rest, `--limit N` per run) and remembers sets that have none; `--redo <set>` replaces a bundle and deletes the old message. Runs hold the upload lock, pace sends, publish every 100 sets and stop after the set in hand on Ctrl-C; `--dry-run` writes nothing.
- `ChannelRemote::delete_message`.
- A channel read that ends short is a failure, not a short bundle or a `subs-none` mark; `--redo` deletes the old bundle message only after the index naming the new one is published.
