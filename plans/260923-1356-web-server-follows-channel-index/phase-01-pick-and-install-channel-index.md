# Phase 01 — Pick and install the channel's newest index

Priority: high · Status: todo

## Context
- Core selection: `crates/mediagram-core/src/api/channel/index.rs` (`pick_index`),
  `mod.rs` (`newest_index`: pins + marker search, posts only)
- Core install: `crates/mediagram-core/src/api/channel/install.rs`
- Caption spec: `crates/mlib-spec/src/index_caption.rs` (`#mlib-index v=2` + JSON `pushed_at`)
- Web reuse: `web/src/package/refresh.ts` (`swapCurrent`, `removeOtherVersions`),
  `web/src/telegram/state-channel.ts` (pinned-message listing on the player's client)

## Requirements
- `pickIndex(candidates, now)`: pure TS port of `pick_index` incl. future-tolerance rule.
- `newestIndex(telegram)`: pinned search + `#mlib-index` query search, dedupe, posts only.
- `installIndex(root, message)`: stream download with 256 MiB cap into `incoming-*`,
  open read-only, `assertSchema` + count playable (proof), rename `v-<pushed_at>`,
  atomic `current` swap. Refuses a snapshot no newer than the installed one.
- Old versions removed only after the live catalog has let go of them (phase 02).

## Files
- create `web/src/channel-index/pick.ts`, `web/src/channel-index/install.ts`
- modify `web/src/package/refresh.ts` (export swap helpers, no behaviour change)
- create tests; add a Rust+TS shared fixture for `pick_index` like
  `channel-updates-fixtures`

## Todo
- [ ] port `pick_index` + shared fixtures (both languages read one file)
- [ ] newest-index search on the existing client
- [ ] staged install with cap + proof + swap
- [ ] tests: not-a-library never swaps; older snapshot refused; cap enforced

## Success
A snapshot pinned by hand, one left unpinned, and a newer unpinned one all
resolve to the same choice core makes (fixture-checked).

## Risks
- `downloadMedia` buffers whole files — use the chunked `iterDownload` path.
