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
  ties by title, German collation. Rust has no ICU `localeCompare("de")`:
  compare by `fold(title)` then raw title — covered by fixtures so any
  divergence from web order fails a test, not a viewer.
- Query is folded only; text side keeps both spellings (`ä`→`a` and `ae`), so
  "steuer" never matches "Fenster".
- Web folds the corpus once per catalog. Phone: fold per call first (566 rows,
  debounced 200 ms); cache per catalog version only if the device check shows > 50 ms.

## Requirements
- `Core::search(query) -> Vec<SearchHit{set_id, matched, excerpt: Option<String>}>`,
  ≤ 50 hits, empty query → empty list, no catalog → empty list.
- Same results as web for every shared fixture case.

## Architecture
`search/normalize.rs` (fold, spell_out, variants, terms) → `search/rank.rs`
(corpus rows → ranked hits) → `search/excerpt.rs` → `api/search.rs` export
reading `PLAYABLE_SQL` sets + summary via `catalog_assets.rs` (phase 01).
Kotlin joins `set_id` back to the in-memory `MediaSet` list for display.

## Related code files
Create:
- `crates/mediagram-core/src/search/mod.rs`, `normalize.rs`, `rank.rs`, `excerpt.rs` (+ `_tests.rs` beside each)
- `crates/mediagram-core/src/api/search.rs` (export; `SearchHit` record)
- `web/test/fixtures/search/cases.json` (library rows + queries + expected ids/matched/excerpt)
- `web/test/search-shared-fixtures.test.ts` (runs web `search/` over the fixture)
- `crates/mediagram-core/tests/shared_search_fixtures.rs` (runs core over the same fixture)
Modify:
- `crates/mediagram-core/src/lib.rs` (mod), `catalog.rs` or `catalog_assets.rs` (searchable rows query)
- `android/core/data/src/main/kotlin/CoreClient.kt`, `DefaultCoreClient.kt`, test fakes
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
- [ ] normalize + tests
- [ ] excerpt + tests
- [ ] rank + limit
- [ ] shared fixture, web test, Rust test
- [ ] export + Kotlin client
- [ ] check.sh, bump, changelog, device timing

## Success criteria
- `bun test` and `cargo test` both pass `cases.json`; editing an expected order
  in the fixture fails **both**.
- Device: query latency logged < 50 ms on the tablet (else add per-version cache).

## Risks
- Collation drift (German `localeCompare` vs folded compare) → pinned by fixture; if
  a case cannot be matched without ICU, record it as the one accepted difference.
- Unicode NFD: `unicode-normalization` is already a core dependency
  (`crates/mediagram-core/Cargo.toml:61`); no new crate.

## Security
Query is never interpolated into SQL; matching is in memory. Length-cap query at 200 chars.

## Next steps
03 builds the screen.
