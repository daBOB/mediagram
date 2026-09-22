# Phase 02 — Classify updates (shared fixture)

## Overview
Priority P2. Status: **done 2026-09-22** (uncommitted). 25 fixture cases (19 classify, 6 debounce) pass on both
suites; a flipped expectation fails exactly one case in each (mutation-checked). A pure function on each side that turns one raw update into an event or nothing.
Pinned by one fixture both test suites read, so the two surfaces cannot decide differently (CLAUDE.md § Surface Parity).

## Requirements
- `classify(update, channelId, ownDevice) -> StateChanged | IndexChanged | None`
  - edit/new message in the library channel whose caption starts `#mlib-state` and whose device ≠ own → `StateChanged`
  - new message with `#mlib-index`, or `updatePinnedChannelMessages` for the channel → `IndexChanged`
    (spike: both arrive for one push; the debouncer folds them)
  - `MessageService` pin notices → none
  - `updatePinnedChannelMessages` with `pinned=false` (the old index unpinned in the same push burst) → none
  - everything else, other channels, own echo → none
- Caption parsing reuses what exists: `deviceFromCaption` (`web/src/telegram/state-channel.ts`), the index marker
  from `push_index.rs`; Rust mirrors them in core (03's watch-state work already parses state captions; reuse it if landed).
- `Debouncer`: at most one event per kind per 5 s, trailing edge.

## Related code files
Created (fixtures moved to where the watch-state ones already live, `web/test/fixtures/<topic>/`):
`web/test/fixtures/channel-updates/{classify,debounce}.json`, `web/src/telegram/updates.ts`,
`web/test/channel-updates-fixtures.test.ts`, `crates/mediagram-core/src/updates.rs`,
`crates/mediagram-core/tests/shared_channel_update_fixtures.rs`.
Modified: `crates/mediagram-core/src/lib.rs` (`pub mod updates`), `api/mod.rs` + `api/channel_index.rs`
(`INDEX_CAPTION_PREFIX` now `pub(crate)`, reused rather than duplicated).

## Decisions made while building
- Update shape is language-neutral `{kind: new|edit|pinned|delete|other, channel, caption?, service?, pinned?}`;
  03/05 adapters map raw grammers/teleproto updates into it.
- Debounce window is timed from the **first** event and not reset, so a long upload pushing indexes every few
  seconds can't starve it. `nextDue()` lets the adapter arm one timer.
- When both kinds fall due together they come out `state`, then `index`, on both ports.
- `device_from_caption` in Rust reproduces the web regex `/\bdevice=(\S+)/` exactly, word boundary included (fixtures cover it).

## Success criteria
Both suites pass on the same fixture: own echo ignored, foreign channel ignored, `#mlib` part captions ignored
(the hashtag trap in `state-channel.ts:20`), pin → IndexChanged.
