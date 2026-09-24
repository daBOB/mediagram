# HTTP stream shutdown drain

Status: DONE

## Cause and repair

`startServer` detached each stream pump, and the pump's response-close listener detached `reader.cancel()`. `RunningServer.close()` therefore finished when HTTP connections closed even though a Telegram-backed generator still owned a pending pull and asynchronous cleanup. The application then disconnected Telegram before that cleanup finished.

`web/src/server.ts` now tracks each complete request, including its pump. Closing the listener stops admission, destroys tracked responses, closes connections, and waits for the request tasks. Repeated close calls share one promise. A pump retains and awaits its cancellation promise, releases its reader lock, and diagnoses a rejected cancellation without creating an unhandled rejection. A stream obtained after its response has already closed is cancelled too; tracking the entire request includes this routing interval.

No change to `application/lifecycle.ts` was needed: its existing awaited server-close step now provides the promised boundary before Telegram disconnect.

[Node's HTTP documentation](https://nodejs.org/api/http.html#servercloseallconnections) recommends calling `closeAllConnections()` after `close()` to avoid admitting another connection between the two. Under the installed Bun 1.4.2 runtime, that ordering alone left the test's active response open. Explicitly destroying tracked responses preserves the admission ordering and makes cancellation observable in the real runtime.

## Regression evidence

New file: `web/test/server-stream-shutdown.test.ts`.

- The first regression uses the real listener, routing, `TelegramSource`, and application shutdown. Only the raw Telegram connection/download boundary is controlled. Its async generator pauses both an active pull and its `finally` cleanup. Before the repair, shutdown completed while both were still pending: `/tmp/web-http-shutdown-red.log` contains the runtime assertion `Expected: false / Received: true` for shutdown completion.
- The final matrix verifies that disconnect happens only after cleanup finishes, including when cleanup rejects. It checks that rejection is diagnosed and that repeated listener-close calls return the same promise.
- A separate real HTTP request pauses routing until after its socket closes, then produces a body with gated cancellation. Close remains pending until that late body's cleanup completes.
- The root's broader gate then caught two existing thumbnail diagnostic assertions: cancelling an already-errored stream repeated its previously logged failure. The pump now suppresses only an `Object.is`-identical failure it already reported. Existing thumbnail assertions remain unchanged. Additional actual errored streams verify one diagnostic for Error, undefined and NaN failures; the shutdown matrix verifies that a distinct undefined cleanup rejection remains diagnosed.

An initial fixture lacked the non-null part chat ID required to stream from the catalog. It was corrected before recording the behavioral red result; its setup timeout is not claimed as regression evidence.

Final command:

```sh
bun test test/thumbnail-cache-only.test.ts test/server-stream-shutdown.test.ts test/server-backpressure.test.ts \
  test/server-startup.test.ts test/application-media-shutdown.test.ts \
  test/application-media-endpoint.test.ts
```

`/tmp/web-http-shutdown-focused.log`: **30 passed, 0 failed, 184 assertions across 6 files** on the final diagnostic correction. This includes existing real-socket backpressure, endpoint binding, media worker shutdown, startup, and thumbnail diagnostic coverage. `bun x --no-install tsc --noEmit` passed with empty output in `/tmp/web-http-shutdown-types.log`. Scoped `git diff --check` passed. Root independently reviewed the initial shutdown implementation and is running the final broad web gate; no full-suite success is claimed here.

## Scope and cleanup

Only `web/src/server.ts`, the new focused test, and this report were modified for this task. No dependencies, manifests, versions, lifecycle ordering, scanner state, or git index were changed. Each regression releases its gates, aborts its owned client, closes its listener, and closes its in-memory database in cleanup. All owned Bun commands exited; the focused media test also verified its owned child was terminated and reaped. No live Telegram or user media was accessed.

Concerns: None within scope. Shutdown intentionally waits for upstream cancellation to finish; it does not add a timeout or claim that arbitrary upstream work can be forcibly interrupted.
