# Audio probe shutdown ownership

Status: DONE — focused implementation and verification complete; root owns independent review and the coordinated whole-web gate.

`AudioTrackReader` now closes admission before waiting, cancels all active probes through an AbortSignal, and awaits every read. New reads after stop return an empty chooser without invoking the prober, including previously cached sets. Late successful results from cancelled probes are discarded. Normal successful/failure caching remains unchanged.

The real ffprobe boundary receives that signal, sends SIGTERM on cancellation, escalates to SIGKILL after three seconds, and always awaits child exit. The existing 20-second probe limit remains. Timer/listener cleanup runs for success, cancellation, timeout, and stdout failure. A stdout failure also terminates a still-running child before returning the reader's existing empty-result fallback.

Startup registers the reader with `ApplicationResources`. Ordered shutdown stops it alongside thumbnail/preload work before closing HTTP or disconnecting Telegram. No network, response, audio-track ordinal, or chooser error contract changed. The injected `Prober` type gains an AbortSignal parameter; existing one-argument implementations remain assignable, and all actual callers were checked.

## Evidence

- `/tmp/web-audio-shutdown-before.log`: **0 passed, 3 failed**. The reader had no stop operation; lifecycle closed server/Telegram while a probe was pending; actual `startPlayer.stop()` returned while its owned child remained alive (`signalCode` was null).
- `/tmp/web-audio-shutdown-focused.log`: **27 passed, 0 failed, 154 assertions, 5 files**. Covers four new lifecycle cases plus existing audio parsing/caching, application shutdown, and actual IPv4/IPv6 media endpoint cases.
- The production-startup regression replaces only the external executable at `Bun.spawn`, retaining the actual reader, prober, HTTP route, and startup/shutdown composition. It verifies the real child PID exists before shutdown and no longer exists afterward, with probe exit preceding Telegram disconnect.
- A second real child installs a SIGTERM handler that refuses to exit. Shutdown escalates after three seconds, reaps it, settles the read, and refuses further work.
- A controlled prober returns valid data after cancellation: shutdown waits for it, and both overlapping reads return empty instead of publishing cancelled results.
- Isolated source mutation removing only `resources.audio = audio` fails the production PID regression; restoring the line passes. Logs: `/tmp/web-audio-shutdown-unregistered.log` and `/tmp/web-audio-shutdown-restored.log`. Repository production files were not mutated for this check.

Scoped diff whitespace validation passed. Every owned test child and local listener was closed, and all test processes exited. No live Telegram connection, user database, dependency, manifest, or scanner state was changed.

The full Bun/TypeScript gate remains coordinated by root after parallel browser file moves. No known runtime blocker remains in this slice.
