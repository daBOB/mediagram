# Phase 01: spec rule for the current channel index

## Context
- `crates/mediagram-core/src/api/channel/index.rs:56-79` (`pick_index`, `pushed_at_of`)
- `crates/mediagram-core/src/api/channel/search.rs:91-100` (own-post filter, then `pick_index`)
- `crates/mediagram-core/src/versions/mod.rs:33` (`FUTURE_TOLERANCE_SECONDS`, also used by `api/refresh/mod.rs:44`)
- `crates/mediagram/src/telegram/download_index.rs:43-61` (uploader's divergent rule)
- `docs/mlib-spec.md` §7 (the normative text)

## Overview
Priority high, blocks phase 03. The uploader chooses "the channel index" with
no own-post filter and no future-clock tolerance, so it can merge a snapshot
no player shows. Move the pure rule into `mlib-spec` so both crates share it.

## Requirements
- `mlib_spec::index_caption::newest(candidates: &[(&str, i64)], now) -> Option<usize>`:
  index captions only; newest by believable `pushed_at` (≤ now + tolerance), message id breaks ties.
- `FUTURE_TOLERANCE_SECONDS` lives in `mlib-spec`; core re-exports or imports it (both call sites keep compiling).
- Core's `pick_index` keeps its errors (`NOTHING_PINNED`, `NOT_AN_INDEX`) and delegates the choice.
- Own-post filtering stays with the caller (needs the Telegram message), documented beside `newest`.

## Steps
1. Move the choice tests from `mediagram-core/src/api/channel/index_tests.rs` that test ordering and tolerance to `mlib-spec` (red).
2. Add `newest` + the constant to `crates/mlib-spec/src/index_caption.rs` (green).
3. Make core's `pick_index` call it; keep the error-mapping tests in core.
4. `cargo test -p mlib-spec -p mediagram-core`.

## Todo
- [x] tests moved, failing
- [x] `newest` + constant in mlib-spec
- [x] core delegates; its tests pass

## Success criteria
One implementation of the rule; `rg "pushed_at_of|max_by_key" crates/*/src` finds no second copy.

## Risks
Changing core's error behaviour by accident. Mitigation: keep core's
error-case tests unchanged.
