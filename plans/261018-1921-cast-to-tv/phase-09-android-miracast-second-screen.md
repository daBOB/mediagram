# Phase 09 — Android: Miracast second screen

**Spec:** §6, §8. **Status:** pending.

**Revised 2026-10-20 at the user's request** ("on android phone and tablet i want player mode or remote control mode for casting"). The user's answers:
- **Two ways to cast, asked on each cast.** Play on TV asks: *the TV plays it* (the box cast of phases 06–08) or *this device plays it* (this phase: the phone or tablet plays and the picture goes to a TV screen).
- **The phone switches to the remote by itself** as soon as either cast starts ("its better automatically to switch. as soon as we start to cast we switch to remote control mode"). For this phase that means: the moment a presentation display is connected, the player turns into today's remote, driving its own player. Spec §6 already says so.
- **Remote control mode is today's remote** (phase 08): play/pause, seek, 15 s skips, next episode, subtitles, audio. No D-pad. **Starts only after a positive Miracast probe** (phase 01 report). On a negative probe, stop and bring the fallback to the user.

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
- Create (if the extraction needs a home): `ui-mobile/.../ui/player/RemoteFrame.kt`, the remote's frame lifted out of `CastRemoteScreen.kt`'s `CastRemote`: black, the title's backdrop at the remote's `ART_ALPHA`, `PlayerTopBar` with the title and its label, the notice pill, and a slot for the card. `CastRemote` and the presenting player both draw it, so the two remotes cannot drift.
- Modify:
  - `PlayerViewModel.kt`: `presenting: StateFlow<String?>`, the display's name, from `PresentationDisplays`.
  - `ui-mobile/.../ui/player/PlayerScreen.kt`:
    - while `presenting != null`, show a `PlayerPresentation`, dismissed when it goes `null`;
    - on the phone, switch to **the remote's look**, without asking: `RemoteFrame` with the label `"Playing on $name"`, and the control card driving the real player. As on the remote, the card is always shown: no auto-hide, no tap-to-hide, no double-tap seek, no notes or marks layer over a picture that is not there. The card holds what the remote's card holds (transport, seek bar, 15 s skips, next episode, subtitles, audio, speed), driving the real player, plus Task 9.3's **Playing on *name*** tool; no Framing, no Watch together, no Stop (Back leaves the player as always) and no picture-in-picture button;
    - turn PiP off (`setPictureInPictureParams` with auto-enter off).
  - `PlayerLifecycle.kt`: on `ON_STOP` while presenting, `pause()`.
  - `PlayerViewModel`: when the display goes away mid-play, pause; the player's own look comes back.
- Test: `ui-mobile/src/test/kotlin/ui/player/PlayerPresentationTest.kt`

- [ ] **Step 1: Failing tests:**
  - `aPresentationDisplayShowsPlayingOnItsName`;
  - `whilePresentingThePhoneShowsTheRemoteNotTheVideo`: no video surface on the phone, the backdrop and "Playing on Living Room TV" in the top bar, the card shown;
  - `whilePresentingTheCardStaysUp`: the card is still shown after the player's auto-hide delay and after a tap on the stage;
  - `noPipWhilePresenting`;
  - `losingTheDisplayPauses`, and the player's own look returns;
  - `stoppingWhilePresentingPauses`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `CastRemoteScreen`'s existing tests unchanged.
- [ ] **Step 5: Commit** `feat(android): play on a Miracast TV as a second screen, the phone as its remote`.

### Task 9.3: Play on TV asks which way, in the player

An ordinary app can't list Miracast TVs or connect to one (that needs a system permission), but it can open the system's Cast screen, which on the tablet is Xiaomi's Cast page with Living Room TV in it. The probe connected exactly that way.

**Files:**
- Create: `ui-mobile/.../ui/catalog/TvScreenSettings.kt`: `fun castSettingsIntent(): Intent` (`Settings.ACTION_CAST_SETTINGS`, `FLAG_ACTIVITY_NEW_TASK`) and `fun canOpenCastSettings(context): Boolean` (the intent resolves to an activity).
- Create: `ui-mobile/.../ui/catalog/CastWayDialog.kt`: the question, and the Android-only words below as constants in one place (they are not in `cast.json`: the web has no second way).
- Modify: `ui-mobile/.../ui/player/PlayerCardTools.kt`, `PlayerControlCard.kt` (`PlayerCardView.presentingOn: String? = null`, and Play on TV's greying rule below), `ui-mobile/.../ui/player/PlayerScreen.kt` (wires it).
- Test: `ui-mobile/src/test/kotlin/ui/player/CastWayTest.kt` (Robolectric Compose)

**Behaviour:**
- **Play on TV asks**, when the device can open the system Cast screen. A dialog titled **Play on TV** (`CastWords.PLAY_ON_TV`) with two choices and Cancel:
  - **The TV plays it**, under it *Mediagram on the TV box plays it. This device becomes the remote.* It does exactly what Play on TV did before this phase: the offer, the ask dialog, the remote.
  - **This device plays it**, under it *The picture goes to a TV screen through Cast. This device becomes the remote.* It opens the system Cast screen and nothing else; the player keeps its title and place, and Task 9.2 moves the picture when the display appears.
  - Back or Cancel closes it and does nothing. The first choice takes focus, as the ask dialog's first button does.
- **Greying.** The box choice is greyed with the reason Play on TV is greyed for today (Telegram not reachable, leave watch together first, …), which a screen reader hears after its name. The other choice touches no channel, so it is never greyed. While the device can open the Cast screen, the Play on TV tool itself is therefore never greyed.
- **No system Cast screen** (nothing handles `ACTION_CAST_SETTINGS`): no question; Play on TV offers straight away and greys exactly as before this phase.
- **While presenting**, the tool reads **Playing on *name*** instead and opens the system Cast screen at once (to disconnect), with no question.
- Phones and tablets only; the TV has no Play on TV (spec §8).

- [ ] **Step 1: Failing tests:**
  - `playOnTvAsksWhichWay`: both choices and Cancel are shown, the first focused;
  - `theTvPlaysItOffersAsBefore`: the offer carries the player's title, place, playing and rate;
  - `thisDevicePlaysItOpensTheSystemCastScreen`: the started intent's action is `android.settings.CAST_SETTINGS`, and no offer is made;
  - `aGreyedTvChoiceSaysWhyAndTheOtherStillWorks`;
  - `withoutTheSystemCastScreenPlayOnTvOffersStraightAway`;
  - `whilePresentingTheToolSaysPlayingOnTheTvAndOpensTheCastScreen`: with `presenting = "Living Room TV"` → "Playing on Living Room TV", no question.
- [ ] **Step 2: Run** `./gradlew :ui-mobile:testDebugUnitTest --tests '*CastWay*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus the phase 08 Play on TV tests.
- [ ] **Step 5: Commit** `feat(android): Play on TV asks whether the TV or this device plays it`.

### Task 9.4: The same question on a title page

**Files:**
- Modify: `ui-mobile/.../ui/catalog/PlayOnTvPill.kt` (and `TitlePills.kt` / the library wiring that opens the player, as needed).
- Test: `ui-mobile/src/test/kotlin/ui/catalog/PlayOnTvPillTest.kt` (create or extend)

**Behaviour:**
- The pill asks the same question with Task 9.3's `CastWayDialog` and the same greying rule.
- **The TV plays it:** the offer from the pill's point, as before this phase.
- **This device plays it:** opens the player for that title from the pill's point, as Play or Resume would, then the system Cast screen. When a presentation display is already connected, it only opens the player (the picture goes straight to the TV).
- Without the system Cast screen: unchanged from phase 08.

- [ ] **Step 1: Failing tests:** `thePillAsksWhichWay`; `thisDevicePlaysItOpensThePlayerAndTheCastScreen`; `withADisplayConnectedThisDevicePlaysItOnlyOpensThePlayer`; `theTvPlaysItOffersFromThePillsPoint`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): a title page asks which way to play on the TV`.

### Task 9.5: Device check (tablet `caad49da`, the user connects)

- [ ] Rebuild and install, pinned to `ANDROID_SERIAL=caad49da`, with the test profile.
- [ ] From the player, Play on TV → **This device plays it**: the system Cast screen opens. The Hisense is on with AnyView Cast; connect through Xiaomi's Cast page → Screen mirroring → Living Room TV (the probe's route, `reports/probe-261018-miracast-and-airplay-report.md`).
- [ ] Play a 1080p title. Check each of the following:
  - the video is full screen on the Hisense, subtitles included;
  - the tablet switched to the remote by itself: backdrop, "Playing on *name*", the card staying up;
  - the sound is on the TV;
  - Home on the tablet pauses, and back resumes on the TV;
  - disconnecting brings the video back to the tablet, paused, in the player's own look.
- [ ] Play on TV → **The TV plays it** still casts to the box and opens the remote (the box must be on with Mediagram in front).
- [ ] Check the TV picture is not black (the display is not secure: no `FLAG_SECURE`; a secure player window or surface would black it out).
