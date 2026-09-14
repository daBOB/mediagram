---
phase: 4
title: "TMDB metadata resolution"
status: completed
priority: P2
effort: "1d"
dependencies: [1]
---

# Phase 4: TMDB metadata resolution

## Overview
Turn a file path plus optional flags into a `ResolvedItem` (kind, provider ids, canonical title/show, year, season/episode/abs, episode title) using TMDB, with interactive disambiguation and a `--manual` path.

## Requirements
- Functional: precedence explicit id flag → filename grammar → TMDB search → prompt if >1 plausible hit → `--manual` prompts title/year/kind/season/episode. Episode titles fetched from `/tv/{id}/season/{s}/episode/{e}`. `external_ids` appended to fetch imdb/tvdb ids.
- Non-functional: one reqwest client; honor 429 `Retry-After`; responses cached on disk under data dir (`tmdb-cache/{path}.json`) so re-runs are free; never prompt when an explicit id was given.

## Architecture
```
crates/mediagram/src/metadata/
  tmdb_client.rs  # get_json(path, query) with cache + 429 backoff; endpoints: find, movie, tv, tv_episode, search_movie, search_tv
  tmdb_types.rs   # serde models (subset)
  resolve.rs      # resolve(input: ResolveInput) -> ResolvedItem; ResolveInput built from clap flags + filename Guess
  prompt.rs       # dialoguer Select over candidates "Title (Year) [tmdb-ID]"; manual entry form
```
"Plausible hit": search results whose year matches the guess ±1 when a year was parsed; if exactly one plausible hit and title similarity ≥ 0.9 (normalized), auto-pick; otherwise prompt.

## Related Code Files
- Create: files above; `crates/mediagram/tests/tmdb_resolve.rs` using recorded JSON fixtures under `tests/fixtures/tmdb/` (no live network in tests)
- Modify: `Cargo.toml` deps: reqwest (rustls-tls, json), strsim

## Implementation Steps
1. `tmdb_client.rs`: base `https://api.themoviedb.org/3`, bearer or `api_key` query from config; cache key = sha1(path+query).
2. Implement endpoint helpers; map `/find` response to candidate list.
3. `resolve.rs`: implement precedence rules; `--season/--episode` required for `t=ep` unless parsed from filename; `--abs` for absolute numbering.
4. `prompt.rs`: Select list; `--manual` form; both produce `ResolvedItem`; manual items have all ids `None`.
5. Fixture tests: remake collision (two "The Thing" results) prompts; explicit `--tmdb` never prompts; episode title populated.

## Success Criteria
- [x] `resolve` for `Severance (2022) - s02e01.mkv` returns tmdb 95396, episode title "Hello, Ms. Cobel" from fixtures
- [x] Ambiguous search triggers prompt exactly once
- [x] Cache hit produces zero HTTP calls (assert via fixture client)

## Risk Assessment
- TMDB rate limits during bulk adds → cache + Retry-After; adds are sequential anyway.
- Anime absolute numbering rarely resolvable on TMDB → `--abs` + `--manual` path; store `abs` verbatim.

## Completion notes

- `TmdbApi` is a native `async fn` trait (edition 2024, no `async-trait` needed since `resolve()` only takes `&impl TmdbApi`, never `dyn`). `TmdbClient` implements it directly over `reqwest`; `DiskCachedApi<A: TmdbApi>` wraps any implementor with the sha256(path + sorted query) disk cache, so the cache is unit-testable against a stub without touching HTTP. `TmdbClient::with_cache(api_key, cache_dir)` returns `DiskCachedApi<TmdbClient>` as the one real-world entry point.
- Cache key is sha256, not sha1 as this file's Architecture section originally said — the orchestrator's design constraints (sha2 dependency, `sha256(path+query)`) are authoritative and consistent with the crate's existing `sha2`/`hex` deps; no new dependency was needed.
- `reqwest`'s `query` feature isn't enabled in `Cargo.toml` (only `rustls`, `json`), so `RequestBuilder::query` isn't available; the client builds the URL by hand via `reqwest::Url::query_pairs_mut()` instead (no dependency change).
- Search/disambiguation logic lives in a private `metadata::search` submodule (added via `mod search;` in `metadata/mod.rs`) purely to keep `resolve.rs` under the 200-line limit; it is not part of the public surface.
- The crate has no `src/lib.rs` (binary-only), so `tests/tmdb_resolve.rs` cannot `use mediagram::...`. Since the whole `metadata` module tree has zero `crate::`-rooted references (verified by grep), the test support module pulls it in directly via `#[path = "../../src/metadata/mod.rs"] pub mod metadata;` inside `tests/support/mod.rs` — no change to `Cargo.toml`, `main.rs`, or any file outside this phase's ownership.
- `--tvdb` is used to query TMDB's `/find?external_source=tvdb_id` when it's the only explicit id given (still TMDB-only, per the locked decision), but the final `ids.tvdb` is always overwritten with the flag's raw value afterward, so it is stored verbatim regardless of what `external_ids` returned.
- Verification: `cargo fmt --all -- --check`, `cargo clippy --all-targets -- -D warnings`, `cargo test --workspace` all pass; every file in this phase's ownership is under 200 lines.
