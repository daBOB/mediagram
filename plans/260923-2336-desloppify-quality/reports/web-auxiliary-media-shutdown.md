# Auxiliary media shutdown

Status: DONE_WITH_CONCERNS. Requested shutdown and ensure contracts are implemented and behaviorally verified. The optional broad TypeScript check reports existing diagnostics outside the changed modules/tests.

## Behavior

- `SeriesPreload.stop()` closes admission and discards queued episodes. The current upstream range and its cache writes drain; a guarded fetch prevents later ranges from starting, and remaining parts are discarded. A held-state refresh already in progress is awaited through `onHeld`.
- `SheetStore.stop()` closes admission, aborts retained ffmpeg children, and waits for every ensure task and partial-file cleanup. Children receive SIGTERM with a three-second SIGKILL fallback; exit cancels the timer. Stopping during filesystem preflight cannot launch a later child. Existing single-generation and atomic publication behavior remains.
- `shutdownFor` begins both stops before its first wait and drains them before closing the HTTP listener or disconnecting Telegram. `startPlayer` registers both resources as they are created.
- `ensure` documents that the initiating caller waits for generation/publication while concurrent callers return false. Held-state lookup exceptions are now contained by the same failure handling; only the invocation that claimed a generation slot releases it.

## Evidence

- Three initial lifecycle regressions failed before implementation: shutdown returned while a preload was active, HTTP closure was reached with a live thumbnail child, and SheetStore had no stop method. A fourth callback-failure regression also failed before its repair.
- Focused suite: **36 passed, 0 failed** across application media shutdown/lifecycle/startup, thumbnail sheets, and series preload tests.
- Full Bun suite: **1,403 passed, 0 failed**, 10,289 assertions, 101 files. Log: `/tmp/mediagram-media-shutdown-full.log`.
- The new lifecycle cases execute the actual cache fill and production shutdown code. A real owned Bun child replaces only the ffmpeg executable; production process cancellation is exercised, child exit is observed before listener close, and an actual `.making.jpg` file is removed. A real `startPlayer` listener/POST request verifies resource registration.
- The lead agent independently reviewed shutdown ordering/admission and process handling. `git diff --check` passes. All spawned children, listeners, and test runners exited; no background process remains from this task.

Focused/red logs: `/tmp/mediagram-media-shutdown-focused.log`, `/tmp/mediagram-media-shutdown-red.log`, `/tmp/mediagram-sheets-preflight-red.log`.

## Concerns

`bunx --package typescript tsc --noEmit --pretty false` reports 52 diagnostics outside the modules and tests changed in this batch, including existing fixture types and the unused expect-error in `catalog/artwork-routes.ts`. No diagnostics name the changed shutdown modules or new media shutdown test. Full output: `/tmp/mediagram-media-shutdown-types.log`.

Active Telegram reads are drained rather than forcibly interrupted because the current range boundary does not expose cancellation. No live Telegram, user files, scanner mutations, or commits were used.
