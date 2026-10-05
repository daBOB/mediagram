# Phase 01: TV backdrops at w1280, re-fetch narrower ones

**Priority:** high. **Status:** pending.

## Context

- `android/core/data/src/main/kotlin/BackdropWidth.kt` picks the width by `smallestScreenWidthDp >= 600`. The TV box reports 960×540dp, so it gets **780**.
- `TvCoverSlide` (Home hero) and `TvDepartmentHero` draw backdrops at full or fractional 960dp width, which is 1920 px on the box. A w780 image is stretched about 2.5×.
- The web player asks w1280 (`crates/mediagram/src/commands/posters.rs:60`). Phase 01 brings the TV level with it.
- `crates/mediagram-tmdb/src/poster_files.rs`:
  - `download_each` skips any `<key>.jpg` that exists.
  - `already_held` counts the same way.
  - Backdrop keys are `<title key>-bg`, with no width in them. A width change alone therefore never replaces an existing file.
- `isTelevision(context)` lives in `android/app/.../SurfaceSelection.kt`, which `core:data` cannot see.

## Design

**Rust (`poster_files.rs`), the one place every caller routes through:**
- A ref with `backdrop_width = Some(w)` counts as held only when two things are true:
  - `<key>.jpg` exists;
  - a sibling record `<key>.width` exists and holds a number ≥ `w`.
- A ref with no width (posters) keeps today's rule: the file existing is enough.
- One private predicate `is_held(ref, dir)` serves both `download_each` and `already_held`, so the two cannot disagree.
- After a successful backdrop download, write `<key>.width`, owner-only via `write_private`.
- When a re-download fails and `<key>.jpg` already exists:
  - keep the old file and count the key as written, so it is not reported as failed;
  - leave the record alone, so the next run tries again.
  - The current download path already preserves the old file, because it writes only after the whole body has arrived.
- Export packages never contain backdrops (posters only, width `None`), so no `.width` file reaches a package.

**Kotlin:**
- `DeviceBackdropWidth`: a TV counts as wide, so it gets 1280; otherwise the existing sw600 rule applies.
- **DRY:** move `isTelevision` down to a module that both `:app` and `:core:data` already depend on, and point `:app` at it. Do not copy it.
  - Check the dependency graph and pick the lowest such module.
  - If there is none without a new edge, put it in `core:data` and have `:app` import it from there.
  - Keep `SurfaceSelectionTest` passing, moved with the function.

**Coil:**
- The image file keeps its path when it is replaced. Check that Coil 3's file cache key includes the last-modified time for the data type the TV passes (path String, File or Uri).
- If it does not, add the last-modified time to the request's memory and disk cache key at the one place backdrops are loaded.

## Files

- **Modify:**
  - `crates/mediagram-tmdb/src/poster_files.rs`
  - `android/core/data/src/main/kotlin/BackdropWidth.kt`
  - `android/app/src/main/kotlin/com/mediagram/android/SurfaceSelection.kt` (moved)
  - its callers (`MainActivity.kt`, `di/UpdateModule.kt`)
- **Tests:**
  - `crates/mediagram/tests/export_posters.rs` or a new `crates/mediagram-tmdb` test, matching where `download_into` is tested;
  - `crates/mediagram-core/tests/artwork_fetch.rs` (`backdrops_already_held` counts);
  - a `DeviceBackdropWidth` unit test.

## Steps

- [ ] Rust, tests first:
  - record 780 and want 1280 → re-fetched and the record becomes 1280;
  - record 1280 and want 780 → held;
  - no record → fetched once, then held;
  - re-fetch fails with the old file present → old file kept and the key counted as written;
  - posters unchanged.
- [ ] Implement `is_held` and the record write; `cargo test --workspace`.
- [ ] Move `isTelevision`; make `DeviceBackdropWidth` return 1280 on TV, with a test covering TV, phone and tablet.
- [ ] Check Coil's file cache key and fix it only if it is stale.
- [ ] `./gradlew testDebugUnitTest lint`.

## Success criteria

The acceptance rows for backdrops in `plan.md`. On the box (phase 04), the hero is visibly sharper and the backdrop files on the box are 1280 px wide.

## Risks

- **One-time desktop re-fetch.** The desktop has no width records, so `mediagram posters` re-fetches its backdrops once. Accepted.
- **Re-fetch loops.** A backdrop whose TMDB original is narrower than 1280 cannot loop, because the record stores the width *asked for*, not the pixel width received.
