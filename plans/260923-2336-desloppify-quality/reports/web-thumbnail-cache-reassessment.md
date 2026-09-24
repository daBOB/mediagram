# Thumbnail cache enforcement and streaming reader consolidation

Status: DONE_WITH_CONCERNS

## Result

Thumbnail generation now uses `/api/sets/:id/cached-stream`. `HeldSets.has()` is
still the inexpensive admission check, but actual delivery checks each chunk's
size on disk. An evicted or truncated chunk fails the stream without fetching
from Telegram. Disk-only reads also bypass speculative readahead, even when the
requested chunks are all cached. A failed generator removes its partial JPEG.

`CachedReadRequest.fetch` explicitly defines the policy: supplied means normal
cache-backed upstream reads; omitted means disk-only delivery. `TelegramSource`
shares the existing pull/cancel/error framing between its ordinary and cached
stream methods. Application startup installs the cached source only when caching
is enabled. The additive route supports GET/HEAD, full and single-range reads,
416 for an unsatisfiable range, 405 for writes, and 404 for absent support or an
unknown title. Existing playback `/stream` behavior is unchanged.

Removed the separate buffered `CachedReader.read` algorithm. Repository searches
found only test callers; this private application has no package exports. The
existing byte comparison tests now collect the production `readStream` generator
through a test helper. Tests for alignment, batching, short reads, readahead,
preloading, cancellation and bounded streaming remain intact.

Updated the owning architecture paragraph and thumbnail reader comments. Prior
audio shutdown registration, root's preload error diagnostics and concurrent
browser module changes were preserved.

## Evidence

The first red run exposed the new route and readahead failures, but its thumbnail
fixture omitted `parts.chat_id` and therefore did not reach upstream fetching.
After correcting the fixture, replayed the actual original `reader.ts`,
`source.ts`, `routes.ts` and `sheets.ts` from HEAD in an isolated temporary copy:

- `/tmp/web-thumbnail-cache-old-behavior.log`: **0 pass, 3 fail**. Eviction and
  truncation each caused one Telegram boundary call; the sequential cached read
  also touched an unrequested third chunk. Two additional route tests were
  intentionally filtered because the old API does not contain that route.
- `/tmp/web-thumbnail-cache-restored.log`: restored current production files,
  **5 pass, 0 fail, 32 assertions**. Original workspace files were never reverted.
- Copy: `/tmp/mediagram-thumbnail-cache-mutation-stt2i55x`; copied authored source,
  public assets, required fixtures and package metadata; existing dependencies
  were linked read-only by convention. No manifests or dependencies were changed.

The new eviction/truncation tests use a real temporary chunk cache, database,
held scan, SheetStore, TelegramSource, router and TCP listener. Only the external
process boundary and Telegram network boundary are controlled. Each test changes
the filesystem after thumbnail admission, requests the actual generated URL,
asserts the cache-miss stream diagnostic, zero upstream calls, false publication,
and absence of both final and partial images. All listeners close in `finally`.

Focused command from `web/`:

```sh
bun test test/thumbnail-cache-only.test.ts test/cache-source.test.ts \
  test/cache-store.test.ts test/series-preload.test.ts test/thumbs-sheets.test.ts \
  test/thumbnail-route-handoff.test.ts test/application-media-endpoint.test.ts \
  test/application-media-shutdown.test.ts test/cache-held.test.ts \
  test/cache-key.test.ts test/cache-strategy.test.ts test/range.test.ts \
  test/telegram-source.test.ts test/thumbs-args.test.ts
```

**146 pass, 0 fail, 501 assertions, 14 files** in 1.119 seconds; log
`/tmp/web-thumbnail-cache-focused.log`. Includes real media child Range reads
through application startup for IPv4, IPv6, wildcard binds and ephemeral ports.
Scoped `git diff --check` passed.

## Integration concerns

Root owns independent review and the coordinated whole-web/typecheck gate; those
were deliberately not duplicated while related edits were in progress. No
remaining defect was identified in local contract and failure-path review.
The held badge can still become stale; it only admits work and no longer grants
permission to fetch missing thumbnail bytes. No live Telegram traffic, user
database writes, scanner updates or background processes remain from this task.
