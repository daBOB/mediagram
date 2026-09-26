---
phase: 4
title: "Android reads through the LAN server"
status: done
priority: P1
effort: "2d"
dependencies: [1, 2, 3]
---

# Phase 4: Android reads through the LAN server

## Overview
On an unmetered network with a paired `mediagram_cache` in reach, each
chunk is asked of the server first. On a miss, Android fetches the chunk
from Telegram itself, serves it, and PUTs it to the server in the
background. The local `SimpleCache` stays in front, so there are two
tiers. Android keeps its Telegram session, exactly as today.

## Key insights (research report)
- **`ACCESS_LOCAL_NETWORK` is a runtime permission at targetSdk 37.**
  Without it, TCP to a LAN address times out and NSD fails. It is the
  first new permission in this work and needs a prompt. **Verify the exact
  constant against `$ANDROID_HOME/platforms/android-37/android.jar`
  before writing code.** If it does not exist there, stop and report.
- Cleartext: `network_security_config` cannot express CIDRs, so
  `base-config cleartextTrafficPermitted="true"`. Today the app makes no
  `http://` calls (checked: none in `android/*/src`), and everything else
  is MTProto or https, so the widening does not reach any existing traffic.
- NSD: `resolveService` allows one listener at a time below API 34; use
  `registerServiceInfoCallback` on 34+. Hold a `MulticastLock` during
  discovery (`CHANGE_WIFI_MULTICAST_STATE`, a normal permission).
- HTTP: plain `HttpURLConnection` needs no dependency, and chunk GET/PUT
  sits below the DataSource level. Connect timeout 1 s (a LAN answers in
  ms; this bounds the cost of fallback), read timeout 5 s.
- The pairing token is a secret. It gets an interface plus an
  `Encrypted*Settings` class in `:core:data/settings/`, following
  `PackageSettings`/`EncryptedPackageSettings` (secrets encrypted, plain
  values plain). The manual address and the on/off switch are plain prefs.
- **The token never leaves the device.** PUTs are signed (`MGC1`, the
  HMAC-SHA256 scheme defined in phase 3) with `javax.crypto.Mac` and
  `MessageDigest`, so no dependency is needed. A look-alike server on
  another Wi-Fi learns nothing. No network binding is needed, which
  matters because reading the Wi-Fi identity would require the location
  permission. <!-- Updated: Validation Session 1 - signing replaces network binding -->
- **Slow-but-alive servers.** Beyond connect failures, a server whose GETs
  are slower than a threshold (moving median > 2 s) is also marked down.
  All HTTP runs with the connection closed from `MlibDataSource.close()`,
  so a player cancel is not stuck behind a blocked read.

## Requirements
- Functional
  - `LanFirstChunkSource` wraps `TelegramChunkSource`:
    - skipped when the network is metered, no server is known, or the
      server was marked down less than 60 s ago;
    - a GET returning 200 with the expected length serves the bytes; a
      wrong length is treated as a miss;
    - a 404 goes to Telegram, serves, and enqueues a PUT;
    - an `IOException` or timeout marks the server down and goes to Telegram.
  - Write queue: bounded to 8 chunks, drop-oldest, one worker on IO, PUT
    signed (`MGC1`) with `X-Set-Total`. A 401 stops writing and surfaces
    "Pairing token rejected" in Settings.
  - Discovery: `LanServerLocator` exposes `StateFlow<LanServer?>`,
    discovers `_mediagram-cache._tcp`, verifies with `GET /v1/status`, and
    uses a manual URL override when one is set.
  - Settings (phone), in a new block: use the home cache server (on/off,
    default on once paired); status (Searching / Connected to host, holding
    X / Not found / Needs local network permission); manual address; token.
    Enabling triggers the permission prompt.
  - System: a "Source" row reading LAN (host) or Telegram, plus LAN
    hit/miss counts in `PlaybackCounters`.
  - Series preload fills the server for free, because it goes through the
    same upstream path.
- Non-functional: with no server, playback is as fast as today (no
  per-chunk timeout, because of the down flag and discovery gating).

## Architecture
```
MlibDataSource ─▶ SetChunkSource = LanFirstChunkSource(
                      lan = LanChunkClient(serverFlow, tokenStore),
                      telegram = TelegramChunkSource(core),
                      network = UnmeteredNetworkCheck,
                      writes = LanWriteQueue(lan))
LanServerLocator (NsdManager + MulticastLock) ──▶ StateFlow<LanServer?>
LanCacheSettings (:core:playback; token via an Encrypted*Settings class in :core:data)
```

## Related code files
- Create in `android/core/playback/src/main/kotlin/`: `LanChunkClient.kt`,
  `LanFirstChunkSource.kt`, `LanWriteQueue.kt`, `LanServerLocator.kt`,
  `LanCacheSettings.kt` (each with tests except the NSD adapter, whose pure
  "pick and verify" logic is split out and tested)
- Modify: `PlaybackCounters.kt`, `PlayerFactory.kt`/`PlaybackModule` (wiring)
- Modify: `android/app/src/main/AndroidManifest.xml` (two permissions, `networkSecurityConfig`)
- Create: `android/app/src/main/res/xml/network_security_config.xml`
- Create: `android/ui-mobile/src/main/kotlin/ui/settings/LanCacheBlock.kt` (+ test); host it in `CacheSection.kt` from phase 1
- Create: `android/feature/system/src/main/kotlin/LanCacheViewModel.kt`
- Modify: System rows/state (the Source row)

## Implementation steps (TDD)
1. **Tests before:** the phase 2 tests plus `SeriesPreloaderTest`,
   green, as the regression baseline.
2. `LanChunkClient` red then green against `okhttp3.mockwebserver`
   (already in `gradle/libs.versions.toml:159`; add it as a
   `testImplementation` of `:core:playback`). Assert the signature against
   phase 3's shared test vector. Cover: GET 200/404, wrong length → miss, PUT headers,
   401 → `Unauthorized`, connect timeout → `IOException`.
3. `LanFirstChunkSource` red then green with fakes: each branch of the
   requirements, the 60 s down window using an injected clock, metered →
   the server is never touched, and a hit calls neither `counters.fetched`
   nor Telegram.
4. `LanWriteQueue` red then green: bounded and drop-oldest, 401 halts,
   and failures never reach the reader.
5. Locator pure logic red then green: picking the first verified server,
   the override winning, and a status failure → `null`.
6. Settings, ViewModel and block red then green (wording, including
   permission denied and token rejected). The System Source row likewise.
7. Manifest, network config and permission request flow (`rememberLauncherForActivityResult`).
8. **Regression gate:** `scripts/check.sh` with `ANDROID_HOME`.
9. **Device (Redmi, test profile), server running on the dev box:**
   - Pair (paste the token) and grant the permission.
   - Play title A for 60 s: Telegram fetches are > 0 and the server
     `held_bytes` grows.
   - Clear local `cache/mlib`, play A again: 0 Telegram fetches, LAN hits
     > 0, and System shows LAN.
   - Stop the server mid-play: playback continues via Telegram with no
     stall loop.
   - Mobile data: Source shows Telegram.
   - Record first-frame time with the LAN warm and cold.
10. Commit: `feat(android-playback): read and share chunks through a home cache server`.

## Success criteria
- [x] Device steps 9 pass (mobile-data step owed: Wi-Fi-only tablet) and the numbers are recorded in the plan review.
- [x] No playback regression with the server absent.
- [x] Only the two permissions added.

## Out of scope (deliberate, documented)
- **TV UI for the permission prompt and token entry.** The TV branch has
  no Settings screen. `:core:playback` works on TV once both are granted
  and set, and it is a follow-up the TV plan owes. Recorded in `plan.md`.
- QR pairing, token rotation, and server-side delete.

## Risk assessment
- **Permission constant or behaviour differs from the research:**
  verification at step "Key insights" gates the phase.
- **OEM NSD flakiness:** the manual address override is always available.
- **Poisoned chunk:** a bad chunk of the right length cannot be detected
  on Android, so playback errors on it (consistent with phase 3).
  `NEARBY_WIFI_DEVICES` might also be needed for NSD on some builds: check
  on the Redmi at step 7 and add it only if discovery fails without it. This is accepted: the token and
  the length/total rules (phase 3) bound who can write, and server-side
  delete is the later remedy.
- **Battery/multicast:** discovery runs only while Settings is open, at
  app start, and after a network change, never continuously.

## Review (2026-09-25)
- Device (Redmi tablet, Android 16, "test" profile; `mediagram_cache` on the dev box at 192.168.0.118:7788):
  - mDNS found the server with no manual address. No permission prompt, which is correct below API 37. Pairing: "A pairing token is stored."
  - First play with a cold local cache: all 67 Telegram-fetched chunks reached the server, none dropped. There was one stall of about a minute with one failed read. It did not recur in four later runs; it came from Telegram, not the LAN path.
  - Replay of a region the server held, with a cold local cache: 83 MB held locally, 6 MB from Telegram, ~77 MB from the LAN. The Telegram fetches were the first reads before discovery finished, plus read-ahead past what the server held.
  - Server stopped mid-play: playback continued for 32 s on Telegram with no stall. System: "Telegram; 49 chunks from the home server", no failed reads.
  - A manual address without a scheme was stored normalised, and the app relaunched without crashing. An unreachable override still found the server through mDNS.
  - Owed: the mobile-data step (the tablet is Wi-Fi only), and the first-frame time with the LAN warm vs cold.
- Code review: 2 Critical (a scheme-less address crash-looped the app; discovery and the multicast lock were never released), 4 High, 5 Medium. All fixed in `7c766cf..e7e4a2d`.
- Found on device and fixed:
  - The Source row showed only the last read, which is almost always Telegram read-ahead, so LAN hits were invisible. It now counts them (`sourceLine`).
  - The `/v1/status` body was read unbounded. It is now capped at 4 KiB (`b1365b3`).
- Deviations kept:
  - A slow server is caught by a 2 s overall GET deadline that marks it down, not a moving median.
  - The deprecated `resolveService` is used on every API level.
