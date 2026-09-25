# Fifth web lifecycle independent review

Status: DONE — PASS

No confirmed defect found in the reviewed cache inventory, idle reaper,
audio-probe coalescing or stream-cancellation changes. This was an independent
read-only source/test review; only this report was written.

## Reviewed contracts

| Boundary | Source and evidence | Assessment |
| --- | --- | --- |
| Cache inventory and playback | [ChunkCache](../../../web/src/cache/store.ts), [cache-store tests](../../../web/test/cache-store.test.ts), [CachedReader](../../../web/src/cache/reader.ts) | Both recursive directory reads and entry stats suppress only actual Error objects carrying `ENOENT`. Missing roots/subtrees and vanished entries remain harmless. ENOTDIR, ELOOP and EACCES reject explicit `sizeOnDisk`/`evict` instead of producing partial totals. `writeChunk` still catches maintenance failures after its atomic write, so `CachedReader` can deliver already fetched bytes. The permission regression exercises this actual path and checks its warning plus explicit-operation failures. |
| Idle reaper ownership | [TranscodeRegistry](../../../web/src/transcode/registry.ts), [registry tests](../../../web/test/transcode-registry.test.ts), [HLS server](../../../web/src/transcode/server.ts) | Every queued candidate is checked against both current tracked-object identity and the original idle deadline after earlier process cleanup awaits. There is no await between that check and `stop` synchronously removing the selected map entry. A touch, acquisition or deterministic-ID replacement therefore prevents stale work from stopping the current session. The replacement regression deliberately makes its timestamp old enough that only identity protects it. The returned count measures sessions actually stopped by this pass. |
| Coalesced audio probes | [AudioTrackReader](../../../web/src/catalog/audio-tracks.ts), [audio tests](../../../web/test/audio-tracks.test.ts), [shutdown tests](../../../web/test/audio-shutdown.test.ts) | A pending promise is registered by set ID before async completion; matching callers share it while other IDs probe independently. Completion removes its entry, and successful, empty and failed results retain the existing cache contract. `stop` closes admission by aborting before draining all pending map values. Late successful probe bytes are discarded, cached reads are also refused after stop, and coalescing does not create untracked children. Existing production-startup tests still verify real child termination/reaping before HTTP/Telegram teardown. |
| Stream cancellation and cleanup | [TelegramSource](../../../web/src/telegram/source.ts), [cancellation tests](../../../web/test/telegram-stream-cancel.test.ts), [HTTP pump](../../../web/src/server.ts) | Cancellation marks the stream before requesting iterator return and awaits both the captured in-flight `next()` and `return()`. This covers cleanup already unwinding through the pull, whose failure a later successful return alone would miss. `allSettled` observes both promises; any rejection is returned to the cancelling caller. The late pull checks cancellation before close/enqueue and before incrementing failed-read statistics. The HTTP pump's existing cancellation catch consumes cleanup rejection after socket abandonment. |

The cache tests use real temporary paths, a dangling symlink, a symlink loop and
an unreadable subtree. The permission test is intentionally skipped for root;
the provided non-root run executes and passes it. The dangling link exercises
the same `stat` ENOENT handling used for a listing/stat removal race without a
timing-dependent deletion fixture.

The reaper tests use an injected clock and gated process stop, not elapsed-time
margins. They check surviving sessions and exact stop counts for touch,
acquisition and replacement. Audio tests gate production reader calls and cover
tracks, no tracks, rejected probes and a repeated same-set read during shutdown.
Stream tests execute the production framing over real async generators, including
cleanup that fails during the active pull or during return. A real listener/TCP
abandonment case checks the HTTP caller's handling of the rejected cleanup.

## Caller and scope checks

- HLS output reads call `touch`, and acquisition updates activity; both are
  covered by the reaper's second check. The startup timer still catches reaper
  cleanup errors, and shutdown retains the existing admission/drain behavior.
- Inventory failures now reach explicit callers. The status route deliberately
  retains its existing memoized last-reading/initial-zero fallback; this patch
  does not promise a new status UI error presentation. Startup inventory errors
  reach the existing startup error/cleanup boundary.
- The audio route uses the same reader instance; application shutdown calls its
  stop before closing HTTP and Telegram. The real prober's subprocess ownership
  code is unchanged by the coalescing delta.
- Ordinary and cache-only Telegram streams share `streamBytes`; disk-only cache
  policy, chunk batching and range framing remain unchanged. Cancellation waits
  for an already pending iterator operation to settle; it does not introduce an
  abort API inside the Telegram transport. No claim of live Telegram coverage is
  made.

## Validation inspected

No redundant test run was started. Existing cause-aligned evidence was checked
against the changed assertions and production code:

- `/tmp/web-cache-reaper-red.log`: **53 passed, 6 failed**. Three failures expose
  swallowed inventory errors and three expose stale queued reaper decisions.
- `/tmp/web-cache-reaper-green.log`: **59 passed, 0 failed**, 133 assertions
  across both affected suites.
- `/tmp/web-probe-stream-before.log`: **13 passed, 8 failed, 1 unhandled error**;
  duplicate probes and detached cancellation cleanup reproduce the defects.
- `/tmp/web-probe-stream-verified.log`: **66 passed, 0 failed**, 268 assertions
  across nine audio/stream/cache/backpressure/application suites, including the
  later real HTTP abandonment regression. The implementation's
  [ownership report](web-probe-stream-ownership.md) identifies exact commands.
- The final whole-web typecheck also passed during the immediately preceding
  formatting task, after the audio-test narrowing correction:
  `/tmp/web-remaining-failure-tsc.log`.

No production, test, dependency-directory, scanner-state or git mutation was
performed. No process was started. The parent retains the coordinated full Bun
gate and final integration/commit decisions.

Concerns/Blockers: none found within this review scope. Transport cancellation
timing and live-service behavior remain outside the offline regression evidence.
