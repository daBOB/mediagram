---
phase: 3
title: "Spieltisch gets the articles"
status: done
priority: P1
effort: "4h"
dependencies: [1]
---

# Phase 3: Spieltisch gets the articles

## Overview
Spieltisch holds no cache token and no media data of its own; the media app answers its `/night`. Reading a blob
needs no token, so the media app only has to say where the home cache server is. Spieltisch's Kotlin side then
reads the article list at night start and the photos on demand, and hands both to the host the way it hands over
titles and title images today. Touches both lines.

## Tests first
- `GameNightProviderTest.kt`: `/night` carries `cache_server` (the address the media app found, or empty when it
  found none or the LAN cache switch is off); a kids-locked profile still gets the address (the host blocks the
  game, not the provider).
- `NightSessionTest.kt`: with an address, Spieltisch GETs `/v1/game/preisfrage/articles.json` (5 s budget) and
  passes the body to `start_night`; 404, timeout or no address → empty list, the night still starts.
- `MediaImagesTest` (Spieltisch side): `ImageKind::Article` with an ASIN → GET `photos-{asin}.jpg` into the app's
  cache dir once, then served from there; a miss answers `None`.
- `spieltisch-core` `night_tests.rs`: `start_night` takes `articles_json`; unreadable JSON → empty list, warn once.
- `mediagram-game`: `ImageKind::Article` routes `/image/article/{asin}` through `ImageLookup`; the dev binary
  `game-night-dev --articles DIR` reads `articles.json` and photos from a `--dry-run` folder.

## Related Code Files
- Modify: `android/feature/games/src/main/kotlin/GameNightProvider.kt` (+ test),
  `android/spieltisch/src/test/kotlin/MediaAppContractTest.kt` (the row shape Spieltisch reads),
  `android/spieltisch/src/main/kotlin/{NightSession,MediaProviderClient,MediaImageCache}.kt` (+ tests), `crates/spieltisch-core/src/{night.rs,media_images.rs}` (+ tests),
  `crates/mediagram-game/src/{titles.rs,image_files.rs,server.rs,host.rs}`, `src/bin/game-night-dev.rs`
- Regenerate and commit the uniffi bindings (`scripts/generate-spieltisch-bindings.sh`; `check.sh` fails otherwise)

## Success Criteria
- [x] Tests seen failing first, then green; `scripts/check.sh` green; both lines minor bump (media 0.177.0 with
      phase 1, Spieltisch 0.177.0: 0.176.0 is held by the unmerged ferry work).
- [ ] On funstation with the cache server holding a list: logcat shows the list's size at night start
      (`MediaLibraryBridge` and `CacheServerArticles`). Moved to phase 7: the funstation's cache server is still
      0.163.0, without the game route.

## As built (differs from the plan above)
- The list is read in `MediaLibraryBridge.night()`, after the media app's row and on its own timeouts (2.5 s
  connect, 2.5 s read), not in `NightSession`: the bridge already reads off the main thread and logs, and a
  slow cache server then costs only the list, never the films. `MediaNight` carries `cacheServer` and `articles`.
- Photos: `NightImages` wraps the media app's `MediaImages` and answers `MediaImageKind::Article` from
  `CacheServerArticles.photo` into `cache/articles/`; `MediaImageCache` is unchanged.
- The media app's address comes from `HomeCacheServer.url`, set by the existing `linkHomeCache` (so it follows
  the LAN cache switch and a manual address), not from `LanServerLocator` directly.
- Spieltisch's network config allows cleartext globally, as the media app's does (the server's LAN address is
  not known in advance).
- The core's `tracing` has no Android logger, so the host's own "Preisfrage articles count=…" line shows only on
  the dev host; on the box the Kotlin log lines carry it.

## Risks
- An older media app without `cache_server` → Spieltisch sees no address → Preisfrage blocked with its reason;
  no crash. Covered by a test with the old row shape.
