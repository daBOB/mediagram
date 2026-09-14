# Code Review: Phase 1 — `mlib-spec` crate

Scope: `Cargo.toml`, `rust-toolchain.toml`, `.gitignore`, `crates/mlib-spec/**`, `crates/mediagram/**` (~980 LOC). All claims below verified empirically with a scratch crate depending on `mlib-spec` by path (no repo files modified).

## Success criteria (phase-01)

| Criterion | Result |
|---|---|
| `cargo test -p mlib-spec` green, ≥20 tests | 22 tests pass for the listed files (12 unit + 5 + 5). See note on stray `edge_cases_probe.rs` below. |
| 2-lang 4K 18-part movie caption < 600 chars | 450 chars (asserted in `tests/caption_roundtrip.rs:65`). |
| `parse(to_text(c)) == c` for all fixtures | Yes (`caption_roundtrip.rs:69-75`, incl. CRLF). |
| No file > 200 lines | Max 179 (`caption.rs`). |
| Field-order snapshot test | Present (`caption_roundtrip.rs:53`). |

Overall: clean, small, idiomatic. Two real bugs (one panic, one budget-counting error), one build-metadata error, a few grammar false positives.

## Critical

None (no trust boundaries / IO in this crate).

## High

**H1. `part_for_offset` panics on empty slice** — `crates/mlib-spec/src/part_plan.rs:68`
`(i > 0 && ...).then_some(i - 1)` evaluates `i - 1` eagerly; with `parts == &[]`, `i == 0` → "attempt to subtract with overflow" (debug panic; wraps in release and happens to return `None`). Fix: `.then(|| i - 1)`. Add a test for `&[]`.

**H2. Caption budget counts Unicode scalars, Telegram counts UTF-16 code units** — `caption_codec.rs:39,45,48`
`to_text(&movie(), "🎬".repeat(2000))` yields `chars()==1024` but `encode_utf16()==1591`. Telegram will reject with `MEDIA_CAPTION_TOO_LONG` at upload time (phase 3 will hit this on any emoji-heavy human line). The JSON line is also not guaranteed ASCII: `serde_json` emits non-ASCII raw (`title: "Amélie 🎬"` → `is_ascii()==false`, 450 chars / 451 UTF-16), contradicting the locked "line 2 JSON is plain ASCII" wording. Astral chars in titles are rare but emoji in the human line is the documented use (`caption_codec.rs:6`).
Fix (2 lines, no format change): count with `s.encode_utf16().count()` in both places and truncate the human line by UTF-16 units (accumulate `c.len_utf16()`); or, cheaper, keep `chars()` but reserve budget as `CAPTION_BUDGET - 2 * astral_count`. Recommend the former and reword the locked decision to "JSON is UTF-8; budget measured in UTF-16 code units". Also consider truncating on a char boundary that does not split a ZWJ/variation-selector sequence (cosmetic).

**H3. `rust-version` not inherited; declared MSRV is wrong** — `Cargo.toml:9`, `crates/*/Cargo.toml`
Workspace sets `rust-version = "1.85"` but neither crate has `rust-version.workspace = true` (`cargo metadata` → `rust_version: None`), so clippy's `incompatible_msrv` is silently disabled. `u64::is_multiple_of` (`part_plan.rs:37`) was stabilized in 1.87, so the stated MSRV would not compile. Fix: add `rust-version.workspace = true` to both crates and set `rust-version = "1.87"` (or replace with `part_size % MIB != 0`).

**H4. `to_text` panics when marker+JSON is exactly 1024 chars and `human` is non-empty** — `caption_codec.rs:45`
`if used > CAPTION_BUDGET` lets `used == 1024` through, then `CAPTION_BUDGET - used - 1` underflows ("attempt to subtract with overflow"; in release it wraps to `usize::MAX`, `room > 0` passes and the whole human line is appended, blowing the budget). Verified with a fixture padded so `used == 1024`. Fix: `let room = CAPTION_BUDGET.saturating_sub(used + 1);` (and `if room > 0` then works). Add the boundary test.

## Medium

**M1. `part_file_name` can exceed 60 chars when suffix alone ≥ 60** — `part_name.rs:35-36`
`part_file_name("Dune", "x"*70, 0, 2)` → 76 chars; doc comment (`:28`) promises ≤60. `ext` comes from `rsplit_once('.')` on an arbitrary filename (`filename.rs:53`), so a weird file name reaches this. Also `ext` is not sanitized (spaces/illegal chars pass through). Fix: sanitize+cap `ext` (e.g. ≤10 chars, alnum) or truncate the final string to `MAX_NAME_LEN` as a last resort.

**M2. Junk stripper is unanchored and eats real titles** — `filename.rs:33`
`JUNK` matches anywhere, so titles that start with or contain a token are destroyed: `Internal Affairs (1990).mkv` → `None`, `Multi-Facial (1995).mkv` → `None`, `Proper Villains (2020).mkv` → `None`. Tokens `internal|multi|proper|dv|nf|avc` are the risky ones. Fix: require the junk run to start after a year or after an `sNNeNN`/`- NNN` token, or only strip when the match does not start at offset 0 and at least one of `\d{3,4}p|web|blu|rip|remux|x26[45]|hevc` is present. Keep the test fixtures.

**M3. Scene-style episode with title but no dash is not recognised** — `filename.rs:37`
`Show.S01E01.Title.mkv` → fallback (`title="Show S01E01 Title"`, no season/episode). Extra group requires `\s*-\s*`; scene names separate the episode title with a plain space after dot-replacement. Fix: `(?:\s*-\s*|\s+)(?P<extra>.+?)`. Same for `Some.Show.S03E12.Pilot.1080p.BluRay.mkv` (junk strip leaves `Some Show S03E12 Pilot`).

**M4. Stray 638-line test file in tree** — `crates/mlib-spec/tests/edge_cases_probe.rs`
Not in the phase-1 file list; 2 of its 36 tests fail (`part_for_offset_empty_array` — real bug H1; `part_file_name_with_very_long_ext` — M1) and it fails `clippy -D warnings` (`identity_op`, `collapsible_if`) and the 200-line rule. Either fold the useful cases into the two existing test files (≤200 lines each) or delete it before the phase gate; `cargo clippy --all-targets` is currently red because of it.

**M5. UniFFI blockers (only true blockers listed)**
- `Episode::Range([u32; 2])` (`caption.rs:21`): fixed-size arrays are not a UniFFI type. `Range(u32, u32)` under `#[serde(untagged)]` still serializes as `[a,b]` — zero wire change, but `first()/last()` and the tests need touching. Do it now while the API is fresh.
- `part_for_offset(...) -> Option<usize>` (`part_plan.rs:66`): `usize` unsupported; return `Option<u32>` (matches `PartRange.idx`).
- `set_hash<S: AsRef<str>>(&[S])` (`set_hash.rs:7`): generic fns cannot be exported; use `&[String]` or a concrete wrapper.
- `MIGRATIONS`/`PLAYABLE_SQL` consts: UniFFI exports functions not consts — non-blocking if Android never runs the DDL (it reads the pushed snapshot), so ignore.

## Low

- **L1** (moved to H4.)
- **L2** `is_mlib` vs `parse` disagree on leading newline (`caption_codec.rs:56` trims, `:61` reads first line raw). Telegram trims captions so this is theoretical; make `parse` skip leading blank lines or drop `trim_start` in `is_mlib`.
- **L3** `set_hash` concatenates without validating each element is 64 hex chars (`set_hash.rs:10`): `["aab","b"]` collides with `["aa","bb"]`. Uploader always passes 64-hex, so fine; a `debug_assert!(p.len()==64)` documents the invariant.
- **L4** `Episode::Range([2,1])` deserializes without validation (`caption.rs:18`); `[1]`, `[1,2,3]`, `-1` are correctly rejected. Add `a <= b` check in a constructor if the Android side will index by it.
- **L5** `MOVIE` grammar leaves a trailing dash: `Fahrenheit 451 - 2018.mkv` → `title="Fahrenheit 451 -"`; `Blade Runner 2049.mkv` → year 2049. Post-trim `" -"` from title; optionally bound year ≤ current+1.
- **L6** `JUNK.*$` also discards a trailing `{tmdb-…}` id: `Movie (2021) 1080p {tmdb-123}.mkv` loses the id. Jellyfin order (`[tmdbid-…] - 1080p`) works. Consider extracting the id token before junk-stripping.
- **L7** `plan_parts` `idx: i as u32` (`part_plan.rs:56`) silently truncates for n > u32::MAX (needs > 4 PiB at 1 MiB parts); a `u32::try_from` → error is cheap.
- **L8** `base_name`, `Caption::is_movie`, `Episode::first` have no direct tests (plan: "unit tests for every public function").
- **L9** `schema.rs`: `MIGRATIONS` is a flat idempotent list with `SCHEMA_VERSION = 1`; `ALTER TABLE ADD COLUMN` in v2 will not be idempotent, so the `&[&str]` shape will need to become `(version, &[stmt])` eventually — fine to defer (YAGNI) but avoid promising stability of `MIGRATIONS` in docs. `ON DELETE CASCADE` requires `PRAGMA foreign_keys=ON` per connection (phase 2 must do it). No index on `sets(tmdb)`/`sets(status)` — add when dedupe query lands.
- **L10** Workspace is not a git repository yet (`git rev-parse` fails); `.gitignore` exists. Init before phase 2 so review diffs work.
- **L11** `crates/mediagram/src/main.rs:1` comment says "wired in later phases" — borderline plan reference; reword to "Subcommands are not yet implemented".

## Positive

- Wire format locked by a byte-exact snapshot test; serde config (`Option` → `null`, unknown keys ignored, missing `Option` keys → `None`) gives sane forward compatibility. Verified: unknown key accepted, missing `show`/`e` accepted, missing `slang` rejected.
- Part planning arithmetic is correct at all boundaries checked (`total == part_size`, `+1`, `MAX_PART_SIZE`, exact multiple has no empty tail).
- No plan/phase/finding codes in code comments; all files < 200 lines; deps limited to the allowed set; no IO/async.
- Error types are `thiserror` enums, `PlanError` is `PartialEq` for testability.

## Recommended actions (ordered)

1. Fix H1 (`then(|| i - 1)`) and H4 (`saturating_sub`) + boundary tests for both.
2. Fix H2: UTF-16 budget counting; reword locked decision to "UTF-8 JSON, UTF-16 budget".
3. Fix H3: inherit `rust-version` in both crates, bump to 1.87.
4. Resolve M4: fold/delete `edge_cases_probe.rs` so `clippy --all-targets` is green again.
5. M5: change `Range([u32;2])` → `Range(u32,u32)`, `usize` → `u32`, de-generic `set_hash` now (cheap, no wire change).
6. M1–M3 grammar/name hardening with the fixtures above added to `filename_grammar.rs`.

## Unresolved questions

- Does the Android player intend to call `part_for_offset`/`plan_parts` over UniFFI, or only read the pushed `library.db`? Determines whether M5 is a phase-1 or phase-7 item.
- Should `parse` be strict about JSON extra keys (`deny_unknown_fields`) for v2, or keep lenient for v2.x additions? Current lenient behavior is recommended; confirm it is the intended spec.
