---
report_date: 2026-09-25
agent: fullstack-developer
worktree: /home/andre/Workspace/mediagram-android-lan-cache
branch: feat/android-lan-cache
phase: plans/260925-2046-external-cache-volume-and-lan-chunk-server/phase-04-android-reads-through-the-lan-server.md
---

# Android reads through the LAN server — implementation + review fixes

**Status:** DONE

Implemented phase 4 strictly TDD, then addressed a full code-review pass
(C1, C2, H1–H4, M1, M2, M3, M5, two Low items) in the same worktree, also
TDD. All work is on `feat/android-lan-cache` in
`/home/andre/Workspace/mediagram-android-lan-cache`; nothing else was
touched. Device step 9 and adb are untouched, per instruction.

## Summary

Android now tries a paired `mediagram_cache` server before Telegram for
every chunk (`LanFirstChunkSource`), mirrors real Telegram misses back to
it on a bounded background queue (`LanWriteQueue`), discovers it via mDNS
with a manual-address override and a bounded 10s pass
(`LanServerLocator` + `LanServerNetworkWatcher`), and exposes pairing and
status in Settings (`LanCacheViewModel` + `LanCacheBlock`) plus a System
"Source" row. Preload shares the exact same path (`MlibDataSourceFactory`),
so it fills the server for free. With no server paired, behaviour is
unchanged from before this phase — the `lan` parameter threaded through
`buildPlayer`/`cacheDataSourceFactory`/`CacheDataSourceWriter` defaults to
`null` everywhere a caller doesn't supply one.

The review round fixed a process-crash-on-launch bug (a manual address
without a scheme, exactly what `avahi-browse` and the server's own status
line print, threw `MalformedURLException` inside a coroutine with no
handler above it), a multicast-lock/battery leak (discovery was never
actually stopped), a permission gate that would have shown "Needs local
network permission" permanently on the target tablet (API 36, below
where `ACCESS_LOCAL_NETWORK` exists), an unbounded-read OOM risk on a
spoofed LAN responder, a write queue that stayed halted forever after a
401, and PUTs being enqueued for LAN reads that were never actually tried
(mobile data, a down-flagged server).

## Tests

- **Before this session:** phase 1–3 baseline + `SeriesPreloaderTest`
  green (confirmed via a full `testDebugUnitTest` run across every module
  before starting).
- **After implementation, before review fixes:** full regression gate —
  `android/gradlew testDebugUnitTest :core:model:test lint` across every
  module, 0 failures — plus `scripts/check.sh` end to end (cargo
  clippy/test, gradle test+lint; web skipped, no `node_modules`).
- **After review fixes (final):**
  - `core:playback`, `feature:system`, `ui-mobile` `testDebugUnitTest`
    with `--rerun-tasks` (so results are not stale) — all green.
  - Full `android/gradlew testDebugUnitTest :core:model:test lint` across
    every module — all green.
  - `ANDROID_HOME=/home/andre/android-sdk scripts/check.sh` from the
    worktree root — `all checks passed`, no `FAILED` anywhere in the log
    (clippy, 92+6+1+2+... cargo test suites, gradle test+lint; bun
    skipped as expected, `web/node_modules` absent).
  - New/changed test coverage this session: `LanChunkClientTest` (12),
    `LanChunkClientWriteTest` (9, split out), `LanFirstChunkSourceTest`
    (4) + `LanFirstChunkSourceFallbackTest` (6, split out),
    `LanWriteQueueTest` (8, incl. new resume-after-clear case),
    `LanServerLocatorTest` (6, unchanged — pure `pickLanServer` logic),
    `LanCacheInputTest` (13, new — address/token normalisation + the
    SDK-gate pure function), `LanCacheViewModelTest` (6) +
    `LanCacheViewModelValidationTest` (6, split out), `LanCacheBlockTest`
    (12, incl. new Grant-action and error-text cases).

## Original implementation (phase 4)

### Files created
- `android/core/playback/src/main/kotlin/`: `LanChunkClient.kt`,
  `LanChunkSigning.kt`, `LanFirstChunkSource.kt`, `LanWriteQueue.kt`,
  `LanServerLocator.kt`, `LanCacheSettings.kt`, `LanCacheRuntime.kt`,
  `MlibDataSourceFactory.kt` (extracted from `MlibDataSource.kt` to make
  room for `lan` wiring without exceeding 200 lines)
- `android/core/data/src/main/kotlin/settings/LanCacheTokenSettings.kt`
  (+ Hilt binding in `di/DataModule.kt`)
- `android/feature/player/src/main/kotlin/di/LanCacheModule.kt` (split
  out of `PlaybackModule.kt` for the same line-limit reason)
- `android/feature/system/src/main/kotlin/LanCacheUiState.kt`,
  `LanCacheViewModel.kt`
- `android/ui-mobile/src/main/kotlin/ui/settings/LanCacheBlock.kt`
- `android/app/src/main/res/xml/network_security_config.xml`
- Test files matching each of the above

### Files modified
- `MlibDataSource.kt` (factory extracted out), `PlaybackCounters.kt`
  (LAN hit/miss counts, last-read source), `PlayerFactory.kt`,
  `CacheDataSourceWriter.kt` (both thread an optional `lan` parameter
  through), `core/playback/build.gradle.kts` (mockwebserver test dep)
- `feature/player/di/PlaybackModule.kt` (wires `lan` into the player and
  preloader)
- `feature/system/SystemUiState.kt` / `SystemViewModel.kt` (Source row
  facts), `feature/system/build.gradle.kts` (robolectric test dep)
- `ui-mobile/ui/settings/CacheSection.kt` (hosts `LanCacheBlock`),
  `ui-mobile/ui/system/SystemRows.kt` / `SystemScreen.kt` (Source row
  wording)
- `android/app/src/main/AndroidManifest.xml` (two permissions, network
  security config)
- `docs/running-the-player.md` (Android pairing paragraph)

## Review fixes (this session)

**C1 — crash loop on a scheme-less manual address (`MalformedURLException`
inside a coroutine with no handler above it).** `LanChunkClient.get/put/verify/status`
now build their connection inside their own `try` (via a shared
`withCancellableConnection` helper in the new `LanChunkHttp.kt`), so the
exception is caught the same as any other connect failure instead of
escaping `LanServerLocator`'s discovery coroutine uncaught.
`LanCacheViewModel.setManualAddress` normalises before saving —
`normalizeManualAddress` in the new `LanCacheInput.kt` trims, prepends
`http://` when there is no scheme, and refuses anything `URL(...)` still
can't parse, showing a sentence under the field rather than saving it.
`LanServerLocator.pick()` wraps `pickLanServer(...)` in `runCatching` as
defence in depth.

**M3 (same path as C1) — IPv6.** A resolved NSD service is only taken when
its address is an `Inet4Address`; an IPv6 literal is skipped rather than
built into a bare `host:port` string that every consumer of that string
(the manual-address field, `pickLanServer`'s own `hostOf`) would also have
needed to learn to bracket.

**C2 — battery/multicast lock never released.** `LanServerLocator.discover()`
now runs one pass bounded to 10s (`PASS_WINDOW_MS`), ending sooner the
moment a verified server is picked, and always stops discovery and
releases the lock in `endPass()` — called from the timeout, from a
successful pick, and from `onStartDiscoveryFailed`. Previously both were
only ever released by the *next* `discover()` call, so the app-start pass
held the radio open for the process's whole lifetime.

**H1 — permission gate wrong below API 37.** `ACCESS_LOCAL_NETWORK`
doesn't exist as a platform permission below API 37; `checkSelfPermission`
for it answers `DENIED` regardless, which showed "Needs local network
permission" permanently on every device under 37 — including the target
tablet, API 36. `localNetworkPermissionGranted(sdkInt, granted)` (pure,
unit tested — Robolectric has no shadow for API 37 in this project's
version, confirmed by trying `@Config(sdk = [37])` and getting
`UnknownSdk`) now short-circuits to granted below 37. The prompt itself
moved off the on/off switch (which defaults to `true`, so an off→on
toggle rarely fires) onto saving a token and a new "Grant" action on the
status row, both gated the same way in `LanCacheBlock`.

**H2 — unbounded read (OOM on a spoofed responder), no deadline, blocking
reads immune to cancellation.** `LanChunkClient.get` now: rejects a
declared `Content-Length` that disagrees with the expected chunk length
before reading; reads into a buffer sized to exactly the expected length
and refuses a body that turns out longer or shorter (`readExactly` in
`LanChunkHttp.kt`); runs under a 2s `withTimeout` (`getDeadlineMs`,
converted to `IOException` on expiry — this is the accepted replacement
for tracking a moving median of read latency, documented as such in
`LanChunkClient`'s own KDoc); and disconnects the underlying connection
the moment the coroutine is cancelled, via
`Job.invokeOnCompletion(onCancelling = true, ...)` (an
`@InternalCoroutinesApi`, used deliberately — the stable overload only
fires once a job reaches a *final* state, which one blocked in synchronous
I/O never reaches on its own until the call returns) — this is what
actually makes the deadline and an external cancel effective against a
plain blocking `HttpURLConnection` read; a 5xx now throws `IOException`
rather than being read as a plain miss (M5). `LanFirstChunkSource` widens
its catch from `IOException` to `Exception`, rethrowing
`CancellationException` immediately so a reader's real cancellation is
never silently turned into a slower Telegram fallback.

**H3 — write queue stayed halted forever after a 401; token not
validated.** `LanWriteQueue` now depends on `LanCacheTokenStatus` directly
(replacing an `onUnauthorized` callback): a 401 still halts and marks it
rejected, but a second worker watches the same status and clears the halt
the moment it's cleared — exactly what saving a new token already does, so
a re-paired device resumes sharing without an app restart.
`normalizePairingToken` (new, `LanCacheInput.kt`) trims and requires
exactly 64 lowercase hex characters, refusing anything else with a
sentence under the field.

**H4 — every Telegram fetch was enqueued for LAN write-back, even when the
LAN path was skipped or failed.** `LanFirstChunkSource` now enqueues only
after a real miss (the server was actually asked, on an unmetered network,
and said 404/wrong-length) — not after a skip (metered, no server,
down-flagged) or a failure, which would otherwise mirror mobile-data reads
back over mobile data or queue writes for a server that had just failed to
answer.

**M1 — no reaction to network changes.** New `LanServerNetworkWatcher`
registers one `ConnectivityManager.NetworkCallback` for the process's
lifetime (this is a singleton with no shorter-lived owner to unregister
it from): losing the default network clears the picked server, and a
newly-unmetered network triggers one bounded discovery pass — the third
of the plan's three triggers, alongside Settings opening and app start.

**M2 — locator state races.** `NsdManager`'s own callbacks run on an
arbitrary thread, not `scope`'s; every one now re-enters through
`scope.launch`, so `found`, the picked server and `searching` are only
ever touched from `scope`'s own (assumed single-threaded, e.g.
`Dispatchers.Main.immediate`) dispatcher — documented as that class's
invariant rather than adding a `Mutex`. A monotonic `generation` counter,
checked before every mutation, discards anything a superseded pass's
callbacks still deliver late, and `searching` now stays `true` for the
whole pass rather than flipping `false` after every individual resolve's
pick attempt.

**M5 — 5xx read as a miss; PUT buffered internally.** Folded into H2's
`get()` rewrite (5xx → `IOException`) and `put()` now calls
`setFixedLengthStreamingMode(body.size)` instead of buffering.

**Low — plan reference in a comment; test file over 200 lines.** Removed
"(phase's device step)" from `LanServerLocator.kt`'s doc (the whole
locator KDoc was rewritten as part of C2/M2 anyway).
`LanFirstChunkSourceTest.kt` split into it and
`LanFirstChunkSourceFallbackTest.kt` (failure/write-gating branches); the
shared fakes moved from file-private to `internal`, renaming the shared
`LanServer` fixture to `LAN_SERVER` to avoid colliding with
`LanWriteQueueTest.kt`'s own same-named private fixture.

## New/renamed files this session
- `android/core/playback/src/main/kotlin/LanChunkHttp.kt` (new —
  `withCancellableConnection`, `readExactly`)
- `android/core/playback/src/main/kotlin/LanChunkSigning.kt` (renamed
  content split out of `LanChunkClient.kt` to stay under 200 lines,
  already existed from phase 4; `statusFromJson` etc. live here)
- `android/core/playback/src/main/kotlin/LanServerPicker.kt` (new — pure
  `pickLanServer`, `LanServerProbe`, `LanServerSource`, split out of
  `LanServerLocator.kt`)
- `android/core/playback/src/main/kotlin/LanServerNetworkWatcher.kt` (new)
- `android/core/playback/src/test/kotlin/LanChunkClientWriteTest.kt` (new
  — PUT/status/verify tests split out of `LanChunkClientTest.kt`)
- `android/core/playback/src/test/kotlin/LanFirstChunkSourceFallbackTest.kt`
  (new — failure/write-gating tests split out)
- `android/feature/system/src/main/kotlin/LanCacheInput.kt` (new —
  `normalizeManualAddress`, `normalizePairingToken`,
  `localNetworkPermissionGranted`)
- `android/feature/system/src/test/kotlin/LanCacheInputTest.kt`,
  `LanCacheViewModelValidationTest.kt` (new)

## Line counts (touched/created files, all < 200)

```
 191 core/playback/src/main/kotlin/LanChunkClient.kt
  73 core/playback/src/main/kotlin/LanChunkHttp.kt
  56 core/playback/src/main/kotlin/LanChunkSigning.kt
  87 core/playback/src/main/kotlin/LanFirstChunkSource.kt
 191 core/playback/src/main/kotlin/LanServerLocator.kt
  58 core/playback/src/main/kotlin/LanServerNetworkWatcher.kt
  42 core/playback/src/main/kotlin/LanServerPicker.kt
  99 core/playback/src/main/kotlin/LanWriteQueue.kt
  70 core/playback/src/main/kotlin/MlibDataSourceFactory.kt
 154 core/playback/src/main/kotlin/MlibDataSource.kt
  83 core/playback/src/main/kotlin/PlaybackCounters.kt
 134 core/playback/src/main/kotlin/PlayerFactory.kt
  60 core/playback/src/main/kotlin/CacheDataSourceWriter.kt
  42 core/playback/src/main/kotlin/LanCacheRuntime.kt
  81 core/playback/src/main/kotlin/LanCacheSettings.kt
  60 core/data/src/main/kotlin/settings/LanCacheTokenSettings.kt
 149 core/data/src/main/kotlin/di/DataModule.kt
 100 feature/player/src/main/kotlin/di/LanCacheModule.kt
 161 feature/player/src/main/kotlin/di/PlaybackModule.kt
  52 feature/system/src/main/kotlin/LanCacheInput.kt
  28 feature/system/src/main/kotlin/LanCacheUiState.kt
 145 feature/system/src/main/kotlin/LanCacheViewModel.kt
  44 feature/system/src/main/kotlin/SystemUiState.kt
 125 feature/system/src/main/kotlin/SystemViewModel.kt
 125 ui-mobile/src/main/kotlin/ui/settings/LanCacheBlock.kt
  32 ui-mobile/src/main/kotlin/ui/settings/CacheSection.kt
 161 ui-mobile/src/main/kotlin/ui/system/SystemRows.kt
 149 ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt
  60 app/src/main/AndroidManifest.xml
  15 app/src/main/res/xml/network_security_config.xml
 150 core/playback/src/test/kotlin/LanChunkClientTest.kt
 119 core/playback/src/test/kotlin/LanChunkClientWriteTest.kt
 167 core/playback/src/test/kotlin/LanFirstChunkSourceTest.kt
 154 core/playback/src/test/kotlin/LanFirstChunkSourceFallbackTest.kt
  89 core/playback/src/test/kotlin/LanServerLocatorTest.kt
 190 core/playback/src/test/kotlin/LanWriteQueueTest.kt
  75 core/playback/src/test/kotlin/LanCacheSettingsTest.kt
  97 core/playback/src/test/kotlin/PlaybackCountersTest.kt
  35 core/data/src/test/kotlin/settings/LanCacheTokenSettingsTest.kt
  85 feature/system/src/test/kotlin/LanCacheInputTest.kt
 196 feature/system/src/test/kotlin/LanCacheViewModelTest.kt
 132 feature/system/src/test/kotlin/LanCacheViewModelValidationTest.kt
 154 ui-mobile/src/test/kotlin/ui/settings/CacheSectionTest.kt
 167 ui-mobile/src/test/kotlin/ui/settings/LanCacheBlockTest.kt
```

## Commits (branch `feat/android-lan-cache`)

Implementation:
1. `58a8d1b` `feat(android-playback): add the LAN chunk client, write queue, and server locator`
2. `4c5f797` `feat(android-playback): read and share chunks through a home cache server`
3. `7c73d21` `feat(android): add the home cache server Settings block and a System source row`
4. `7efbfe6` `docs(running-the-player): describe pairing an Android device with the home cache server`

Review fixes:
5. `7c766cf` `fix(android-playback): bound LAN GET reads and stop malformed URLs from crashing` (C1, H2, M5)
6. `ca096d8` `fix(android-playback): only mirror a real LAN miss, and never swallow cancellation` (H2, H4)
7. `62b1f7a` `fix(android-playback): resume LAN writes once a re-paired token clears the halt` (H3)
8. `deb9786` `fix(android-playback): bound discovery to one pass and stop it on network change` (C2, M1, M2, M3, Low)
9. `e7e4a2d` `fix(android): gate the local-network prompt to API 37+ and validate pairing input` (H1, C1 address, H3 token)

No AI attribution in any commit message. No version bump (per task instruction).

## Deviations / notes carried over from the original implementation

- NSD resolve API: uses `NsdManager.resolveService` (deprecated at API
  34+, still functional) uniformly rather than branching to
  `registerServiceInfoCallback` at 34+, serialising resolves with a small
  pending queue instead. Not required by the phase spec's Requirements or
  Implementation steps.
- `GET /v1/status`'s `held_bytes` etc. are parsed with a small regex
  (`statusFromJson` in `LanChunkSigning.kt`) rather than a JSON library —
  `org.json.JSONObject` is an Android-framework stub that silently returns
  zeros under a plain (non-Robolectric) JVM unit test, which
  `LanChunkClientTest` is; the server's own response shape is fixed and
  owned by this same codebase, so a general parser buys nothing.
- Device step 9 (Redmi, real server) and adb are untouched, as instructed.

## Unresolved questions

- `status()`'s own body read (`GET /v1/status`) is still an unbounded
  `inputStream.use { it.readBytes() }` — H2 only asked for `get()`'s chunk
  read to be bounded. The status payload is small and server-controlled by
  the same codebase, but a spoofed responder on `/v1/status` specifically
  could still send an oversized body. Flagging in case this was meant to
  be covered too.
- `LanServerNetworkWatcher`'s `onUnmeteredAvailable` fires on any
  newly-unmetered default network without checking whether it's actually a
  *different* network worth re-discovering on (e.g. a brief capability
  flap on the same Wi-Fi); each call is cheap and self-cancelling
  (`discover()`'s own generation bump), so this is accepted rather than
  fixed, but noting it as a simplification.
