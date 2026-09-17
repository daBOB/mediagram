---
phase: 5
title: "Disk cache"
status: completed
priority: P1
effort: "1d"
dependencies: [2]
---

# Phase 5: Disk cache

## Overview
Cache fetched byte ranges on disk so seeking and re-watching do not refetch
from Telegram. Wherever the player runs, this is the difference between a
scrub bar that costs bandwidth and one that does not.

## Key insight
Downloads arrive as whole aligned chunks at a known offset, so the natural
cache unit is the chunk, not the request. Chunk-aligned caching makes any
Range request a mix of hits and misses with no partial-overlap arithmetic,
and it matches what the transport already does.

The unit is the request size phase 2 settled on, not 512 KiB: teleproto's
legal sizes are 4 KiB multiples that divide 1 MiB. Pick one size, record it
in the cache path, and a file name stays derivable from a byte offset.

## Requirements
- Functional: a chunk present on disk is served without a Telegram call; a
  miss is fetched, stored and served; the cache stays within a size budget.
- Non-functional: corrupt or truncated cache entries must be detected rather
  than served; two concurrent requests for the same chunk fetch it once.

## Architecture
```
<cache_dir>/<chunk_bytes>/<set_id>/<part_idx>/<chunk_index>
```

Chunk index is `offset / chunk_bytes`, so a file name is derivable from a
byte offset with no index. The size sits in the path so changing it retires
the old entries instead of misreading them. Eviction is least-recently-used by access time, run when
the total exceeds the budget.

Size comes from the file itself: a chunk file of the wrong length is a
truncated write and is refetched rather than served.

## Related Code Files
- Create: `web/src/cache/store.ts` (read, write, evict),
  `web/src/cache/key.ts` (pure: offset to path)
- Modify: `web/src/telegram/download.ts` (consult the cache first),
  `web/src/config.ts` (`cacheDir`, `cacheMaxBytes`)

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
