# Phase 04 — Android core + player: subtitle files, cache, offline holds, the playback rule

## Context links
- Core reads today: `crates/mediagram-core/src/api/set_text.rs:10-33` ("never a Telegram round trip"), `catalog_assets.rs:15-58`, `api/store/editorial.rs:74-75,128`, `dto/summary.rs:53` (`subtitles: Vec<String>`), `catalog.rs:58-60` (`COLUMNS`: no `alang`/`slang`).
- Fetch path to reuse: `transport/stream.rs:75-93` (`part_document`; `get_messages_by_id` takes a slice), `api/channel/install.rs:54-84` (capped `iter_download`), `api/read.rs:70-80` (live client + owner), `api/mod.rs:52-70` (`Core.data_dir`).
- Install paths: `api/channel/install.rs:44-50` and `api/refresh/mod.rs:75`, both via `versions/install.rs:51` (`Staging::install`). **No Android device installs a package catalog**: no Kotlin caller of `refreshCatalog` outside the bindings and `FakeCore`; the app calls `refreshLibrary` (`android/core/data/src/main/kotlin/CatalogRepository.kt:122`, `android/feature/setup/src/main/kotlin/Libraries.kt:38`).
- `crates/mlib-spec/src/subtitle_bundle.rs` (phase 02: `decode`, caps, `valid_sha256`).
- Android: `core/playback/src/main/kotlin/SubtitleTrackSource.kt:16-42`, `core/model/src/main/kotlin/MediaSet.kt:59`, `core/data/src/main/kotlin/CatalogRepository.kt:204`, `core/data/src/main/kotlin/PlayerPreferences.kt:14-39`, `feature/player/src/main/kotlin/SubtitleChoice.kt:19-40`, `SubtitleChoiceController.kt:33-134`, `AudioChoiceController.kt:31-39,141-181`, `PlayerChoicesController.kt:46-50`, `PlayerViewModel.kt:71-80` (198 lines), `core/playback/src/main/kotlin/CacheDataSourceWriter.kt:50-66` (already takes `currentCore: () -> CoreInterface?`), `FilmPreloader.kt:40`, `SeriesPreloader.kt:64`.
- Facts: `ui-mobile/src/main/kotlin/ui/catalog/TitleDetailScreen.kt:205`, `feature/catalog/src/main/kotlin/SeriesSummary.kt:26,67,118-124` — read the inline-assets languages today; the web reads `slang` (`web/public/lib/catalog/series-summary.js:46-49`).
- Rendering stays: `core/playback/src/main/kotlin/PlayerFactory.kt:124-127` (ExoPlayer text off; Compose `SubtitleLayer` draws).
- Fake: `core/testing/src/main/kotlin/testing/FakeCore.kt:498`.
- Rule: `plan.md` "Playback rule"; fixture `web/test/fixtures/subtitles/choice-cases.json`; reference `web/public/lib/playback/subtitle-choice.js` (phase 03).
- Red team: failure-mode F3/F4/F8, security F2/F3, scope-critic F8, assumption-destroyer F4.

## Overview
Priority P1. Effort 1.5d. Version: next **minor**. Status: pending. Depends on phase 02; parallel with 03. Installs to devices together with phase 05.

## Key decisions
- **Reuse the part path:** `part_document` + the capped download loop (extracted from `install.rs` into one helper both use). No new transport.
- **Cache:** `<data_dir>/subtitles/<sha>.json.gz`, **size-capped LRU** (`MAX_SUBTITLE_CACHE_BYTES = 64 MiB`, mtime touched on hit with `File::set_modified`). No prune on install: an index without the v13 tables (a pre-v13 push) must not wipe held bundles. 64 MiB is roughly the whole library's bundles, so eviction is rare in practice.
- **Integrity:** one sha-checked fetch function shared by play and hold, behind a per-sha async mutex.
  - The sha must pass `valid_sha256`; `bytes ≤ 16 MiB`.
  - Writes go to a unique `<sha>.<pid>.<rand>.tmp`, then `sync_all`, then rename.
  - A hit that fails to decode is deleted and refetched.
- **Holds:**
  - (a) `CacheDataSourceWriter.write()` calls `currentCore()?.holdSubtitles(item.setId)`, which covers both preloaders. No new parameter, and a failure never fails the preload.
  - (b) Opening a lesson calls `holdCourseSubtitles(setId)` in the background. It fetches the bundles of that lesson and the **next 10 lessons** in the course's own order (same `group_key`) that are not cached yet, sequentially — user decision 2026-09-30 (not the whole course: Wall Street Story alone is 864 lessons ≈ 20 MB).
- **Audio language (parity):** the selected ExoPlayer track's language, else the first of the set's `alang`, else unknown. The same `audioLanguage` as the web.
- **Facts from the file:** carry `slang` too; the Android "Subtitles" fact reads `slang`, matching the web. The inline-assets language list it used disappears with the old DTO field.

## Requirements
Core (Rust)
- `catalog_subtitles.rs` (new, replacing the subtitle half of `catalog_assets.rs`): `tracks_by_set(conn)` (v13, else inline rows by `ORDER BY lang`; missing tables → empty), `bundle_ref`, `legacy_body`.
- DTO: `SubtitleTrack { track: u32, lang, forced, sdh, label }`; `SetSummary.subtitles: Vec<SubtitleTrack>`; `SetSummary.alang: Vec<String>`, `slang: Vec<String>` (JSON columns added to `COLUMNS`).
- `api/subtitles.rs` (new, uniffi): `subtitle_text(set_id, track) -> Option<String>`, `hold_subtitles(set_id) -> bool`, `hold_course_subtitles(set_id)`; every failure → `None`/`false` + one `tracing::warn`.
- `set_text` keeps summaries; kind `subtitle` answers `None`.

Android (Kotlin)
- `SubtitleTrackInfo(track, lang, forced, sdh, label)`; `MediaSet.subtitles`, `MediaSet.alang`, `MediaSet.slang`; repository mapping.
- `SubtitleTrackSource.load(setId, track: Int)` → `core.subtitleText`.
- `SubtitleChoice.kt`: port of `subtitle-choice.js` (`trackKey`, `audioLanguage`, `chooseSubtitles`, `toggleOn`, `visibility`, `subtitleOptions` = Off + regular tracks). Test reads the shared fixture.
- `SubtitleChoiceController`: inputs tracks, remembered (show scope), preferred (`PlayerPreferences` also loads scope `profile`, name `subtitle`), audio language (from `AudioChoiceController`, fallback `alang`), `last` in memory per show; outputs regular cues, else forced cues (forced shows when regular is off); `toggle()` remembers like `choose()`; exposes `subtitleStyleVisible` for phase 05's gates.
- `PlayerViewModel.toggleSubtitles()` delegates (file stays ≤ 200 lines).
- Lesson open → `core.holdCourseSubtitles(setId)` (fire-and-forget, IO scope) from `PlayerChoicesController`.
- `CacheDataSourceWriter.write()` → `currentCore()?.holdSubtitles(item.setId)` (runCatching).
- Facts: `TitleDetailScreen.kt:205`, `SeriesSummary.kt` → `slang`.
- `FakeCore`: new methods.

## Architecture
```
installed index → catalog_subtitles → SetSummary{subtitles, alang, slang} → MediaSet
player open: controller(tracks, remembered, preferred, audio = Exo tag ?? alang, last) → regular | forced
   └ core.subtitleText(setId, n) → bundle(sha): lock(sha) → cache hit (decodes?) | miss → part_document → capped download → sha ✓ → tmp+fsync+rename → LRU trim
preload write() → holdSubtitles(setId);  lesson open → holdCourseSubtitles(setId)
```

## Related code files
- Modify (Rust): `crates/mediagram-core/src/{catalog.rs,catalog_assets.rs,catalog_assets_tests.rs,api/set_text.rs,api/store/editorial.rs,dto/summary.rs,api/channel/install.rs,api/mod.rs,lib.rs}`.
- Create (Rust): `crates/mediagram-core/src/{catalog_subtitles.rs,catalog_subtitles_tests.rs,api/subtitles.rs,api/subtitles_tests.rs,api/channel/download.rs}`.
- Modify (Kotlin): `android/core/model/src/main/kotlin/MediaSet.kt`, `android/core/data/src/main/kotlin/{CatalogRepository.kt,PlayerPreferences.kt}`, `android/core/playback/src/main/kotlin/{SubtitleTrackSource.kt,CacheDataSourceWriter.kt}`, `android/feature/player/src/main/kotlin/{SubtitleChoice.kt,SubtitleChoiceController.kt,AudioChoiceController.kt,PlayerChoicesController.kt,PlayerViewModel.kt}`, `android/feature/catalog/src/main/kotlin/SeriesSummary.kt`, `android/ui-mobile/src/main/kotlin/ui/catalog/TitleDetailScreen.kt`, `android/core/testing/src/main/kotlin/testing/FakeCore.kt`, their tests.
- Regenerated: bindings (`scripts/generate-android-bindings.sh`), native `.so` (`scripts/build-android-core.sh`).
- Docs: `docs/system-architecture.md` (Android subtitles, holds, cache), `docs/project-changelog.md`.

## Implementation steps
1. `catalog_subtitles.rs` + tests (v13; inline; v12 without tables; both → v13).
2. `COLUMNS` + DTO (`subtitles`, `alang`, `slang`); editorial wiring; `set_text` narrowed.
3. Shared capped download helper; `api/subtitles.rs` with the `download_with`-style stub seam: hit; miss; bad sha shape (no fetch); sha mismatch (nothing written); oversize (no fetch); corrupt cached file (deleted, refetched); two concurrent callers → one fetch; LRU trim; course hold (batching, once per course).
4. Bindings + `.so`; Kotlin model, repository, source.
5. Rule port + shared-fixture test; controller (audio fallback, preference, forced when off, `last`, `toggle`, `subtitleStyleVisible`).
6. Preload hold in `write()`; course hold on lesson open; facts from `slang`; `FakeCore`.
7. `./gradlew test detekt`, `scripts/check.sh`; bump by pattern; changelog.

## Todo
- [ ] core tracks reader + DTO (`subtitles`, `alang`, `slang`)
- [ ] bundle fetch: sha lock, integrity, LRU cap, holds
- [ ] bindings + native rebuild
- [ ] rule port (shared fixture), controller, toggle, style flag
- [ ] preload hold via `currentCore()`, course hold, facts from `slang`, FakeCore
- [ ] tests, detekt, check.sh, manifests, changelog

## Success criteria
- Rust + Kotlin tests green; the shared-fixture test passes on both surfaces unchanged.
- Tablet (`ANDROID_SERIAL=caad49da`, `installDebug`) and TV box (`192.168.0.35:5555`, `installBenchmark` + `compile -m speed`), **test profile**, installed with phase 05: a Geldhochschule lesson opens with subtitles off; choosing its language shows cues; the next lesson follows. Navigate-only elsewhere.
- The bundle path on device is verified by phase 06's first real upload.

## Tests
| Level | What |
|---|---|
| Rust unit | tracks reader; fetch/caps/sha/lock/LRU; corrupt-file refetch; course hold |
| Kotlin unit | rule (shared fixture); controller: forced when off, audio fallback via `alang`, preference, `last`, toggle, style flag |
| Device | lesson on tablet + TV (above) |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| Stale native `.so` crashes at launch | Med × High | Rebuild core + bindings before install |
| Course hold fetches hundreds of bundles (Wall Street Story: 864 lessons) | Med × Low | Background, sequential, batched resolve, once per course per process; cached ones skipped; ~20 MB total |
| Bundle cache grows | Low × Low | 64 MiB LRU cap (a real bound, independent of index contents) |
| Fetch blocks the player | Med × Low | Cues load off the main thread; video unaffected |
| `PlayerViewModel.kt` passes 200 lines | Med × Low | Logic in the controllers; VM delegates |

## Security
Message ids only from the installed index; sha shape checked before a path is built; `bytes` checked before download; sha256 before caching; decode caps from `mlib_spec`.

## Rollback
Reinstall the previous APK; cached bundles are inert files.

## Next
Phase 05 (installs with this one) wires CC, the captions key, style gates and the profile setting.
