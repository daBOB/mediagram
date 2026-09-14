---
phase: 4
title: "TMDB metadata resolution"
status: pending
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
- [ ] `resolve` for `Severance (2022) - s02e01.mkv` returns tmdb 95396, episode title "Hello, Ms. Cobel" from fixtures
- [ ] Ambiguous search triggers prompt exactly once
- [ ] Cache hit produces zero HTTP calls (assert via fixture client)

## Risk Assessment
- TMDB rate limits during bulk adds → cache + Retry-After; adds are sequential anyway.
- Anime absolute numbering rarely resolvable on TMDB → `--abs` + `--manual` path; store `abs` verbatim.
