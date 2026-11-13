# Phase 2: Android mark button

Spec § Marking. Android gets one control and a short confirmation; no list, no export (user's decision).

### Task 3: "Mark scene" in the phone/tablet and TV players

**Files:**
- Modify: `android/core/data/src/main/kotlin/WatchStateRepository.kt` (interface + `DefaultWatchStateRepository`; model mapping for `scenes` in `StateSnapshot.toModel` only if the snapshot model needs it — it does not for this phase, so skip it)
- Modify: `android/feature/player/src/main/kotlin/PlayerMarksController.kt` (`markScene`)
- Modify: `android/feature/player/src/main/kotlin/PlayerViewModelDelegates.kt` (`PlayerViewModel.markScene()`)
- Modify: `android/ui-mobile/src/main/kotlin/ui/player/PlayerCardBridge.kt` (`PlayerMarksActions.onMarkScene`) and the `PlayerMarks` composable that renders My List
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerControlsBridge.kt` (`TvMarksActions.onMarkScene`) and the TV marks row composable
- Test: `android/feature/player/src/test/kotlin/PlayerMarksTest.kt`; fakes of `WatchStateRepository` used by tests must gain the new method

**Interfaces:**
- Consumes: core `mark_scene(profile_id, set_id, at) -> Option<String>` from Task 2 (regenerate UniFFI bindings; rebuild the native `.so` — a stale one crashes at launch with UnsatisfiedLinkError).
- Produces: `suspend fun WatchStateRepository.markScene(setId: String, at: Double): Boolean` (true when the core returned an id); `fun PlayerMarksController.markScene(atSeconds: Double)`; `val PlayerMarksController.sceneNotice: StateFlow<String?>`; `fun PlayerViewModel.markScene()` reading the current film position from the player handle the ViewModel already uses for progress (`ProgressRecorder`'s source).
- Copy: notice `"Scene marked at 1:02:15"` (clock format the player already uses — `PlayerClock.kt`), cleared after 3 s; failure uses the existing `actionNotice` with `"Couldn't mark this scene"`.
- Control label `"Mark scene"`; on TV it sits in the same row as My List and is focusable like its neighbours.

- [ ] **Step 1: Write the failing tests** in `PlayerMarksTest.kt`: `markSceneRecordsTheOpenTitleAtThePosition` (fake repo records `("S1", 3735.0)`, `sceneNotice` is `"Scene marked at 1:02:15"`); `markSceneWithNoTitleOpenDoesNothing`; `aFailedMarkSaysSo` (fake returns false → `actionNotice == "Couldn't mark this scene"`).
- [ ] **Step 2: Run** `cd android && ./gradlew :feature:player:testDebugUnitTest --tests '*PlayerMarksTest*'` — expect FAIL.
- [ ] **Step 3: Implement** repository → controller → ViewModel delegate → both bridges and composables.
- [ ] **Step 4: Run** the test, then `cd android && ./gradlew testDebugUnitTest lint` (or the repo's `scripts/check.sh` Android part) — pass.
- [ ] **Step 5: Commit** `feat(android): mark a scene from the player`.
