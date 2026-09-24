# Phase 02: Core — search ported from the web

## Context links
- `web/src/search/normalize.ts` (119 l.): `fold` `:22-78`, `spellOut` `:36-40,88-99`, `variants` `:109-113`, `terms` `:116-119`
- `web/src/search/index.ts` (123 l.): `FIELDS` `:32`, `MAX_HITS=50` `:50`, match `:93-101`, sort `:111`, excerpt only for summary hits `:120`
- `web/src/search/excerpt.ts` (71 l.): `EXCERPT_PAD=90` `:12`, markdown strip `:24-31`, anchor `:49-55`, ellipses `:70`
- `web/src/catalog.ts:122-130` (`listSearchable`: playable sets + `assets` summary)
- `web/test/search-index.test.ts`, `web/test/search-normalize.test.ts` (the cases to share)
- Lesson 2026-09-18 in `tasks/lessons.md`: two implementations of one decision are pinned by one test reading both
- Pattern: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` + `web/test/fixtures/watch-state/`

## Overview
Priority P1 · Status pending · Core API only; UI in 03.

## Key insights
- Web search is server-side over the index, not over the browser's catalog,
  because it needs the summary text the catalog rows do not carry. The phone
  has no server but has the index: **search runs in `mediagram-core`**, a port,
  not a Kotlin filter over `MediaSet` (which would miss summaries).
- Ranking: every term must hit some field (terms may hit different fields);
  rank = best field index any term hit (`title` < `show` < `chap` < `path` < `summary`);
  ties by title, German collation. A first pass approximated this with
  `fold(title)` then raw title, which a code review caught disagreeing with
  the web on real titles from the library (leading punctuation, digits
  beside a percent sign, case). Replaced with `icu_collator`, ICU4X's
  pure-Rust collator built from the same CLDR German tailoring Bun's ICU
  reads, so the tie-break is the same comparison rather than a substitute
  for it — real disagreeing pairs are pinned in `cases.json` (see
  `shared_search_fixtures.rs`'s module doc).
- Query is folded only; text side keeps both spellings (`ä`→`a` and `ae`), so
  "steuer" never matches "Fenster".
- Web folds the corpus once per catalog. Phone: `search::rank::Corpus`
  folds the same way, cached in `Core` by the catalog's resolved version
  directory (`api::search::cache`) — a review measured the naive per-call
  fold at ~18 ms over the real library, likely over the phase's 50 ms
  budget on the tablet, so the cache was built rather than left for a
  device check to justify.
- **Deliberate difference from the web:** `excerpt`'s ±90 window is counted
  in `char`s (Unicode scalar values); the web counts UTF-16 code units.
  The two agree for BMP text — everything this library's summaries are
  written in — and only diverge for a surrogate pair (an emoji, say)
  landing exactly on the window's edge, where the web's UTF-16 count would
  split it in two and Rust's would not. Kept as-is rather than matched: the
  web's behaviour there is a defect, not a decision worth porting.

## Requirements
- `Core::search(query) -> Vec<SearchHit{set_id, matched, excerpt: Option<String>}>`,
  ≤ 50 hits, empty query → empty list, no catalog → empty list.
- Same results as web for every shared fixture case.

## Architecture
`search/normalize.rs` (fold, spell_out, variants, terms) → `search/rank.rs`
(`Corpus::build` folds; `search` ranks folded entries against a query,
tie-breaking with `icu_collator`) → `search/excerpt.rs` → `api/search/mod.rs`
export, reading `PLAYABLE_SQL` sets + summary via `catalog.rs`'s
`list_searchable` and caching the built `Corpus` per catalog version in
`api/search/cache.rs`. Kotlin joins `set_id` back to the in-memory
`MediaSet` list for display.

## Related code files
Create:
- `crates/mediagram-core/src/search/mod.rs`, `normalize.rs`, `rank.rs`, `excerpt.rs` (+ `_tests.rs` beside each)
- `crates/mediagram-core/src/api/search/mod.rs` (export; `SearchHit` record) and
  `cache.rs` (+ `cache_tests.rs`) — a directory, not a single file, the same
  shape `api::channel` uses, once the per-version cache needed its own module
- `web/test/fixtures/search/cases.json` (library rows + queries + expected ids/matched/excerpt)
- `web/test/search-shared-fixtures.test.ts` (runs web `search/` over the fixture)
- `crates/mediagram-core/tests/shared_search_fixtures.rs` (runs core over the same fixture)
Modify:
- `crates/mediagram-core/src/lib.rs` (mod), `catalog.rs` (searchable rows query,
  `SearchableSet`), `api/mod.rs` (`Core.search_cache` field)
- `android/core/data/src/main/kotlin/CoreClient.kt`, `DefaultCoreClient.kt` (test fakes
  untouched — the interface method has a safe default, phase 03 adds a real fake)
- generated bindings

## Implementation steps
1. Port `normalize.ts` 1:1 incl. the ASCII fast path; unit tests copy `search-normalize.test.ts` cases.
2. Port `excerpt.ts` (anchor on original words whose variants contain a term; ±90 chars; strip `*_\`~#`).
3. Port ranking/limit from `index.ts`.
4. Write `cases.json` from the existing `search-index.test.ts` scenarios (umlaut spellings,
   substring, all-terms-required, cross-field, order title>path>summary, excerpt window,
   none-for-title-hit, limit). Web test + Rust test both assert it.
5. Export, build core, regenerate bindings, `CoreClient.search`.
6. Device: time a 3-term query in logcat (`tracing` span).

## Todo
- [x] normalize + tests
- [x] excerpt + tests
- [x] rank + limit
- [x] shared fixture, web test, Rust test
- [x] export + Kotlin client
- [x] check.sh, bump, changelog
- [ ] device timing (needs the adb tablet; a `tracing::debug!` span in
      `api/search.rs` logs `elapsed_ms` on every call — read it from logcat
      during this phase's own device check, run by whoever holds the phone,
      not from this worktree)

## Success criteria
- `bun test` and `cargo test` both pass `cases.json` (31 cases); editing an
  expected order in the fixture fails **both**.
- Desktop release build over the real library (903 sets, 162 summaries):
  a cached (warm) query costs well under a millisecond; the first query
  after a catalog swap (cold, folds + ranks) costs ~25 ms — the reason the
  cache is in place rather than deferred to a device check.
- Device: query latency logged < 50 ms on the tablet (the `elapsed_ms` span
  in `api/search/mod.rs`; still to read from logcat on the adb tablet).

## Risks
- Collation drift (German `localeCompare` vs a Rust substitute) → resolved by
  using `icu_collator` rather than approximating; `cases.json` carries the real
  title pairs a review found the earlier fold-based tie-break getting wrong,
  so any future regression here fails a test, not a viewer.
- Unicode NFD: `unicode-normalization` is already a core dependency
  (`crates/mediagram-core/Cargo.toml:61`); no new crate. `regex` (already a
  workspace dependency, used by `mlib-spec`) and `icu_collator`/`icu_locale_core`
  (new, `compiled_data` feature — bundles CLDR German collation data into the
  binary rather than reading it at runtime) were added for the excerpt's
  Unicode word matcher and the title tie-break respectively.

## Security
Query is never interpolated into SQL; matching is in memory. Length-cap query at 200 chars.

## Review
A code review (`reports/code-reviewer-260924-0256-phase-02-core-search-report.md`)
found the title tie-break disagreeing with the web on real titles (High),
the fixture not covering ties or several edge cases (Medium), and the fold
cost being high enough to need a cache rather than a device check to
justify one (Medium), plus five Low findings (Unicode property mismatches,
a documentation gap, stale comments, an over-eager fixture skip). All
addressed; see the report for detail and verification.

## Next steps
03 builds the screen.
