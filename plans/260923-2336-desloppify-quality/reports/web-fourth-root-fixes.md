# Server error and cleanup follow-up

Status: focused implementation complete; coordinated full-web gate pending.

Channel discovery and series preload now share the existing package-refresh
failure formatter, extracted into a dependency-free helper. Null, undefined,
string and unprintable rejection values preserve installed/local fallback and
permit the next queued preload, including later requests after the queue drains.
All twelve new cases failed before repair. The resulting package/channel/cache/
state gate passes 117 tests (`/tmp/web-rejection-boundaries-{red,green}.log`).

Transcode cleanup removes only a successfully isolated directory. If the source
directory vanished, it never deletes a replacement from the reusable original
path after process shutdown. Other isolation errors remain visible, the process
is still stopped, and multiple cleanup failures retain their original causes.
Two regression cases failed before repair. Independent source review
[passed](web-transcode-cleanup-review.md).

Poster reads/counts preserve optional-artwork behavior while logging unexpected
filesystem failures with the path and original error. Missing files remain quiet.
Real EISDIR and ENOTDIR fixtures produced the two intended pre-fix diagnostic
failures. The combined transcode/poster gate passes 66 tests
(`/tmp/web-registry-poster-{red,green}.log`).

HLS interface/implementation documentation now describes one release per viewer
acquisition and last-share shutdown; only an absent-session release is harmless
to repeat. Response documentation distinguishes known finite lengths from
indefinite event streams. A private profile lookup/creation helper now names both
operations without changing behavior. TypeScript passes.

The controller also independently reviewed the
[audio shutdown change](web-audio-shutdown-reassessment.md). Admission closes
before draining, cancellation reaches every active probe, and both graceful and
forced child termination are reaped before HTTP/Telegram shutdown. Task/timer
ownership and existing empty-chooser fallback remain coherent. The real-child
regressions and startup-registration mutation support the behavior; no additional
finding was identified.

## Final integration gate and cache review

The coordinated final gate passes **1,608 tests**, 11,542 assertions across 121
files, and `bunx tsc --noEmit`. Logs: `/tmp/web-fourth-full-tests.log` and
`/tmp/web-fourth-full-types.log`. All eleven fourth-review findings are now
resolved through the CLI; the postflight scan remains 92.1 strict pending
reassessment of changed dimensions.

Independent controller review of [thumbnail cache enforcement](web-thumbnail-cache-reassessment.md)
passes. The omitted fetcher is checked before miss batching and bypasses the
readahead tracker entirely. Cached and ordinary responses share the existing
pull/cancel/framing path, and the additive route delegates to the same tested
range and method handling. Eviction/truncation tests cross the real TCP and
filesystem boundaries after admission, asserting zero upstream calls and no
published or partial image. Removal of buffered `read` is confined to this
private application's test-only surface; existing tests exercise `readStream`.
No additional defect was found.

## Postflight mechanical adjudication

The security warning on `telegram/state-channel.ts` is an evidenced false
positive: `SyncEngine.pushIfChanged` serializes its local typed export; incoming
channel documents are separately validated by `parseRecord`. A failure in the
async adapter propagates to `SyncEngine.round`'s catch and produces a retryable
failed outcome. No local parse catch is needed to protect external input.

The `src/login.ts` untested-entry warning also misses executable evidence.
`test/login-setup.test.ts` launches a real Bun child importing that entry and
checks no prompt, connection, output leakage or file creation. The re-exported
implementation has 23 behavior cases covering authentication, cleanup and file
permissions. Both dispositions use supported CLI false-positive classification;
neither is reported as a code repair. Reopening policy remains tool-owned.
