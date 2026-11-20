# Phase 02 — `add-audiobook` in the uploader

**Context:** spec § Uploader. Model: `crates/mediagram/src/commands/add_docu/mod.rs` (`run_file`), `upload/session/identity.rs`.
**Status:** todo. **Depends on:** Phase 01.

## Requirements

- Cover art is never the video: a stream with `disposition.attached_pic == 1` is skipped when `inspect` picks the video stream. A book then has `vcodec`, `width`, `height`, `quality` all `None`, and its caption carries `hdr: None` (a file with no video has no dynamic range).
- `mediagram add-audiobook <path> [--dry-run] [--no-push]`: `path` is one `.mp3` or a folder walked recursively for `*.mp3` (case-insensitive), sorted. Per file, in walk order:
  - title = format tag `title` (trimmed), else the file stem with a trailing ` [digits]` removed. Never number-split (`"99 Mind Hacks …"` keeps its `99`).
  - category = format tag `genre`, trimmed; none when absent or blank. Written with `edit::category::planned(Kind::Book, Some(title), genre)` + `edit::category::write` before upload. A genre `normalise` refuses (e.g. `"Other"`) is reported for that file and the file uploads without one.
  - poster = the bytes of the `attached_pic` stream, stored with `index::artwork::put(conn, &title_art_key(title), mime, bytes)`; mime `image/jpeg` for `mjpeg`, `image/png` for `png`; a file without one gets none. Read with `ffmpeg -v error -i <file> -map 0:<index> -c copy -f image2pipe -` (stdout bytes).
  - uploaded as one `Kind::Book` set through the shared upload `Session`, one item per file, one session for the whole walk.
- Re-running finds a complete book by kind + title and reports it held; a pending one is resumed by the session as any pending set is. No book is uploaded twice.
- `--dry-run` prints one line per file: `<title> | <genre or -> | cover: yes/no`, then a total, and uploads nothing.
- The source file is never deleted (`delete_source: None`) and never remuxed (it is not MP4).

## Files

- Modify: `crates/mediagram/src/media/probe.rs` (`RawDisposition.attached_pic: u8`; `RawFormat.tags: Option<RawFormatTags>` with `title`, `genre`; ffprobe already runs `-show_format`, check it includes format tags)
- Modify: `crates/mediagram/src/media/inspect.rs` (skip `attached_pic` streams)
- Create: `crates/mediagram/src/media/audiobook_tags.rs` (≤200 lines: `pub struct AudiobookTags { pub title: String, pub genre: Option<String>, pub cover: Option<CoverStream> }`, `pub struct CoverStream { pub index: u32, pub mime: &'static str }`, `pub async fn read(path: &Path) -> Result<AudiobookTags>`, `pub async fn cover_bytes(path: &Path, cover: &CoverStream) -> Result<Vec<u8>>`, `pub fn title_from_file_name(stem: &str) -> String`)
- Modify: `crates/mediagram/src/upload/new_set.rs`: replace `docu: bool` + `docu_title: Option<String>` with `standalone: Option<Kind>` (`Some(Kind::Docu)` or `Some(Kind::Book)`) + `standalone_title: Option<String>`; update `commands/add_docu/mod.rs:86-87` and `upload/prepare_set.rs:33,59`
- Modify: `crates/mediagram/src/metadata/resolve.rs`: `docu_file(title, input)` → `standalone_file(kind: Kind, title: Option<&str>, input: &ResolveInput) -> ResolvedItem`; for `Kind::Book` with no `title` the fallback is `title_from_file_name` (no number split); update `tests/add_docu_plan.rs`
- Modify: `crates/mediagram/src/upload/session/identity.rs` + `index/set_lookup.rs`: `pub fn standalone_status(conn: &Connection, kind: Kind, title: &str) -> Result<Option<SetStatus>>` (`SELECT status FROM sets WHERE kind = ?1 AND title = ?2 AND show IS NULL`, newest first); `file_status` uses it when `new.standalone == Some(Kind::Book)` and `standalone_title` is set
- Create: `crates/mediagram/src/commands/add_audiobook.rs` (≤200 lines; split a `walk` helper out if it grows); Modify: `commands/mod.rs`, `commands/args/docu.rs` (`AddAudiobookArgs { path, dry_run, no_push }`), `cli.rs` (`/// Upload an audiobook MP3, or every MP3 under a folder` → `AddAudiobook(AddAudiobookArgs)`), `main.rs`
- Test: `crates/mediagram/src/media/audiobook_tags_tests.rs`, `upload/session/identity_tests.rs`, `media/inspect` test beside it, `tests/add_docu_plan.rs`

## Interfaces

- Consumes: `Kind::Book` (Phase 01); `edit::category::{planned, write}`; `index::artwork::put`; `mlib_spec::package::title_art_key`; `upload::session::{Session, Item, Set}`.
- Produces: CLI `mediagram add-audiobook`.

## Steps

- [ ] **1: Failing tests** (fixtures generated in a temp dir with `ffmpeg -f lavfi -i anullsrc=r=44100:cl=mono -t 2 -c:a libmp3lame`, plus a 2×2 JPEG attached with `-map 1 -c:v mjpeg -disposition:v attached_pic` and `-metadata title=… -metadata genre=…`; skip with a printed reason when `ffmpeg` is absent, as other media tests here do):
  - `an_attached_cover_is_not_the_video`: `inspect` on the tagged MP3 → `vcodec == None`, `quality == None`, `acodec == Some("mp3")`, `container == "mp3"`.
  - `tags_give_title_genre_and_cover`: `read` → title `"Bibi & Tina, Folge 121: Stürmische Weihnachten"`, genre `Some("Kinderbücher")`, `cover.mime == "image/jpeg"`; `cover_bytes` starts with `FF D8`.
  - `a_file_without_tags_is_titled_from_its_name`: `title_from_file_name("99 Mind Hacks Mentale Tricks [16969919]") == "99 Mind Hacks Mentale Tricks"`; `read` on an untagged MP3 named so → that title, `genre: None`, `cover: None`.
  - `a_blank_genre_is_none`: genre tag `"  "` → `None`.
  - In `identity_tests.rs`: `a_complete_book_is_found_by_its_title` (a recorded complete `book` set titled T → `Some(Complete)` for `NewSet { standalone: Some(Kind::Book), standalone_title: Some(T), .. }`) and `a_book_of_another_title_is_not_found` (→ `None`).
  - In `add_docu_plan.rs`: existing docu cases keep passing through `standalone_file(Kind::Docu, …)`; new `a_book_without_a_title_keeps_its_leading_number`.
- [ ] **2: Run, expect failures:** `cargo test -p mediagram audiobook_tags identity add_docu_plan inspect`.
- [ ] **3: Implement** probe/inspect, `audiobook_tags.rs`, the `NewSet` generalisation, `standalone_file`, `standalone_status`, the caption `hdr: None` when `vcodec` is `None` (in `prepare_set.rs`).
- [ ] **4: Implement `add_audiobook::run(cfg: &Config, args: AddAudiobookArgs) -> Result<()>`**: collect files; `read` each; dry run prints and returns; otherwise write category and poster per file (one `db::open`), then one `Session::upload` over all items with `standalone: Some(Kind::Book)`, `standalone_title: Some(title)`, `say` printing `uploading <title>` / failures as `add-docu` does; `session.end(args.no_push)`; `ensure!` no failed or blocked items. Category and poster are written even for a held book (re-runs refresh them).
- [ ] **5: Run tests**, then `cargo run -p mediagram -- add-audiobook --dry-run /media/DBI/HiDrive/HB`. Expected: 118 lines, every one `cover: yes`, genres matching the spec's counts (Sachbuch 31 … Fantasy 1).
- [ ] **6: Rust checks** (plan.md). **Commit** `feat(uploader): add-audiobook uploads MP3 audiobooks with their embedded cover and genre`.

## Success criteria

Dry run on the real folder matches the spec's measured facts; no test or `clippy` regressions.

## Risks

- Telegram caption length with 200-character titles: encode the longest real caption (title `"99 Mind Hacks: Mentale Tricks für Klarheit im Kopf und Stärke im Alltag | Vom Bestsellerautor und Mentalisten Timon Krause | Psychologie-Hacks für mehr Erfolg im Leben"`) in a test against the caption codec's own limit; shorten only if it fails.
- `edit::category::normalise` may merge a genre into an existing similar name; that is its intended behaviour.
