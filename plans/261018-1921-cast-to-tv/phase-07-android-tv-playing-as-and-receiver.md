# Phase 07 — Android TV: playing-as profile and receiver

**Spec:** §2 (TV side), §3, §4, §5 (TV label). **Status:** pending. This is the largest internal change.

**Context:**
- **Profile writes** are implicit through `WatchStateRepository.writing{}` → `selection()` (`core/data/.../WatchStateRepository.kt:404-418`). The core already takes an explicit profile id on every call (`setProgress(profileId, …)`, `setWatched`, `snapshot`, `watchedToday`).
- **The player:**
  - `PlayerViewModel.kt` (deps :37-55; `stop()` :194; the daily limit :78, :110-115);
  - `PlayerViewModelOpen.kt` (`open` :30; the together start position :54);
  - `ProgressRecorder.kt:30`, `DailyLimitGate.kt:32`, `PlayerChoicesController.kt:143,194`, report profile :137;
  - `PlayerViewModelTogether.kt` (`togetherController(...)` :32, `labelOf` :69);
  - `together/TogetherController.kt` (`open` :79), `TogetherFollowing.kt` (`onLive` :28), `TogetherUi.kt` (label :82);
  - `UpNextController.kt` (`switchTo` :186, refused :196).
- **TV UI:**
  - `ui-tv/.../TvApp.kt` (`TvStep`), `profile/TvProfileGate.kt` (chosen branch :60);
  - `TvLibrary.kt` (`when(top)` :141), `TvLibraryBranches.kt:42` (`TvPlayerBranch`);
  - `player/TvPlayerScreen.kt:60`, `player/TvPlayerTopBar.kt:53`;
  - `catalog/TvCatalogScreen.kt:118` (`chosenKey`);
  - `ui-common/.../LibraryPositions.kt` (`openPlayer` :189 is gated; `replacePlayer` :221; `toCatalog` :299).
- **Tests:**
  - `feature/player/src/test/kotlin/PlayerViewModelTestFixture.kt` (`buildViewModel`);
  - `core/testing/.../WatchStateFixture.kt` (two profiles: seed through `fixture.core`);
  - `FakeCore.watchedTodayByProfile`;
  - `together/TogetherRig.kt:49`;
  - Robolectric Compose tests in `ui-tv/src/test`.

### Task 7.1: Writes for a named profile

**Files:**
- Modify: `core/data/src/main/kotlin/WatchStateRepository.kt`
- Test: `core/data/src/test/kotlin/WatchStateForAnotherProfileTest.kt`

**Interfaces — Produces:**
- `setProgress(setId: String, at: Double, duration: Double?, profileId: String? = null)`
- `markFinished(setId: String, profileId: String? = null)`
- `suspend fun snapshotOf(profileId: String): WatchSnapshot?`

`null` means the chosen profile, exactly as today. A write for another profile goes through a `writingFor(profileId)` beside `writing{}`, and never refreshes the chosen profile's snapshot.

- [ ] **Step 1: Write the failing tests.**
  - `progressForAnotherProfileIsThatProfiles`: with profiles A (chosen) and K, `setProgress("s1", 300.0, 2400.0, profileId = "K")` → `FakeCore`'s progress for K has s1 at 300, A has none, and `snapshot.value.progress` stays empty.
  - `finishedForAnotherProfileIsThatProfiles`.
  - `snapshotOfReadsTheNamedProfile`.
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest --tests '*WatchStateForAnotherProfile*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): watch state can be written for a profile other than the chosen one`.

### Task 7.2: The player plays as the caster

**Files:**
- Modify: `PlayerViewModel.kt`, `PlayerViewModelOpen.kt`, `PlayerSession.kt`, `ProgressRecorder.kt`, `DailyLimitGate.kt`, `PlayerChoicesController.kt`, `PlayerHandle.kt` + `DefaultPlayerHandle.kt` + `FakePlayerHandle` (`openSet`)
- Test: `feature/player/src/test/kotlin/PlayerPlayingAsTest.kt`

**Interfaces — Consumes:** `TogetherLive.takeCast()` and `ArmedCast` (phase 06).
**Interfaces — Produces:**
- `PlayerViewModel.playingAs: StateFlow<Profile?>`. It is non-null while this player plays a cast.
  - **Set in `open()`:** `togetherLive.takeCast()?.takeIf { it.set == setId }`, looking up its `Profile` in `repository.profiles.value`.
  - **Cleared** on `stop()` and when the cast is over (Task 7.3).
- `ProgressRecorder.save(setId, atSeconds, observedDurationSeconds, profileId: String?)`.
- `DailyLimitGate(repository, profile: () -> Profile?)`. The view model passes `{ playingAs.value ?: repository.chosenProfile.value }`.
- `PlayerChoicesController` takes `profileId: () -> String?` in place of reading `chosenProfileId` (:143, :194), with the same fallback.
- `PlayerHandle.openSet: StateFlow<String?>`, set by `open`, cleared by `stop`.

- [ ] **Step 1: Write the failing tests** (`buildViewModel`, two profiles A (chosen, grown-up) and K (kid, daily limit 30)).
  - `castProgressGoesToTheCaster`: `castOnOpen(ArmedCast(id = 900, set = "s1", profile = "K", from = "Tablet", playing = true))`, `open("s1")`, advance 10 s of playback, `stop()` → progress written for K, none for A.
  - `castDailyLimitIsTheCasters`: K has watched 30 min today → an up-next switch is refused.
  - `castPreferencesAreTheCasters`: `PlayerPreferences.load` is asked for K.
  - `tvProfileIsUntouchedByACast`: `chosenProfileId` and `snapshot` are unchanged afterwards, and `playingAs` is `null` after `stop()`.
  - `anOpenWithoutACastPlaysAsTheChosen`: `playingAs` is `null`, and writes go to A.
- [ ] **Step 2: Run** `./gradlew :feature:player:testDebugUnitTest --tests '*PlayerPlayingAs*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus all of `:feature:player` (the existing callers of `save`/`DailyLimitGate` updated).
- [ ] **Step 5: Commit** `feat(android): the player can play as another profile`.

### Task 7.3: The TV in the cast session

**Files:**
- Modify: `together/TogetherController.kt` (`open`), `TogetherFollowing.kt`, `TogetherUi.kt`, `PlayerViewModelTogether.kt`, `PlayerViewModelOpen.kt`, `UpNextController.kt` (refusal hook)
- Create: `feature/player/src/main/kotlin/together/CastTrackSync.kt`
- Test: `feature/player/src/test/kotlin/together/TogetherCastTvTest.kt` (on `TogetherRig`, label `"TV"`)

**Interfaces — Produces:**
- `TogetherController.open(set: String, cast: ArmedCast? = null): Playback?`. With a matching `cast` it calls `live.accept(cast.id, me)` and enters that session. It returns `Playback(set, playing = false, pos = expected)`, so the handle opens paused at the expected position. When `accept` gives `null` → `castOver`.
- `TogetherController.firstFrame()`: called from a `Player.Listener.onRenderedFirstFrame` the view model adds. Only on the first call after a playing offer is accepted, and only while the session is live and not ended, it sends `viewer(PlaybackChange(playing = true))`.
- `TogetherController.castOver: StateFlow<Boolean>`. It turns `true` when a session this TV holds as `cast.tv` ends or goes. It is exposed as `PlayerViewModel.castOver`.
- `TogetherUi.label`: `castFrom(session.cast.from)` while this screen is the session's `cast.tv`; otherwise unchanged.
- `CastTrackSync(controller, choices: StateFlow<PlayerChoices>, chooseAudio: (AudioOption) -> Unit, chooseText: (String) -> Unit, scope)`:
  - **While this screen is `cast.tv`, after each change of `choices`, it writes `live.tracks(...)`**, skipping a list identical to the last one written:
    - audio ids are `"${groupIndex}.${trackIndex}"` and labels `option.text`;
    - text entries are the `subtitleOptions` other than `SUBTITLES_OFF`, as `(value, label)`;
    - `audio`/`text` are the selected ids, with `""` for off.
  - **When an edit by another screen changes `cast.audio` or `cast.text`, it applies the change:** the matching `AudioOption`, or `chooseText(value)`, with `SUBTITLES_OFF` for `""`.
- **Up next refused by the daily limit while casting:** the controller `end`s the session, and the remote shows "Finished".

- [ ] **Step 1: Write the failing tests.**
  - `acceptOnOpenJoinsAsTheTv`: the edit has the TV as a member with `remote = false`, `cast.tv == screen`, `state == ON` and `playing == false`.
  - `playingOfferStartsAtFirstFrame`: `firstFrame()` → one `act` edit with `playing = true`; a second `firstFrame()` sends nothing.
  - `stopBeforeFirstFrameClosesWithoutPlaying`: after the accept, the remote's `finished` edit is pushed; then `firstFrame()` → no edit is sent, and `castOver == true`.
  - `backOnTheTvEndsTheCast`: `leave()` → the edit is `ended = true`, because the remote alone doesn't keep it.
  - `anotherTvTookItClosesThePlayer`: `accept` gives `null` → `castOver == true`.
  - `labelReadsCastFromTablet`.
  - `tracksAreWrittenOnceKnown`: the choices hold two audio and two subtitle options → one `tracks` edit, and no second one for identical choices.
  - `aRemoteSubtitleChoiceIsApplied`: a pushed edit with `text = "de"` by `tab-1` → `chooseText("de")`.
  - `aKidOutOfTimeEndsTheCastAtTheTitlesEnd`.
- [ ] **Step 2: Run** `./gradlew :feature:player:testDebugUnitTest --tests '*TogetherCastTv*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus all `together.*` tests.
- [ ] **Step 5: Commit** `feat(android): the TV takes a cast, plays it, and keeps the remote's track list`.

### Task 7.4: The receiver

**Files:**
- Create: `feature/player/src/main/kotlin/CastReceiverViewModel.kt`
- Test: `feature/player/src/test/kotlin/CastReceiverViewModelTest.kt`

**Interfaces — Consumes:** `TogetherLive.requests`, `busy`, `refuse`, `castOnOpen`; `answerOf`, `TvState`, `CastWords`, `ageReason`, `timeReason`; `forKidsProfile` (`core/model/AgeRating.kt:50`); `dailyLimitState` (`DailyLimit.kt:44`); `PlayerHandle.openSet`.
**Interfaces — Produces:**
- `@HiltViewModel class CastReceiverViewModel @Inject constructor(live: TogetherLive, catalog: CatalogRepository, repository: WatchStateRepository, handle: PlayerHandle)`.
- `data class CastOpen(val set: String, val replace: Boolean)`.
- `val opening: StateFlow<CastOpen?>`: collecting it is what listens (built over `live.requests`, `WhileSubscribed(5_000)`). Each request is answered once, keyed by id and state, with `answerOf(s, TvState(screen, busy, refusal), now)`.
  - `screen` is this view model's own random UUID.
  - `busy` is the title line of `handle.openSet.value` (null when the player is closed).
  - `refusal`, in this order:
    1. an unknown profile → `CastWords.PROFILE_UNKNOWN`;
    2. a set the TV's catalog lacks → `CastWords.LIBRARY_MISSING`;
    3. a kid and `forKidsProfile(listOf(set), snapshotOf(profile)?.kidsMarks.orEmpty(), kid.kidsLimit)` empty → `ageReason(name)`;
    4. a kid whose `dailyLimitState(limit, watchedToday(id))` is `OUT` → `timeReason(name)`.
  - The answers: `Busy` → `live.busy`; `Refuse` → `live.refuse`; `Accept` → `live.castOnOpen(ArmedCast(...))`, then `opening.value = CastOpen(set, replace = busy != null)`.
- `fun opened()`: clears `opening` once the UI has navigated.

- [ ] **Step 1: Write the failing tests** (`FakeTogetherLive` with settable `requests`, recording moves).
  - `idleTvAcceptsAndOpens`.
  - `busyTvAnswersWithItsTitle`.
  - `replaceOpensInsteadOfThePlaying`: `replace = true`.
  - `unknownProfileIsRefused`.
  - `missingTitleIsRefused`.
  - `kidAboveAgeIsRefused`: the note is `ageReason("Mia")`.
  - `kidOutOfTimeIsRefused`.
  - `aRequestIsAnsweredOnce`: the same id and state pushed twice → one move.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): the TV answers a cast: accept, busy or refused`.

### Task 7.5: The TV opens it, and comes back

**Files:**
- Modify:
  - `ui-common/.../LibraryPositions.kt`: add `castPlayer(id: String)`, which replaces a top player frame or pushes one, without the play gate, because the receiver has already checked the caster's limits;
  - `ui-tv/.../TvLibrary.kt`: collect `opening`, call `castPlayer` then `opened()`;
  - `TvLibraryBranches.kt`: `TvPlayerBranch` gets `onCastOver`, which does `toCatalog()` then asks Home;
  - `TvCatalogScreen.kt`: a `homeRequest: Int` param; `LaunchedEffect(homeRequest)` sets `chosenKey = CatalogTab.Home.key` when it is > 0;
  - `profile/TvProfileGate.kt`: when no profile is chosen and `opening != null` (or a cast it opened still plays), show `TvPlayerScreen` for that set over the picker, back to the picker on `castOver`;
  - `player/TvPlayerScreen.kt`: `LaunchedEffect(castOver)` calls an `onCastOver` param;
  - `player/TvPlayerTopBar.kt`: the marks (watchlist, kids, add-to) are hidden while `playingAs != null`.
- Test: `ui-tv/src/test/kotlin/ui/tv/TvCastHostTest.kt` (Robolectric Compose, the way the existing `ui-tv` tests set up Hilt-free fakes)

- [ ] **Step 1: Write the failing tests.**
  - `aCastOpensThePlayerFromHome`.
  - `aCastReplacesAnOpenPlayer`.
  - `aCastOverReturnsToHome`: from a title page → after `castOver`, the shelves are on Home.
  - `aCastWithNoProfileOpensOverThePicker`, and comes back to it.
  - `marksAreHiddenDuringACast`.
- [ ] **Step 2: Run** `./gradlew :ui-tv:testDebugUnitTest --tests '*TvCastHost*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `./gradlew :ui-tv:testDebugUnitTest :ui-common:testDebugUnitTest`.
- [ ] **Step 5: Commit** `feat(android): the TV opens a cast on any screen and returns home after`.

**Success criteria:** `./gradlew testDebugUnitTest detekt lint` passes, and the TV's own profile, places and Continue are untouched by a cast in every test.
