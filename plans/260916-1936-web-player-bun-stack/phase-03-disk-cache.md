---
phase: 3
title: "Disk cache"
status: pending
priority: P1
effort: "1d"
dependencies: [1]
---

# Phase 3: Disk cache

## Overview
Cache fetched byte ranges on the homelab disk so seeking and re-watching do
not refetch from Telegram.

## Key insight
Downloads arrive in fixed 512 KiB chunks at a known offset, so the natural
cache unit is the chunk, not the request. Chunk-aligned caching makes any
Range request a mix of hits and misses with no partial-overlap arithmetic,
and it matches what the transport already does.

## Requirements
- Functional: a chunk present on disk is served without a Telegram call; a
  miss is fetched, stored and served; the cache stays within a size budget.
- Non-functional: corrupt or truncated cache entries must be detected rather
  than served; two concurrent requests for the same chunk fetch it once.

## Architecture
```
<cache_dir>/<set_id>/<part_idx>/<chunk_index>
```

Chunk index is `offset / 512 KiB`, so a file name is derivable from a byte
offset with no index. Eviction is least-recently-used by access time, run when
the total exceeds the budget.

Size comes from the file itself: a chunk file of the wrong length is a
truncated write and is refetched rather than served.

## Related Code Files
- Create: `crates/mediagram/src/serve/cache.rs`
- Modify: `crates/mediagram/src/serve/stream.rs` (consult the cache first),
  `crates/mediagram/src/config.rs` (`cache_dir`, `cache_max_bytes`),
  `config.example.toml`

## Implementation Steps
1. Chunk path derivation, pure and tested.
2. Read path: hit when the file exists and has the expected length; the last
   chunk of a part is shorter, so the expected length depends on the part.
3. Write path: write to a temporary name and rename, so an interrupted write
   cannot leave a short file that looks valid.
4. Single-flight: concurrent requests for one chunk wait on the first fetch
   rather than each issuing their own.
5. Eviction: when total size exceeds the budget, delete least-recently-used
   chunks until under it. Never evict a chunk currently being served.
6. Report hit rate at the end of a stream, so the effect is visible.

## Success Criteria
- [ ] A second play of the same title issues no Telegram download
- [ ] Seeking backwards into already-played content is served from cache
- [ ] A truncated chunk file is detected and refetched, not served
- [ ] Two concurrent requests for one chunk cause one fetch
- [ ] The cache stays within its budget under sustained playback
- [ ] Eviction never removes a chunk being served

## Risk Assessment
- **Disk pressure.** The host has 899 GB free; a budget in the tens of
  gigabytes is safe, and the budget must be enforced rather than advisory.
- **Cache poisoning by a stale file reference.** If Telegram returns different
  bytes for the same offset after a re-upload, cached chunks are wrong. Key
  the cache by `set_id`, which is minted per upload, so a re-upload is a
  different key.
- **A cache that hides a bug.** Phase 1's correctness tests must run with the
  cache disabled as well as enabled.
