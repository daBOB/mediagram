# Android mobile domains and playback lifecycle

Status: DONE

## Changes

- Reorganized the existing `ui-mobile` module without adding dependencies between feature modules. Packages now follow source directories: `ui` holds app composition/navigation/chrome; `ui.catalog`, `ui.player`, `ui.profile`, `ui.setup`, `ui.settings`, and `ui.system` hold their screens and presentation helpers. Existing tests moved alongside those domains.
- Extracted the unchanged shared row component into `ui.components.Block` and the unchanged byte/budget formatting into `ui.formatting.ByteSize`. Catalog list naming stays in catalog; the player's add-to-list dialog stays in player because neither is actually shared.
- Added a defaulted `PlayerViewModel` parameter to `PlayerScreen`; Hilt remains its production default. Removed the trivial `shouldStopOnDispose` wrapper and its two boolean-only tests, retaining its equivalent Activity condition directly in the disposal effect.
- Added `PlayerLifecycleTest`, `PlayerTestActivity`, and `PlayerLifecycleFixture`. The harness composes the entire production `PlayerScreen`, uses a real Activity ViewModelStore, and drives the production `DefaultPlayerHandle`, `PlayerViewModel`, and `ProgressRecorder`. Only ExoPlayer, persistent watch storage, and sync scheduling are doubles.
- Added test-only dependencies already present in the catalog: Robolectric 4.16.1, MockK, and Compose UI testing from the existing BOM. Enabled Android resources for mobile JVM tests; no version changes or new catalog entries.

## Lifecycle evidence

1. `ActivityController.configurationChange` destroys the old Activity and its composition, reports `isChangingConfigurations == true` during disposal, creates a fresh Activity/composition, retains the exact ViewModel, and neither stops nor reloads/prepares media again. A themed ContextWrapper also exercises production Activity lookup.
2. Real Activity pause/stop emits ON_STOP while the composition remains. The current playhead is persisted exactly once, playback is not stopped, and returning to the foreground retains the playing state.
3. Pressing the production back button removes the player from composition, writes its position, and calls ExoPlayer.stop exactly once. Stopping the Activity afterward adds neither another stop nor another progress write.

These are Robolectric/Compose lifecycle tests, not physical-device codec or live Telegram tests. No user database or Telegram connection was used. The first harness attempt needed the new Activity made visible after configuration change; that harness failure is not evidence of an original production bug.

## Validation

- Package-only gate passed: `./gradlew :ui-mobile:testDebugUnitTest :app:compileDebugKotlin --no-daemon` (`/tmp/android-ui-packages.log`).
- New lifecycle suite passed: `./gradlew :ui-mobile:testDebugUnitTest --tests ui.player.PlayerLifecycleTest --no-daemon` (`/tmp/android-player-lifecycle-second.log`).
- Sensitivity checks temporarily changed only the production hooks and restored the exact source in a `finally` block:
  - Removing stop/save hooks failed the background and navigation cases (2 of 3): `/tmp/android-player-lifecycle-missing-hooks.log`.
  - Stopping unconditionally during configuration recreation failed the recreation case (1 of 3): `/tmp/android-player-lifecycle-stop-on-rotation.log`.
- Restored final gate passed: `./gradlew :ui-mobile:testDebugUnitTest :feature:player:testDebugUnitTest :ui-tv:compileDebugKotlin :app:compileDebugKotlin :app:hiltJavaCompileDebug --no-daemon` (`/tmp/android-ui-lifecycle-verified.log`). XML results: mobile 106 tests and player 42 tests, zero failures/errors/skips.
- `git diff --check -- android/ui-mobile` and whitespace inspection of new/moved Kotlin files passed.
- No background Gradle process remains owned by this task.

The Compose dependency choice follows the [official test setup](https://developer.android.com/develop/ui/compose/testing#setup); the resolved Compose library recommends its `junit4.v2.createEmptyComposeRule` API, used here. Robolectric's installed `ActivityController` API supplies real configuration recreation and lifecycle dispatch.

## Integration

All sources are saved; no project commit or scanner resolution was made. The controller owns the architecture documentation update and final independent review. Existing deprecation/stability-file warnings remain unchanged. No additional production behavior or UI flow changes were introduced.
