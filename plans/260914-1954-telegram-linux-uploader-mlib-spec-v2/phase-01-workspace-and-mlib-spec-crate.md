---
phase: 1
title: "Workspace and mlib-spec crate"
status: completed
priority: P1
effort: "1d"
dependencies: []
---

# Phase 1: Workspace and mlib-spec crate

## Overview
Create the Cargo workspace and the pure `mlib-spec` library: caption v2 types, part planning, filename fallback grammar, part-name builder, SQLite schema. No Telegram or IO dependencies. This crate is the future UniFFI target for the Android app, so it must stay pure.

## Requirements
- Functional: serialize/parse `#mlib v=2` captions; compute part tables; parse `Title (Year)`, `Show (Year) - sNNeYY[-eYY]`, `Show - NNN` fallback names; build ≤60-char part file names; expose schema DDL + migration list.
- Non-functional: no async, no network; `serde` + `regex` + `sha2` + `ulid` only; every file < 200 lines; unit tests for every public function.

## Architecture
```
crates/mlib-spec/src/
  lib.rs            # re-exports
  ids.rs            # ProviderIds { tmdb: Option<u64>, tvdb: Option<u64>, imdb: Option<String> }
  caption.rs        # Caption struct (serde), Kind enum (movie|ep), Episode numbering (s/e/e-range/abs)
  caption_codec.rs  # to_text(&Caption, human_line) -> Result<String, BudgetError>; parse(&str) -> Option<Caption>
  part_plan.rs      # plan_parts(total: u64, part_size: u64) -> Vec<PartRange>; validates part_size % 1MiB == 0
  part_name.rs      # part_file_name(base, ext, idx, n) -> String, truncates title to fit 60 chars
  filename.rs       # fallback grammar: parse_filename(&str) -> Option<Guess>
  set_hash.rs       # set_hash(part_hashes: &[String]) -> String
  schema.rs         # SCHEMA_VERSION, MIGRATIONS: &[&str] (sets, parts, meta tables)
```
Caption JSON field order is fixed by struct order so output is deterministic. Marker line: `#mlib v=2`. Line 2: minified JSON. Line 3+: free human text. `to_text` errors if total > 1024 chars after dropping the human line; it first truncates the human line, never the JSON.

Caption JSON shape (normative, also written to docs in phase 7):
```json
{"t":"movie","ids":{"tmdb":693134,"imdb":"tt15239678"},"title":"Dune: Part Two","year":2024,
 "q":"2160p","hdr":"DV","container":"mkv","vcodec":"hevc","acodec":"truehd",
 "alang":["en","de"],"slang":["en"],"dur":9960,"variant":null,
 "set":"01JQ8F2K9M4XZ","part":{"i":0,"n":18,"off":0,"len":3758096384,"sha256":"..."},"total":62914560000}
```
Episode adds `"show"`, `"s"`, `"e"` (int or `[a,b]`), `"abs"`, `"title"` = episode title. Specials use `"s":0`. Anime absolute: `"abs":1075,"s":null,"e":null`.

SQL schema (sets, parts, meta) as in the brainstorm report §5.5 with two amendments decided in validation: `parts.file_unique_id TEXT` is renamed `parts.doc_id INTEGER` (Telegram document id), and `parts.verified_at INTEGER NULL` is added.

## Related Code Files
- Create: `Cargo.toml` (workspace, resolver 3), `rust-toolchain.toml`, `.gitignore`, `crates/mlib-spec/Cargo.toml`, all files listed above, `crates/mlib-spec/tests/caption_roundtrip.rs`, `crates/mlib-spec/tests/filename_grammar.rs`
- Create: `crates/mediagram/Cargo.toml` + `src/main.rs` stub printing version (filled in phase 2)

## Implementation Steps
1. `cargo new --lib crates/mlib-spec`, `cargo new crates/mediagram`; workspace `Cargo.toml` with shared `[workspace.dependencies]`.
2. Implement `ids.rs`, `caption.rs` with serde derives; `Option` fields serialize as `null` (spec requires presence of keys so parsers stay simple).
3. Implement `caption_codec.rs`: `to_text` and `parse`; parse tolerates trailing human lines and CRLF; rejects unknown `v`.
4. Implement `part_plan.rs`: returns `Vec<PartRange{idx, off, len}>`; n = ceil(total/part_size); last part shorter; total 0 is an error; `part_size % 1_048_576 != 0` is an error.
5. Implement `part_name.rs`: base = `Title (Year)` or `Show (Year) - sNNeYY`; suffix `.<ext>.pNNN` only when n > 1; hard cap 60 chars by truncating base on a word boundary.
6. Implement `filename.rs` with the three regexes from the brainstorm report §1.6 (MOVIE, EP, ABS), strips scene junk tokens (`1080p|2160p|WEB-DL|x265|...`) before matching.
7. Implement `set_hash.rs`, `schema.rs`.
8. Tests: round-trip every Kind; budget overflow; part plan on 62,914,560,000 B → 17 parts of 3.5 GiB + remainder; 1 GiB → 1 part; grammar fixtures incl. `The Simpsons - S24E03 - Adventures in Baby-Getting.mkv`.
9. `cargo build && cargo test && cargo clippy` clean.

## Success Criteria
- [x] `cargo test -p mlib-spec` green with ≥ 20 tests
- [x] Caption for a 2-language 4K movie with 18 parts serializes under 600 chars
- [x] `parse(to_text(c)) == c` for all fixtures
- [x] No file in crate > 200 lines

## Risk Assessment
- Field order drift breaks deterministic captions → lock with a snapshot test of the exact string.
- Regex grammar over-fitting → grammar is fallback only; ids are authoritative; keep it small.

## Completion notes (2026-09-14)
- 62 tests (unit + 3 integration files incl. tester probe file), clippy `-D warnings` clean, fmt clean, MSRV 1.87 inherited.
- Review fixes applied: empty-slice panic in `part_for_offset`; budget underflow at exactly 1,024; budget now counted in UTF-16 units (`caption_codec::tg_len`); extension capped at 8 chars in part names; junk stripping anchored after year/episode code; provider-id token extracted before junk stripping; scene-style episode titles; trailing ` -` trimmed.
- Wording amendment: caption line 2 is minified UTF-8 JSON with no Telegram entities or custom emoji (serde emits non-ASCII titles raw; "plain ASCII" was never enforceable for titles like Amélie).
- Deferred, not blocking: `Episode::Range([u32;2])` and `Option<usize>` returns are UniFFI-unfriendly → revisit when the Android round starts; `Range([2,1])` and set-hash hex validation left to the uploader; bare-year titles like `Blade Runner 2049.mkv` guess year 2049 → the TMDB prompt catches it.
- Reports: `reports/code-reviewer-260914-2046-phase-01-mlib-spec-crate-review-report.md`, `reports/tester-260914-2106-phase-01-mlib-spec-test-report.md`.
