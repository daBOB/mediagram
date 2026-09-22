# Phase 04 — Web library switch from a channel's index

## Context links
- Android's decided semantics (reference for this feature — web has none):
  `crates/mediagram-core/src/api/channel.rs:45-70` (`list_libraries`), `:74-87` (`refresh_library`),
  `:108-151` (`newest_index`: pins + `#mlib-index` text search), `:155-172` (`install`: stage → count → swap), `:174-195` (256 MiB cap)
- `crates/mediagram-core/src/api/channel_index.rs:24` (`INDEX_CAPTION_PREFIX`), `:34-53` (error sentences), `:71-102` (`pick_index`, `pushed_at`)
- `android/feature/setup/src/main/kotlin/Libraries.kt` (install before remembering the choice)
- `web/src/package/refresh.ts:270-284` (`swapCurrent`, `removeOtherVersions`, not exported), `web/src/catalog.ts` (`assertSchema`, `listPlayable`)
- `web/src/telegram/state-channel.ts:70` (teleproto pinned filter already used)
- `web/src/login.ts` dialog walk (`iterDialogs`, bot-API id, `accessHash`)
- `tasks/lessons.md` 2026-09-18 — two implementations of one decision must be pinned by a test

## Overview
Priority P2. Status: pending. Web can list the account's channels, pick the newest index
snapshot a channel holds, install it as the catalog, and hand back what 05 needs to
rebuild the runtime — no restart.

## Key insights
- A "library" on Android is a channel whose pins/history carry a `#mlib-index` snapshot of
  `library.db`. Surface Parity: the web matches this rather than inventing a second meaning.
- Chunk cache is keyed by content-hashed set id (`mlib-spec/src/set_hash.rs:1-3`), so a
  switch keeps every cached chunk valid.
- Handles, not channel ids, cross to the browser (same as `LibraryChoice.handle`); access
  hashes stay server-side.
- Channel snapshots carry no artwork. Posters fall back to the env catalog's directory (Q4).
- Package root (`catalogDir`) prunes other versions on refresh; channel installs need their
  own root so the two never delete each other's versions.

## Requirements
- F: `listLibraries(telegram)`: channels + groups, ≤ 500 dialogs, title + opaque handle
  (random, server memory map → `{chatId, accessHash, title}`).
- F: `newestIndex(telegram, peer)`: pinned (≤ 100) + text search `#mlib-index` (≤ 50, allowed to fail);
  `pickIndex` = TS port of `pick_index`; same three error sentences.
- F: `installChannelIndex(document, root, version)`: download ≤ 256 MiB into `incoming/`,
  `assertSchema` + `listPlayable` as proof, then atomic `current` swap; failure leaves current intact.
- F: returns `{indexPath, sets, pushedAt}`; persisting the choice to `telegram.json` is 05's job,
  done **after** install succeeds (Android's order, `Libraries.kt` `install`).
- F: shared fixture of caption cases read by a Rust test and a TS test.

## Architecture
```
GET libraries → iterDialogs → [{handle,title}]  (map kept in memory, rebuilt per listing)
POST library{handle} → map→peer → holder.withChannel(peer)  (temp, not yet current)
   → newestIndex → pickIndex → download → incoming/ → assertSchema+count → swap current
   → return {indexPath,sets} → (05) openLibraryRuntime → setRouter → persist choice → holder.withChannel
```
Channel catalog root: `MEDIAGRAM_CHANNEL_CATALOG_DIR`, default `~/.cache/mediagram-channel-catalog`.

## Related code files
Create: `web/src/package/channel-index.ts` (pure: prefix, `pickIndex`, `pushedAt`, sentences),
`web/src/telegram/libraries.ts` (dialogs, newest index, download),
`web/src/package/channel-install.ts` (stage/verify/swap),
`web/test/fixtures/index-caption-cases.json`, `web/test/channel-index.test.ts`,
`web/test/channel-install.test.ts`.
Modify: `web/src/package/refresh.ts` (export `swapCurrent`, `removeOtherVersions` — no behaviour change),
`crates/mediagram-core/src/api/channel_index.rs` (test module reads the shared fixture via
`CARGO_MANIFEST_DIR/../../web/test/fixtures/…`, skipping when absent, like `shared_playable_sql.rs`).
(`channelCatalogDir` config field is added in 01, so 04 does not touch `config.ts`.)
Delete: none.

## Implementation steps
1. Write the fixture: pinned+unpinned, unreadable timestamp, tie on timestamp (higher id wins), empty list, no marker → expected index or error kind.
2. `channel-index.ts` + TS test over fixture; Rust test over same fixture.
3. `channel-install.ts` with tmp-dir tests: good db installs; non-sqlite / wrong schema never replaces current; oversize aborts.
4. `libraries.ts`: teleproto calls (verify exact signatures in teleproto typings first — [UNVERIFIED] `iterDialogs`, `getMessages({filter, search, limit})`, `iterDownload`/`downloadMedia`).
5. Export helpers from `refresh.ts`; rerun `package-refresh.test.ts`.

## Todo
- [ ] shared fixture + Rust test + TS test
- [ ] channel-install + tests
- [ ] libraries.ts (teleproto) with fakeable client seam
- [ ] refresh.ts exports

## Success criteria
- Rust and TS tests both read the fixture and agree; editing one case breaks both.
- Install test proves a bad snapshot leaves the previous `current` untouched.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| TS/Rust pick logic drift | M×H | shared fixture test on both sides |
| Downloading a hostile/huge doc | L×H | 256 MiB cap during stream; verify before swap |
| Dialog listing leaks account's channel list | M×H | only via admin-gated route (01/05); handles only |
| State sync documents pinned in the same channel | L×M | prefix filter already excludes them |

## Security
Access hashes never serialised to the browser; the downloaded file is opened read-only.

## Next steps
05 exposes list/choose and performs the runtime swap + persistence.
