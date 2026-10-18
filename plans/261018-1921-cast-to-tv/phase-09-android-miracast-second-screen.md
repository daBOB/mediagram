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

### Task 9.3: Show on a TV screen, from inside the app

Added 2026-10-19 at the user's request ("yes add it to phase 09"). An ordinary app can't list Miracast TVs or connect to one (that needs a system permission), but it can open the system's Cast screen, which on the tablet is Xiaomi's Cast page with Living Room TV in it. The probe connected exactly that way.

**Files:**
- Modify: `ui-mobile/.../ui/player/PlayerCardTools.kt` (a tool after Play on TV); `PlayerControlCard.kt` (`PlayerCardActions.onTvScreen: (() -> Unit)? = null`, `PlayerCardView.presentingOn: String? = null`); `ui-mobile/.../ui/player/PlayerScreen.kt` (wires it).
- Create: `ui-mobile/.../ui/player/TvScreenSettings.kt`: `fun castSettingsIntent(): Intent` (`Settings.ACTION_CAST_SETTINGS`, `FLAG_ACTIVITY_NEW_TASK`) and `fun canOpenCastSettings(context): Boolean` (the intent resolves to an activity).
- Test: `ui-mobile/src/test/kotlin/ui/player/TvScreenButtonTest.kt` (Robolectric Compose)

**Behaviour:**
- The tool reads **Show on a TV screen** while no presentation display is connected, and **Playing on *name*** (`presenting`, Task 9.2) while one is. Either way, tapping it opens the system Cast screen (to connect, or to disconnect).
- Hidden when nothing on the device handles `ACTION_CAST_SETTINGS`, and hidden on the TV (the TV casts nothing, spec §8). Phones and tablets only; Android-only like the rest of Miracast (spec §8).
- Opening it starts nothing else: the player keeps playing, and Task 9.2 moves the picture when the display appears.
- The words are Android-only, so they live beside the player's other tool labels, not in `cast.json`.

- [ ] **Step 1: Failing tests:**
  - `theToolOpensTheSystemCastScreen`: the started intent's action is `android.settings.CAST_SETTINGS`;
  - `theToolIsHiddenWhenNothingHandlesIt`;
  - `whilePresentingTheToolSaysPlayingOnTheTv`: with `presenting = "Living Room TV"` → "Playing on Living Room TV".
- [ ] **Step 2: Run** `./gradlew :ui-mobile:testDebugUnitTest --tests '*TvScreen*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): open the system Cast screen from the player to put it on a TV`.

### Task 9.4: Device check (tablet `caad49da`, the user connects)

- [ ] Rebuild and install, pinned to `ANDROID_SERIAL=caad49da`, with the test profile.
- [ ] Connect from the player's **Show on a TV screen** (Task 9.3): the Hisense on AnyView Cast, then Xiaomi's Cast page → Screen mirroring → Living Room TV (the probe's route, `reports/probe-261018-miracast-and-airplay-report.md`).
- [ ] The user connects to AnyView Cast. Play a 1080p title. Check each of the following:
  - the video is full screen on the Hisense, subtitles included;
  - the tablet shows the card and "Playing on *name*";
  - the sound is on the TV;
  - Home on the tablet pauses, and back resumes on the TV;
  - disconnecting brings the video back to the tablet, paused.
- [ ] Check the TV picture is not black (the display is not secure: no `FLAG_SECURE`; a secure player window or surface would black it out).
- [ ] Record the results in `reports/walk-261018-miracast-report.md`.

**Success criteria:** the tests pass and the device check is recorded. The deliberate difference from spec §8 is already written there.
