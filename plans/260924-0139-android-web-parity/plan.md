---
title: "Android reaches web-player parity"
description: "Search, genres, player essentials, touch/PiP/MediaSession, preload, notes, profiles on Android, matching the web player's decisions."
status: pending
priority: P1
effort: 60h
branch: feat/android-web-parity
tags: [android, parity, player, search, core]
created: 2026-09-24
---

# Android reaches web-player parity

Supersedes the unbuilt phases 1-3, 6, 7, 10 of
[`260922-0124-android-web-parity`](../260922-0124-android-web-parity/plan.md)
(4, 5, 8, 9 were done by watch-state sync). Web is the reference (CLAUDE.md
§ Surface Parity); every phase names the web module it ports.

## Facts every phase relies on (verified)
- Subtitles and notes are rows in the index `assets(set_id, kind, lang, body)`
  (`crates/mlib-spec/src/schema.rs:94`); VTT sidecars only, nothing embedded is
  extracted (`web/src/assets.ts:25-51`). The phone already holds the whole
  index: snapshots are a `VACUUM INTO` copy (`crates/mediagram/src/index/snapshot.rs:26`).
- Per-show choices live **per profile, on the device, unsynced**: web table
  `preferences` (`web/src/state/schema.ts:169`), absent from `sync-record.ts`.
  Core's `state.db` deferred exactly this table (`crates/mediagram-core/src/state/schema.rs:2`).
- ExoPlayer is an app-scoped singleton (`android/feature/player/src/main/kotlin/di/PlaybackModule.kt:53-73`):
  speed, track overrides and text state leak across titles unless reset per open.
- Cache key is the URI `mlib://set/<id>` (no custom key factory,
  `android/core/playback/src/main/kotlin/PlayerFactory.kt:21-39`), so "held" =
  `cache.isCached(key, 0, totalBytes)`.
- `crates/mediagram-core/src/api/mod.rs` is at 200 lines: new exports go in new
  `api/*.rs` impl blocks. Kotlin files already over 200 lines and touched here:
  `PlayerScreen.kt` 217, `PlayerViewModel.kt` 223, `DefaultPlayerHandle.kt` 206,
  `LibraryFlow.kt` 277, `CatalogViewModel.kt` 203 — split before growing.

## Phases (strictly sequential; one commit each)
| # | Phase | Depends | Effort | Status |
|---|-------|---------|--------|--------|
| 01 | [Core: index extras, preferences, profile delete](phase-01-core-index-extras-and-preferences.md) | - | 5h | done |
| 02 | [Core: search ported from the web](phase-02-core-search.md) | 01 | 5h | done |
| 03 | [Android: search screen and genre pages](phase-03-android-search-and-genres.md) | 02 | 5h | done |
| 04 | [Player foundation: sheet, per-show store, title, ends-at, retry, speed](phase-04-player-foundation-and-speed.md) | 01 | 6h | pending |
| 05 | [Audio track chooser](phase-05-audio-tracks.md) | 04 | 3h | pending |
| 06 | [Subtitles](phase-06-subtitles.md) | 04 | 6h | pending |
| 07 | [Up next and queues](phase-07-up-next-and-queues.md) | 04 | 6h | pending |
| 08 | [Fullscreen, double-tap, framing](phase-08-fullscreen-gestures-framing.md) | 04 | 5h | pending |
| 09 | [Picture-in-picture and MediaSession](phase-09-pip-and-media-session.md) | 08 | 5h | pending |
| 10 | [Series preload and offline badges](phase-10-series-preload-and-offline.md) | 07 | 5h | pending |
| 11 | [Notes panel](phase-11-notes-panel.md) | 04 | 5h | pending |
| 12 | [Profile removal, shelf view, docs close-out](phase-12-profiles-shelf-view-docs.md) | 01 | 4h | pending |

Sequential because 04-11 share player files and every phase edits the three
version manifests and the changelog.

## Every phase ends with
1. `scripts/check.sh` green **with `ANDROID_HOME` set** (else it skips Android).
2. Rust touched: `ANDROID_NDK_HOME=… scripts/build-android-core.sh`, then
   `scripts/generate-android-bindings.sh`; bindings committed.
3. Minor bump in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`
   `versionName`, computed from `main` at commit time, edited by pattern (main moves).
4. Entry in `docs/project-changelog.md`; deliberate differences written where
   the phase says.
5. Device check: `./gradlew :app:installDebug` on the adb tablet, signed in.
   Never "Start over", never clear app data (cache dir only).

## Deliberate differences (not built; recorded in phase 12)
Scrub thumbnails (server ffmpeg sprites), adaptive bitrate / needs-transcode
badge (phone never transcodes), keyboard-only features (A-B loop, frame step,
number jumps, `[`/`]`), volume memory (hardware volume).

## Decided (user, 2026-09-24)
- Profiles: remove only, as the web does (a profile other devices hold
  returns on sync). No rename on either surface for now. Phase 12.
- Shelves: posters stay the default on the phone, with the web's List/Grid
  toggle on Films and Series; courses always a list. Different default is
  deliberate (touch screen) and recorded in phase 12.
- Lock-screen controls with a media service and background audio; playback
  pauses when the viewer leaves the player screen. Phase 09.
- Preload next two episodes on Wi-Fi only, skipping one that would push the
  cache past 75% of its budget. Phase 10.
- Retry button, double-tap seek, PiP button: phone-only touch equivalents of
  the web's seek-to-retry and `p` key. Recorded as deliberate, not web debt.
