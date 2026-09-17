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
| 5 | Streaming part upload with resume | Code-complete, live gate pending |
| 6 | Index push + rescan | Code-complete, live gate pending |
| 7 | `verify` command + this documentation set | Code-complete, live gate pending |

`cargo test` is green (240 passing tests across both crates, plus 1
`#[ignore]`d live test) and every file under `src/` is within the 200-line
limit.

### Open live gates

These require a real Telegram account and an admin-owned private channel,
so they were not run in this environment and are not yet checked off. They
do not require a TMDB key: `add --manual` enters metadata by hand, so the
acceptance run needs an interactive terminal rather than an API key:

- **Phase 1 gate — PASSED 2026-09-15**: a live 3,758,096,384-byte (3.5 GiB)
  smoke upload to a real Premium account's private channel succeeded with
  no `FLOOD_WAIT` and no throttling; see `plan.md`'s "Phase-1 gate" section
  for the full measurement.
- **Phase 5/6/7 live gates — pending**: a live 3-part `add`, `kill -9` mid-part followed by `resume` producing no
  duplicate parts, `verify --full` matching all hashes on that set (and
  failing loudly on a deliberately tampered `parts.sha256` row), and
  `rm library.db && rescan` reproducing the same rows. Whole-plan success
  criteria are listed in `plan.md` and get checked off there once run.

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

Android is not cancelled, only no longer first. The UniFFI notes below still
apply when that round starts.

## Later: Android TV app round

A native Android TV player is the planned second client of the mlib
format. Approach: bind `mlib-spec` into Kotlin via
[UniFFI](https://mozilla.github.io/uniffi-rs/), reusing the caption
parser/serializer, part-plan math, filename grammar, and schema constants
verbatim rather than reimplementing them — the whole point of
[`docs/system-architecture.md`](system-architecture.md#7-backend-portability)'s
backend-agnostic index is that a second client doesn't reparse anything the
Rust crate already validates.

**Known UniFFI friction, already identified, not yet resolved:**
`caption::Episode` is `enum Episode { Single(u32), Range([u32; 2]) }`.
UniFFI does not support fixed-size array types inside enum variant
payloads. Before generating bindings, `Episode::Range` needs a
UniFFI-friendly shape — the two live options are a two-field variant
(`Range { first: u32, last: u32 }`) or a `Vec<u32>`-backed variant with a
length invariant enforced in code; either requires updating
`caption_codec` serialization and the JSON wire format stays the array
form (`"e":[1,2]`) regardless, since that's `serde`'s concern, not
UniFFI's. This is a v2-crate change, not a v3 spec bump — the wire format
is unaffected. Not started.

The TV app itself (playback, catalog browsing, download management) has no
detailed plan yet; it starts from a fresh planning round once the UniFFI
binding question above is resolved.

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
