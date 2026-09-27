# Phase 01 — Cache server: per-film status route

## Context links

- Server: `crates/mediagram-cache/src/http.rs:30-168` (routes; `/v1/status` at 158-168),
  `store.rs:63-162` (layout `root/<id>/<n>`, `root/<id>/total`; GET/HEAD), `store/scan.rs`,
  `store/total.rs`, `store/evict.rs:20-38` (`remove_set_dir_if_empty` — the same dir scan),
  `rules.rs:9-55` (id pattern `^[A-Za-z0-9]{1,64}$`, 1 MiB chunks, last short).
- Android client: `android/core/playback/src/main/kotlin/LanChunkClient.kt:34-192`,
  `LanChunkSigning.kt:42-52` (`LanServerStatus` regex parse), `LanCacheRuntime.kt:32-41`.

## Overview

P2 · done. Add `GET /v1/sets/{id}` → `200 {"total": u64|null, "chunks_held": u64,
"bytes_held": u64}` (404 only for an invalid id; an unknown film is `200` with zeros and
`total: null`). Open like the other reads (only PUT is authenticated). Must not count as
use (no mtime touch, no LRU reorder) — it is a question, not a read.

## Requirements

- Answer from the in-memory index if it already tracks per-set chunk sizes; otherwise one
  directory scan of `root/<id>` (numeric file names only, sizes summed). Pick whichever
  the store already makes cheap — read `store/index.rs` first.
- Consistent under concurrent PUT/evict (same locking discipline as HEAD).
- Tests beside the existing ones (`store_tests.rs`/http tests): empty, partial, complete,
  after eviction, invalid id → 404, does not touch mtime/LRU.
- Android: `LanChunkClient.setStatus(baseUrl, setId): LanSetStatus?` — null on 404
  (older server), network error or bad body; parsed the same way `LanServerStatus` is.
  Unit test with the existing fake-server/HTTP test pattern in core/playback.

## Related code files

- Modify: `crates/mediagram-cache/src/{http.rs,store.rs}` (+ `store/index.rs` if the
  count lives there), tests; `android/core/playback/src/main/kotlin/{LanChunkClient.kt,
  LanChunkSigning.kt}` (or a new small `LanSetStatus.kt`), tests.
- Docs: the cache server's route list wherever `/v1/status` is documented
  (`docs/` — grep `v1/status`).

## Steps

1. Read the store; implement the count; route; tests (`cargo test -p mediagram-cache`).
2. `cargo clippy -p mediagram-cache` clean.
3. Android client + test (`./gradlew :core:playback:testDebugUnitTest`).
4. Docs line. Minor bump by regex (three manifests), changelog entry. No commit — lead
   reviews. Do NOT install or run the server as a service on this machine.

## Todo

- [x] count per set (index or scan)
- [x] `GET /v1/sets/{id}` + tests
- [x] clippy clean
- [x] `LanChunkClient.setStatus` + test (404 → null)
- [x] docs, version, changelog

## Success criteria

`curl http://<box>:7788/v1/sets/<id>` answers held chunks/bytes for a film the tablet
has played; an old server makes the Android call return null without errors.

## Risks

- A scan of a 5,500-chunk directory per poll: fine at the page's poll rate (seconds),
  but prefer the index if it already knows.

## Security

Read-only, same exposure as HEAD (the LAN already sees which ids exist). Validate the id
with the existing rule before touching the filesystem (path traversal).
