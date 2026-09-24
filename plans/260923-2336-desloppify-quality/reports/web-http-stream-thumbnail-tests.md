# HTTP stream and thumbnail handoff tests

Status: DONE
Date: 2026-09-24

## Changes

Only `web/test/server-backpressure.test.ts` and new `web/test/thumbnail-route-handoff.test.ts` changed. No production sources, scanner state, config, or commits were changed.

The backpressure integration test now uses a genuinely paused `node:net` TCP reader. An observer delegates every call to the original `ServerResponse.write`, including its callback, and records an actual `false` result that still has a drain waiter after an IO turn. The test disconnects only then. It proves the client consumed no data, no drain completed before the server observed the disconnect, the byte source was cancelled, and the write/pump drain, close and error listeners were removed. This last assertion detects a suspended pump even though the independent close listener already cancels the source.

There are no fixed success sleeps. Events and promises drive progress; deadlines only fail a stalled test. The isolated close/error/drain tests remain, with the old close-test sleep replaced by awaiting its actual rejection. Cleanup restores the response prototype, destroys the client, closes the listener and SQLite handle, and releases a stranded waiter if a mutation intentionally breaks the implementation.

The thumbnail tests use the real `createRouter`, a real temporary SQLite file containing a playable set, and real `SheetStore` filesystem operations. Only the ffmpeg process boundary is replaced. Its runner writes the established JPEG byte fixture to the requested partial path, then waits on an explicit exit gate. Filesystem rename/removal events observe actual publication and cleanup.

The three new cases verify:

- Missing GET/HEAD previews return a bodiless 404 while generation remains pending; repeated requests share one generation. Successful exit publishes atomically, then GET returns the exact JPEG with type, length and cache headers, while HEAD preserves headers and returns no body.
- Nonzero ffmpeg exit removes its partial JPEG and leaves the route unavailable.
- Unknown catalog sets trigger neither held-state lookup nor generation and cannot serve a leftover sheet.

## Validation

```text
bun test test/server-backpressure.test.ts test/thumbnail-route-handoff.test.ts \
  test/thumbs-sheets.test.ts test/posters.test.ts
30 pass / 0 fail, 127 assertions

bunx --no-install tsc --noEmit
Earlier run passed with zero diagnostics; latest run has two concurrent,
unowned application-media-endpoint.test.ts diagnostics subsequently fixed
by its owning worker (see below).

git diff --check -- test/server-backpressure.test.ts test/thumbnail-route-handoff.test.ts
Passed
```

The seven tests in the two owned files also passed ten consecutive runs to check the event/IO synchronization. Logs: `/tmp/mediagram-http-stream-thumbnail-{focused,repeat,types}.log`.

Four mutation checks ran only in `/tmp/mediagram-http-test-mutations-wwrunz28`, copied from the current source. Each failed the intended new assertion, and the copy was restored after each check:

| Mutation | Observed failure |
| --- | --- |
| Remove the write wait's close/error settlement listeners | The real socket test finds one stranded drain listener after disconnect. |
| Await `sheets.ensure` inside the thumbnail route | The initial 404 cannot return while the ffmpeg exit gate remains closed; the response deadline fails. |
| Publish output despite a nonzero ffmpeg exit | A final six-byte JPEG exists when the failure case requires no published sheet. |
| Return image bytes for HEAD | The successful handoff test receives a JPEG body where HEAD requires null. |

The first typecheck exposed an overloaded `ServerResponse.write` instrumentation signature mismatch; preserving the original overload type fixed it and the next whole-web check passed. The final rerun, after concurrent changes, reported only `test/application-media-endpoint.test.ts:55` (`string | undefined` argument) and `:92` (`unknown` playlist response). These unowned diagnostics were sent to the controller; that file was not edited here. The controller subsequently confirmed its owning worker corrected both errors and reported a full-web result of **1,556 passing tests**. No additional validation was repeated after that handoff.

No runtime failures remain in the owned tests. No media encoder, Telegram session, user database, or external network was used by this task.
