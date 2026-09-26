# Development roadmap

Source of truth for phase-by-phase status is
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`; this
document is the higher-level summary kept current for anyone not reading
the plan directory.

## v1 (Linux CLI uploader): phases 1-7

All seven implementation phases are code-complete and merged. Phases 5-7
stay "live gate pending" until the acceptance run in `plan.md` executes
against the real channel; `plan.md` is the source of truth for that state:

| Phase | Scope | Status |
|---|---|---|
| 1 | Workspace + `mlib-spec` crate (caption, part-plan, filename grammar, schema) | Complete |
| 2 | Config + Telegram auth (grammers 0.10 client, session, login/whoami) | Complete |
| 3 | Media inspect + faststart remux | Complete |
| 4 | TMDB metadata resolution | Complete |
| 5 | Streaming part upload with resume | Complete |
| 6 | Index push + rescan | Complete |
| 7 | `verify` command + this documentation set | Complete |

`cargo test` is green (240 passing tests across both crates, plus 1
`#[ignore]`d live test) and every file under `src/` is within the 200-line
limit.

### Live gates

These needed a real Telegram account and an admin-owned private channel, so
they waited until there was one. All have now been run.

- **Phase 1 gate — PASSED 2026-09-15**: a live 3,758,096,384-byte (3.5 GiB)
  smoke upload to a real Premium account's private channel succeeded with
  no `FLOOD_WAIT` and no throttling; see `plan.md`'s "Phase-1 gate" section
  for the full measurement.
- **Phase 5/6/7 live gates — PASSED 2026-09-17**: run against the real
  channel with 10 MiB parts, so a 24 MB file made three and a 118 MB file
  twelve. A 3-part `add` then `verify --full` reported 3/3; a tampered
  `parts.sha256` row made `verify` exit 1 naming the part and printing both
  digests. `kill -9` with 3 of 12 parts done, then `resume`, produced 12 parts
  across message ids 49-60 — a span of exactly twelve, so the part in flight
  when the process died was adopted rather than re-uploaded. Peak RSS was
  35.6 MB. `rm library.db && rescan` reproduced all 18 sets and 32 parts
  byte-identically across every column the captions carry.

  The gate also found a bug it was not looking for: the id of the pinned
  index lives in `library.db`, so the first push after a rescan left the
  previous index pinned beside the new one. `rescan` now asks Telegram for
  the pinned messages and records the index snapshots among them.

## Tutorials and courses

Caption `v3` adds a third kind, `tut`, plus two fields: `chap` for a chapter
title and `cid`, a general collection id that groups sets with no provider
id. Course, chapter and lesson map onto `show`, `s`/`chap` and `e`/`title`,
so ordering, resume and the playable invariant need no special cases.

`mediagram add-course` walks a course folder; `mediagram add` gained
`--course`, `--cid`, `--chapter`, `--chap` and `--lesson` for a single
lesson. Schema version 2 adds `sets.chap`, and migrations became
version-gated because SQLite has no `ADD COLUMN IF NOT EXISTS` and the old
runner replayed every statement on each open.

Design: `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md`.

## Prebuilt metadata package

`mediagram export-package` publishes an encrypted package (index snapshot
plus posters) to a static URL, with a small plaintext pointer at a fixed
path. Phases 1-3 of
`plans/260915-1956-prebuilt-metadata-package-for-player/` are complete and
the format is specified in [`mlib-package-v1.md`](mlib-package-v1.md).

Known limits of format 1, all deliberate: the pointer is unsigned, so a
hostile or stale host can withhold updates though it cannot pass off stale
content as fresh; key rotation is manual; and the package is capped at 48 MB
on export because a reader must hold it whole to verify it.

## Web player: complete

A web player comes first, decided 2026-09-16. Design:
`plans/reports/brainstorm-to-planner-260916-1936-web-player-bun-stack-report.md`.

Shape, revised 2026-09-16 once the player was required to run anywhere rather
than beside the uploader: the Bun app speaks MTProto itself through
`teleproto` (the maintained fork of the archived GramJS), takes its catalog
from the published encrypted package, and serves HTTP Range over a set's
concatenated parts. ffmpeg transcodes what browsers cannot play. Browsers
refuse AC3/E-AC3 and Matroska and are patchy on HEVC, so the films need
transcoding, and a 25 Mbit/s uplink means remote viewing needs it for bitrate
too.

`mediagram serve` (Rust, phase 1, complete) is kept as the reference the
TypeScript port is checked against byte for byte. Verified before committing
to the reversal: `teleproto` on Bun 1.4.2 read five byte ranges of the live
7 GB film identically to the local source file, seeking in 46-152 ms, using a
session string exported from the uploader's own auth key — so the player needs
no second Telegram login.

All eight phases are complete as of 2026-09-17. Plan:
`plans/260916-1936-web-player-bun-stack/`.

| Phase | Scope | Status |
|---|---|---|
| 1 | `mediagram serve`: Range over a set's concatenated parts (Rust) | Complete |
| 2 | The same in TypeScript over `teleproto`, so it runs anywhere | Complete |
| 3 | The `mlib-package-v1` reader: catalog from a published package | Complete |
| 4 | The page: shelves for Movies, Series and Tutorials; direct play | Complete |
| 5 | Disk cache with a quota and readahead | Complete |
| 6 | ffmpeg to HLS for what browsers will not decode | Complete |
| 7 | Loopback binding, proxy-aware reach, bitrate-aware direct play | Complete |
| 8 | This documentation set | Complete |

Verified live against the channel throughout: byte-exact Range reads of a
7 GB two-part film including across the part boundary, a full season uploaded
and corrected, and a 2h 2m HEVC/AC-3 film converted and played in a browser
that had refused it — 1.7 s to first frame, 5.1 Mbit/s against an 8 Mbit/s
cap, no encoder left running afterwards.

What remains for the player is operational rather than structural: nothing is
signed in `mlib-package-v1` format 1 (a hostile host can withhold updates, not
forge one), a conversion produces one rendition rather than an adaptive
ladder, and the transcode directory has no quota of its own.

## Web player: editorial departments and schema v9 (0.62.0)

Released 2026-09-26: department pages (Home, Movies, Series, Tutorials, Collections),
feature pages with tabs (Overview, Cast, Similar, Details), Cast from schema v9
credits with circular portraits, franchises, people search, Settings shell with
Appearance (Dark/Light/Auto + 7-accent swatches) and Profile, theme system via
`data-theme`, and search grouping. Schema v9 adds shows columns, credits table,
and franchises table. Both uploaders and Android must run ≥0.62.0 before any v9
push/export.

| Phase | Scope | Status |
|---|---|---|
| 1 | Schema v9 (Rust): shows columns + credits/franchises tables, portrait pipeline | Complete |
| 2 | Web data layer: v9 queries, portrait resolution, similar, people search | Complete |
| 3 | Shell, navigation, Home page (magazine layout) | Complete |
| 4 | Feature detail pages (film, series) with tabs, Cast | Complete |
| 5 | Departments (Movies/Series/Tutorials), paging, genre shelf | Complete |
| 6 | Collections (franchises + lists) and Search (grouped by type) | Complete |
| 7 | Settings (Appearance, Profile, admin Library & Telegram), theme system | Complete |
| 8 | Verify and ship, docs update | Complete |

All phases complete, tests passing (web 2141, Rust 1268), lint clean. Code review (DONE_WITH_CONCERNS) identified
3 high-severity behavioural fixes applied before ship, 2 medium-severity items (async
page redraw, season picker focus), and medium-severity credits atomicity (now transactional).
Versions bumped to 0.62.0 in all three manifests. Plan:
`plans/260926-1142-web-player-editorial-departments/`.

## Android: parity for editorial departments

Android lacks departments, feature pages, Cast, franchises, and people search.
Follow-up plan: `plans/260926-1330-android-editorial-departments-parity/`. Android
must tolerate v9 before any v9 push. Existing Android builds refuse v9 packages.

## Android: shipping, and reaching for parity

The phone app exists and is installed on a real device. It is the third
consumer of the index and the second viewing surface, described in
[`docs/system-architecture.md`](system-architecture.md#8-playback-the-android-app).

**The UniFFI friction this document predicted never had to be resolved.**
`caption::Episode` is `enum Episode { Single(u32), Range([u32; 2]) }` and
UniFFI cannot carry a fixed-size array inside an enum variant — but `Episode`
does not cross the boundary. `mediagram-core`'s `dto.rs` flattens a set's
episode into an `episode_first`/`episode_last` pair, so the parser stays an
implementation detail of the crate that owns it and no player ever sees the
enum. `mlib-spec` was never changed and the wire format never moved.

The shape that did emerge is the opposite of the one planned here: rather than
binding `mlib-spec` alone and writing a Telegram client in Kotlin, the whole of
`mediagram-core` — grammers included — is bound, so the phone has no server and
no second MTProto implementation.

| Round | Scope | Status |
|---|---|---|
| Foundation | modules, Hilt, Compose, setup and login, the catalog | Complete |
| Player | a set that plays, over a custom ExoPlayer data source and a disk cache | Complete |
| Transport controls | play/pause, a scrubber, skip, a clock | Complete |
| System menu | the System screen, playback stats, library refresh | Complete |
| Details | the phone fetches its own synopses and artwork from TMDB | Complete |
| External cache | the cache on a chosen volume, and a LAN chunk server every Android device shares | Done, [`plans/260925-2046-external-cache-volume-and-lan-chunk-server/`](../plans/260925-2046-external-cache-volume-and-lan-chunk-server/plan.md) (superseding [`260921-1751`](../plans/260921-1751-android-external-cache/plan.md)); the budget shipped in `b91e9e8` |
| Parity | audio, subtitles, watch state, and the screens that read it | Done, `260924-0139-android-web-parity` (removed; in git history) (superseding `260922-0124`, also in git history) |

### What the phone still cannot do

Per CLAUDE.md § Surface Parity the web player is the reference, so these are
gaps rather than choices: no watch state at all — no resume, no watched marks,
no watchlist, no lists — because `mediagram-core` exposes nothing touching
progress; no audio-track or subtitle selection, which reaches 312 and 206 sets
respectively; no search; no notes; no start page. The parity plan above closes
them in order and names the ones that will stay different.

## The television surface

Planned in `plans/260924-2239-android-tv-surface/` and built on branch
`feat/android-tv-ui`. The television surface is a second renderer over the
`feature:*` ViewModels, built on `androidx.tv:tv-material`. Phone and TV share
the catalog and player logic; TV adds remote navigation and 10-foot UI.

| Phase | Scope | Status |
|---|---|---|
| 1 | Move shared rules out of `ui-mobile` into `ui-common` and feature modules | Complete |
| 2 | Foundation: 10-foot theme, overscan, `TvApp` shell | Complete |
| 3 | Setup, sign-in, library and profile choice via D-pad | Complete |
| 4 | Catalog browse: masthead, Home, walls, series, courses, title pages | Complete |
| 5 | Player: full-screen, remote keys, marks, subtitles, settings, up next | Complete |
| 7 | Catch up with main: audio, subtitles, search, genres, offline, profile removal | Complete |
| 6 | System/Settings menu, docs, version bump, emulator validation | In review |

Walked on the real TV box and smoke-tested on the phone on 2026-09-26
(`plans/260924-2239-android-tv-surface/reports/validation-260926-android-tv-box-and-phone-walk-report.md`).
Remaining before merge: the emulator walk from a fresh install, the code review's
fixes, and the version bump.

## Explicitly deferred (from the v1 implementation logs, not tracked as bugs)

Recorded here so they are not silently forgotten, not because they are
scheduled:

- `add --tmdb <id>` infers movie vs. show by calling `/movie/{id}` unless
  `--season`/`--episode` is also given; there is no `--kind` override.
  Acceptable because `add` already requires `--season`/`--episode` for
  `t=ep`, but a TMDB id that is ambiguous without those flags will resolve
  as a movie.
- `media::test_fixtures` (ffmpeg-built fixtures shared by unit and
  fixture-integration tests) compiles into the release binary rather than
  being gated behind `#[cfg(test)]` / a dev-dependency-only crate. Unused
  at runtime, adds to binary size.
- TVDB ids are stored verbatim when passed via `--tvdb` but never fetched
  from the TVDB API; TMDB is the only metadata provider in v1.
