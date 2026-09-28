# Phase 3 implementation report — core anime flag and Android data

## Executed phase
- Phase: phase-03-core-anime-flag-and-android-data.md
- Plan: plans/260928-2042-anime-department
- Worktree: `.claude/worktrees/agent-a6c7a5ec4d3ff3685`
- Branch: `feat/anime-core`
- Status: completed

## Files modified
- `crates/mediagram-core/src/shows/anime.rs` (new, 71 lines) — `is_anime`, `anime_overrides`
- `crates/mediagram-core/src/shows/anime_tests.rs` (new, 65 lines)
- `crates/mediagram-core/tests/shared_anime_fixtures.rs` (new, 57 lines) — runs `web/test/fixtures/anime/cases.json`
- `crates/mediagram-core/src/shows/mod.rs` — `mod anime;` + re-export
- `crates/mediagram-core/src/shows/facts.rs` — `ShowFacts.original_language`, `facts()` query, 2 tests
- `crates/mediagram-core/src/dto/summary.rs` — `SetSummary.anime: bool`, `false` in `summary_from`
- `crates/mediagram-core/src/api/store/editorial.rs` (179 → 185 lines) — reads `anime_overrides` once, computes `summary.anime` per row
- `crates/mediagram-core/src/api/store/editorial_tests.rs` — 4 new cases (index facts, sidecar fallback, override in/out, v10 index)
- `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` — regenerated
- `android/core/model/src/main/kotlin/MediaSet.kt` — `val anime: Boolean = false`
- `android/core/data/src/main/kotlin/CatalogRepository.kt` — `anime = summary.anime` in `toMediaSet`
- `android/core/data/src/test/kotlin/CatalogRepositoryTest.kt` — `summary()` builder param + `aSetSaysWhetherItIsAnime` test
- `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`, `android/app/build.gradle.kts` — 0.77.0 → 0.77.1
- `docs/project-changelog.md` — 0.77.1 entry
- `docs/system-architecture.md` §10.1 — note on how `SetSummary.anime`/`MediaSet.anime` is computed
- `plans/260928-2042-anime-department/phase-03-core-anime-flag-and-android-data.md`, `plan.md` — marked completed

## Tasks completed
All implementation steps 1–7 done as written. `is_anime` and `anime_overrides` are a direct port of `web/src/catalog/anime.ts`'s `isAnime`/`animeOverrides` (same kind check, override precedence, `ja`+"Animation" rule, NULL-row skip, poster-key format). `ShowFacts.original_language` follows the existing `optional_column` accommodation pattern for pre-v11 indexes. `editorial::enrich` reads overrides once beside `ratings`, and computes `anime` per row from genres + facts' `original_language` (index or sidecar fallback, same `or_insert` merge as everything else in that function) + the override, after genres/facts are attached and before `resolve_artwork`.

## Tests status
- Type check / build: pass (`cargo build --all`)
- `cargo clippy -p mediagram-core --all-targets -- -D warnings`: pass, no warnings
- `cargo test -p mediagram-core`: 405 lib tests + all integration suites (incl. `shared_anime_fixtures`) pass, 0 failed
- `scripts/check.sh`: **all checks passed** — clippy, `cargo test --all`, bun test (web deps present), gradle `testDebugUnitTest`/`:core:model:test`/`lint`/tv+ffmpeg androidTest compile — all green

## Native core / bindings
- `ANDROID_NDK_HOME=.../28.2.13676358 scripts/generate-android-bindings.sh` run inside the worktree: cross-built `.so` for arm64-v8a, armeabi-v7a, x86_64, x86 (release), then regenerated `mediagram_core.kt` from the arm64-v8a `.so`.
- `.so` files are gitignored (`android/core/rust/src/main/jniLibs/`), confirmed untracked by `git status`; only the `.kt` bindings file is committed, matching the repo's existing convention.
- Verified with the NDK's `llvm-nm -D` against the rebuilt `libmediagram_core.so`: `UNIFFI_META_MEDIAGRAM_CORE_RECORD_SETSUMMARY` present, confirming the rebuilt library carries the updated `SetSummary` record (a record gains no separate per-field FFI symbol; UniFFI encodes the field list in this metadata blob, read at Kotlin's binding-generation time — that generation step, run against this same `.so`, is what emitted the `anime` field into the `.kt` file).

## Deviations from the phase file
- None material. The phase's doc instruction named "the Android catalog field list / §10.1" for `system-architecture.md`; no separate field-list section exists, so the note went into §10.1 beside the existing `anime_overrides`/`original_language` prose instead.
- `editorial.rs` landed at 185 lines (phase estimated ~187), `summary.rs` untouched line-count-wise beyond the one field (phase estimated ~176; actual close, well under 200 either way).

## Version
- 0.77.0 → 0.77.1 (patch: an internal core/data-layer change, nothing yet reachable from any Android screen — same precedent as 0.73.1). `versionCode` untouched (18). `cargo metadata --locked --offline` succeeds.

## Next steps
- Phase 4 (Android UI: Anime shelf on phone/tablet/TV) is unblocked.
- Phase 4's own device steps should start from `scripts/build-android-core.sh` per the phase's risk note (a stale `.so` against these new bindings would crash at launch) — this worktree's `.so` is fresh, but a device install was out of scope here (hard limit) and was not attempted.
