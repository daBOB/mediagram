# App Baseline Profile wiring (phase 02) report

## Versions
- `androidx.benchmark:benchmark-macro-junit4` 1.5.0 and plugin `androidx.baselineprofile` 1.5.0 (one catalog version `androidxBenchmark`). Stable, 2026-09-09 (developer.android.com/jetpack/androidx/releases/benchmark). No AGP opt-out needed on AGP 9; builds clean on 9.3.1.

## Files
- android/gradle/libs.versions.toml, android/settings.gradle.kts
- android/baselineprofile/build.gradle.kts (app.android.test convention unchanged; targetProjectPath :app; useConnectedDevices)
- android/baselineprofile/src/main/kotlin/com/mediagram/android/baselineprofile/BaselineProfileGenerator.kt
- android/app/build.gradle.kts (plugin, baselineProfile(project), direct profileinstaller)
- android/ui-tv/src/main/kotlin/ui/tv/TvApp.kt: `testTagsAsResourceId = true` on TvShell root (was not enabled)

## Journey
Cold start; profile picker (tag tv-profile-picker-first-tile) or no "Movies" pill -> log + stop. Else 6x DOWN on Home; UP then RIGHT until Movies pill (text "Movies*") is focused; ENTER; wait tag tv-movies-department-page; DOWN + 40 DOWN at 80 ms; ENTER on focused plate -> wait tag tv-title-page-body -> Back once. Never OK on settings, never plays, never Back at root. Logcat tag `BaselineProfile`.
UNVERIFIED on device: Movies pill text match / isFocused on merged Compose node; ENTER on a deep item may open a genre/collection page (still navigate-only; logs if no title page). Watch logcat on first run.

## Signing (apksigner verified)
No change needed. nonMinifiedRelease and benchmarkRelease inherit `release`; both signed with cert 5840181d3da5f44c9f0185bf4ac3345e5fe08786347350db04aaa6e69df4f576 = EXPECTED_CERT in release-android.sh (same as release). Installs over the box's release app. Without the key property (other machines) they are unsigned like release. Custom `benchmark` type untouched. Release APK now carries assets/dexopt/baseline.prof(m) (library starter profile until the app one is generated).

## Generation
Tasks exist: `generateReleaseBaselineProfile`, `generateBaselineProfile`; output to app/src/release/generated/baselineProfiles.
`cd android && ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:generateReleaseBaselineProfile`

## Verification
:baselineprofile:assemble, :app:assembleRelease/NonMinifiedRelease/BenchmarkRelease, testDebugUnitTest, :core:model:test, lint, androidTest compile tasks: all pass. No spotless/detekt at gradle root.
Worktree: gitignored jniLibs copied from main checkout to build; not committed.

## Risks
- nonMinifiedRelease inherits self_update=true: if a newer release is published during generation the app may replace itself. Afterwards the box runs the nonMinified build; reinstall release / let it self-update. Same applicationId and versionCode, adb -r fine.
- Journey touches household's real app state (focus/scroll restore), not Continue.
- Profile not generated/committed yet (lead, on device).
