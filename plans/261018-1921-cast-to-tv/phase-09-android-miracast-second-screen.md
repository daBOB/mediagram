# Phase 09 — Android: Miracast second screen

**Spec:** §6, §8. **Status:** pending. **Starts only after a positive Miracast probe** (phase 01 report). On a negative probe, stop and bring the fallback to the user.

**Context:**
- `ui-common/.../player/PlayerVideo.kt:72`: `Video(player, framing, …)` over media3-compose `PlayerSurface`.
- `ui-common/.../player/SubtitleLayer.kt`: subtitles are drawn by the app in Compose, so the second screen must host them too.
- `ui-mobile/.../ui/player/PlayerScreen.kt:59`; `PipController.kt:50` (`setPictureInPictureParams` :65).
- `ui-common/.../player/PlayerLifecycle.kt:31` (start/stop hooks).
- There is no `DisplayManager` use anywhere yet.

### Task 9.1: Which presentation display is connected

**Files:**
- Create: `feature/player/src/main/kotlin/PresentationDisplays.kt`
- Test: `feature/player/src/test/kotlin/PresentationDisplaysTest.kt` (Robolectric `ShadowDisplayManager.addDisplay("w1920dp-h1080dp")`, flagged as a presentation display)

**Interfaces — Produces:**
- `class PresentationDisplays @Inject constructor(@ApplicationContext context: Context)`.
- `val display: StateFlow<Display?>`: the first of `getDisplays(DISPLAY_CATEGORY_PRESENTATION)`, kept current by a `DisplayListener` while collected.

- [ ] **Step 1: Failing tests:** `noSecondScreenIsNull`, `aPresentationDisplayIsFound`, `removingItGoesBackToNull`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS. **Step 5: Commit** `feat(android): notice a wireless display the player can use`.

### Task 9.2: The picture on the TV, the remote in the hand

**Files:**
- Create: `ui-mobile/.../ui/player/PlayerPresentation.kt`. It holds `class PlayerPresentation(context, display) : Presentation`, whose content is a `ComposeView` hosting `Video(...)` and the subtitle layer, full screen and black. Its view-tree lifecycle, saved-state and view-model owners are the activity's, so Compose runs inside a `Presentation` window.
- Modify:
  - `PlayerViewModel.kt`: `presenting: StateFlow<String?>`, the display's name, from `PresentationDisplays`.
  - `ui-mobile/.../ui/player/PlayerScreen.kt`:
    - while `presenting != null`, show a `PlayerPresentation`, dismissed when it goes `null`;
    - on the phone, draw the backdrop dimmed in place of the video, with the label `"Playing on $name"`, and the normal control card driving the real player;
    - turn PiP off (`setPictureInPictureParams` with auto-enter off, and no PiP button).
  - `PlayerLifecycle.kt`: on `ON_STOP` while presenting, `pause()`.
  - `PlayerViewModel`: when the display goes away mid-play, pause.
- Test: `ui-mobile/src/test/kotlin/ui/player/PlayerPresentationTest.kt`

- [ ] **Step 1: Failing tests:**
  - `aPresentationDisplayShowsPlayingOnItsName`;
  - `theLocalScreenShowsNoVideoWhilePresenting`;
  - `noPipWhilePresenting`;
  - `losingTheDisplayPauses`;
  - `stoppingWhilePresentingPauses`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): play on a Miracast TV as a second screen, the phone as its remote`.

### Task 9.3: Device check (tablet `caad49da`, the user connects)

- [ ] Rebuild and install, pinned to `ANDROID_SERIAL=caad49da`, with the test profile.
- [ ] The user connects to AnyView Cast. Play a 1080p title. Check each of the following:
  - the video is full screen on the Hisense, subtitles included;
  - the tablet shows the card and "Playing on *name*";
  - the sound is on the TV;
  - Home on the tablet pauses, and back resumes on the TV;
  - disconnecting brings the video back to the tablet, paused.
- [ ] Record the results in `reports/walk-261018-miracast-report.md`.

**Success criteria:** the tests pass and the device check is recorded. The deliberate difference from spec §8 is already written there.
