# Audio probe and byte-stream ownership

Status: DONE

## Changes

`AudioTrackReader` now keeps pending probes in a map keyed by set ID. Concurrent
readers of one title await the same task; different titles still probe
independently. Successes, empty results and thrown probe failures retain the
existing result/negative-cache contract. Shutdown closes admission, aborts and
drains the same map, so coalescing does not detach work or publish late results.

`TelegramSource` cancellation now returns the promise for asynchronous iterator
cleanup. It tracks an active `next()` alongside `return()`: an async generator
queues return behind next, and a cleanup failure already unwinding inside next
would otherwise be lost when the queued return resolves normally. Cancellation
awaits both outcomes before preserving their rejection. A late pull cannot
enqueue into the cancelled stream or increment normal failed-read statistics.

The HTTP server already attaches a catch to `reader.cancel()`, so no server
change was needed. The ordinary and disk-only byte paths share this framing;
disk-only cache policy, normal range framing and upstream batching are unchanged.

Owned production files: `web/src/catalog/audio-tracks.ts` (189 lines) and
`web/src/telegram/source.ts` (200 lines). No API signature changed.

## Regression evidence

Pre-fix command from `web/`:

```sh
bun test test/audio-tracks.test.ts test/audio-shutdown.test.ts \
  test/telegram-stream-cancel.test.ts
```

`/tmp/web-probe-stream-before.log`: **13 pass, 8 fail, 1 unhandled error**.
The three gated concurrent-probe cases observed duplicate calls; the shutdown
case also started a duplicate same-set probe. Cancellation resolved before
cleanup, and the deliberately rejected cleanup produced the actual detached
promise error described by the finding.

After repair, those tests passed **21/21 with 85 assertions** in
`/tmp/web-probe-stream-focused.log`. Added a real local TCP abandonment test
afterward. It waits for the actual server response-close event before releasing
the blocked iterator, then lets cleanup reject. The production server's existing
cancellation catch handles that rejection without an unhandled promise or a
spurious failed-read count.

The final focused gate:

```sh
bun test test/audio-tracks.test.ts test/audio-shutdown.test.ts \
  test/telegram-stream-cancel.test.ts test/telegram-source.test.ts \
  test/cache-source.test.ts test/thumbnail-cache-only.test.ts \
  test/server-backpressure.test.ts test/application-media-shutdown.test.ts \
  test/application-media-endpoint.test.ts
```

**66 pass, 0 fail, 268 assertions across 9 files** in 4.16 seconds;
`/tmp/web-probe-stream-verified.log`. This retains actual child termination,
IPv4/IPv6 endpoint, backpressure, cache batching and thumbnail disk-only checks.

Whole-web `bunx --package typescript tsc --noEmit --pretty false` passed;
`/tmp/web-probe-stream-types.log`. One new test's fixed-length result indexing
needed a non-null assertion; after that type-only correction its 13 audio tests
passed again in `/tmp/web-audio-coalescing-verified.log`. Scoped diff whitespace
checks passed.

## Handoff

The new tests control prober/Telegram IO with real asynchronous gates and
generators. The HTTP case uses a real listener and TCP client; every listener,
socket, child and gate is closed or settled during cleanup. No live Telegram,
user database, scanner state, manifests or commits were touched.

Root owns independent review and the coordinated whole-Bun gate. No known
remaining defect or owned running process remains in this slice.
