# Phase 02: App Baseline Profile

**Priority:** medium. **Status:** pending.

## Context

- `androidx-profileinstaller` is listed in `android/gradle/libs.versions.toml` but no module declares it. Compose pulls it in transitively.
- The 2026-09-26 perf report (`plans/reports/tv-box-scroll-perf-260926-0045-report.md`) measured on the benchmark build:
  - Movies wall with a held key: p90 32–36 ms, against a 33 ms target.
  - Paced D-pad scrolling: p90 25 ms.
  - Library profiles were already applied (`speed-profile [install-dm]`).
  - An app profile adds AOT coverage for the app's own code: `TvWall`, `TvPlate`, Home rows, title pages.
- AGP 9.3.1. The `app.android.test` convention plugin exists in `build-logic` but no module uses it yet.
- `:app` already has a custom `benchmark` build type (`initWith(release)`, profileable). The baselineprofile plugin adds `nonMinifiedRelease` and `benchmarkRelease`, which are different names.
- The TV box runs the self-updating **release** build (`android-tv-self-update` memory), installed through PackageInstaller. `profileinstaller` writes the profile on first launch, and background dexopt compiles it.

## Design

- **New module `:baselineprofile`** (`com.android.test` through `app.android.test`) with `targetProjectPath = ":app"` and a `BaselineProfileRule` generator. The journey:
  1. Cold start the app.
  2. If the library is loaded, D-pad down through the Home rows.
  3. Open Movies and hold DOWN through the wall.
  4. Open one title page, then go Back.
  - It **navigates only**:
    - never press OK on a setting;
    - never start playback, because test plays land in Continue;
    - never press Back at the library root.
  - If setup or the profile picker shows, the journey stops after startup and logs that it did, rather than failing.
- **`:app`:** apply the `androidx.baselineprofile` plugin, add `baselineProfile(project(":baselineprofile"))` and make `profileinstaller` a direct `implementation`.
- **Versions:** read `developer.android.com/jetpack/androidx/releases/benchmark` (docs-seeker) for the latest stable `androidx.benchmark` / `androidx.baselineprofile` release that supports AGP 9.3. Pin it in the catalog.
- **The generated profile is committed.** Re-generate it when the TV home or wall changes substantially; this is not per release.

## Open question (user decides at the plan gate)

Which device generates the profile?
- **TV emulator (recommended if it is signed in with a library).** It touches no real data. The profile is dex-level and independent of ABI.
- **TV box.** The generator installs a `nonMinifiedRelease` build over the self-updating release app. That is only safe if it is signed with the same release key, and the box must get the real release reinstalled afterwards. It touches the household's real install.

## Files

- **Create:**
  - `android/baselineprofile/build.gradle.kts`
  - `android/baselineprofile/src/main/kotlin/.../BaselineProfileGenerator.kt`
  - `android/app/src/release/generated/baselineProfiles/baseline-prof.txt` (generated)
- **Modify:**
  - `android/settings.gradle.kts`
  - `android/gradle/libs.versions.toml`
  - `android/app/build.gradle.kts`

## Steps

- [ ] Look up compatible versions; add them to the catalog.
- [ ] Create the module and the journey; `./gradlew :baselineprofile:assemble`.
- [ ] Wire `:app` and add `profileinstaller`; `./gradlew :app:assembleRelease`.
- [ ] Generate on the chosen device (`ANDROID_SERIAL` pinned); commit the profile.
- [ ] Check that `unzip -l` on the release APK lists `assets/dexopt/baseline.prof`.
- [ ] On the box's benchmark build, re-measure Movies wall held-key gfxinfo (same method as the 09-26 report). Record before and after.

## Success criteria

The Baseline Profile acceptance rows in `plan.md`. The held-key p90 is no worse than 32–36 ms, and ideally under 33 ms. Report the number whatever it is.

## Risks

- **Release signing.** The signing used by `nonMinifiedRelease` must not break `scripts/release-android.sh` or the release signing.
- **AGP 9 plugin compatibility.** Confirm it before writing the module.
