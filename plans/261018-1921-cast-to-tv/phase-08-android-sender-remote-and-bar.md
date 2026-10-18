# Phase 08 — Android phone/tablet: Play on TV, remote, bar

**Spec:** §2 (sender), §3 (remote side), §4 (remote words), §5. **Status:** pending. The web (phase 05) is the reference for words and behaviour; read it before building this.

**Context:**
- **`ui-mobile/.../ui/player/`:**
  - `PlayerControlCard.kt` (`PlayerControlCard(player: Player, view, actions, …)` :103; `PlayerCardActions` :53; `PlayerCardView` :72);
  - `PlayerCardTools.kt` (`CardToolsRow` :41; Watch together in it);
  - `PlayerCardTransport.kt` :36.
- **Title pages:** `ui-mobile/.../ui/catalog/TitleDetailScreen.kt` (Play label :102), `SeriesPage.kt` :135.
- **Library:**
  - `ui-mobile/.../ui/LibraryFlow.kt` (`LocalTogetherOffers` :97, the daily-limit notice :66);
  - `LibraryFlowBranches.kt:144` (player frame);
  - `ui-common/.../LibraryPositions.kt` (`FrameKind` :27, payload :228).
- **Home's together row:** `ui-mobile/.../ui/catalog/HomeScreen.kt:111` → `TogetherJoinRow`; `feature/catalog/.../TogetherOffer.kt` (`offerOf` :45).
- **Session and controls:** session-mirroring needs media3 `SimpleBasePlayer` (1.10.1). The title backdrop is drawn as on `TitleDetailScreen.kt:95` (`set.backdropPath ?: set.posterPath`). The speeds are the ones the player's speed menu offers.

### Task 8.1: Asking the TV, from a view model

**Files:**
- Create: `feature/player/src/main/kotlin/CastSenderViewModel.kt`
- Test: `feature/player/src/test/kotlin/CastSenderViewModelTest.kt`

**Interfaces — Consumes:** `TogetherLive.offer`, `replace`, `cancel`, `available`, `live`; `CastWords`, `busyQuestion`, `refusedLine`; `labelOf(context)`.
**Interfaces — Produces:**
- `sealed interface CastAsk { data object Asking; data class Busy(val id: Int, val title: String); data class Told(val text: String); data class On(val id: Int) }`
- `@HiltViewModel class CastSenderViewModel(live: TogetherLive, repository: WatchStateRepository, @ApplicationContext context: Context)`:
  - `val ask: StateFlow<CastAsk?>`;
  - `fun playOnTv(own: Playback)`: offers with `me = Member(UUID, labelOf(context), remote = true)` and `profile = chosenProfileId`;
  - `fun replace()`, `fun cancel()`, `fun dismiss()`.
  - `val castable: StateFlow<String?>`: `null` when Play on TV may be pressed, otherwise the reason: `TOGETHER_UNREACHABLE` while unavailable, and `CastWords.LEAVE_FIRST` while this device's player is in a session. The player passes that in as `inSession: () -> Boolean`.
- Each answer maps to a state:

| answer | state |
|---|---|
| `ON` | `On(id)` |
| `BUSY` | `Busy(id, note)` |
| `REFUSED` | `Told(refusedLine(note))` |
| `NONE` | `Told(CastWords.NO_ANSWER)` |
| thrown | `Told(TOGETHER_UNREACHABLE)` |

- [ ] **Step 1: Write the failing tests:** `onOpensTheRemote`, `busyAsksThenReplaceIsOn`, `busyThenCancelSendsCancel`, `refusedTellsTheReason`, `noAnswerTellsMediagramIsntOpen`, `unreachableIsGreyedWithTheReason`, `inASessionIsGreyedLeaveFirst`.
- [ ] **Step 2: Run** `./gradlew :feature:player:testDebugUnitTest --tests '*CastSender*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): ask the TV to play a title from a phone or tablet`.

### Task 8.2: The remote's model

**Files:**
- Create: `feature/player/src/main/kotlin/together/CastRemotePlayer.kt` and `feature/player/src/main/kotlin/CastRemoteViewModel.kt`
- Test: `feature/player/src/test/kotlin/CastRemoteViewModelTest.kt`

**Interfaces — Consumes:**
- `TogetherLive.join` (as a remote), `act`, `choose`, `end`, `leave` and `live`;
- `expectedPosition`, `changeOf`, `endingOf`, `sayOnRemote` and `CastWords`;
- `CatalogRepository.mediaSet`, and the episode run `episodeListFlow` builds for the player (`PlayerViewModel.kt:122`), so that Next episode is `nextInQueue(run, set)`.

**Interfaces — Produces:**
- `internal class CastRemotePlayer(looper: Looper) : SimpleBasePlayer`. Its state is set by `show(session: Session, durationMs: Long, now: Long)`:
  - `playWhenReady` from `playing`;
  - `contentPositionMs` as a `PositionSupplier` over `expectedPosition`;
  - `playbackParameters` from `rate`;
  - `STATE_READY`.
- Its commands go out through the `onMove: (PlaybackChange) -> Unit` it is given:
  - `handleSetPlayWhenReady` → `{playing}`;
  - `handleSeek` → `{pos}`;
  - `handleSetPlaybackParameters` → `{rate}`;
  - each returns an immediate future.
- `data class RemoteUi(val id: Int, val title: String, val backdrop: String?, val label: String, val notice: String?, val tracks: CastTracks, val audio: String, val text: String, val hasNext: Boolean, val over: String?)`
- `@HiltViewModel class CastRemoteViewModel(...)` with:
  - `val ui: StateFlow<RemoteUi?>` and `val player: Player`;
  - `fun open(id: Int)`, which joins with `remote = true`, and `fun close()`, which leaves;
  - `fun next()`, `fun chooseAudio(id: String)`, `fun chooseText(id: String)` and `fun stop()`.
- Drawing the state:
  - `label` is `CastWords.PLAYING_ON_TV`;
  - `notice` is `sayOnRemote(...)`, held for 2 s;
  - `over` is `CastWords.FINISHED` / `CastWords.STOPPED` from `endingOf`, or `STOPPED` when the session is gone. The UI shows it for 2 s, then pops.

- [ ] **Step 1: Write the failing tests.**
  - `openingJoinsAsARemote`.
  - `pauseSendsPlayingFalse`.
  - `seekSendsThePosition`.
  - `positionFollowsTheSessionClock`: `currentPosition` reads `expectedPosition`.
  - `germanSubtitlesSendTextDe`.
  - `stopEndsAndCloses`.
  - `aTvPauseSaysPausedOnTv`.
  - `theTvLeavingSaysStopped`.
  - `nextEpisodeSendsTheNextSet`.
  - `noNextHidesNextEpisode`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): a remote that drives the TV's session through the player controls`.

### Task 8.3: The screens

**Files:**
- Modify:
  - `ui-common/.../LibraryPositions.kt`: add `FrameKind.REMOTE` (payload: the session id); `openRemote(id: Int)`; and `replaceWithRemote(id: Int)`, which swaps the top player frame for the remote.
  - `ui-mobile/.../LibraryFlowBranches.kt`: the `REMOTE` branch → `CastRemoteScreen`.
  - `PlayerControlCard.kt`:
    - `PlayerCardActions` gains `onStop: (() -> Unit)? = null` and `onPlayOnTv: (() -> Unit)? = null`;
    - `PlayerCardView` gains `castReason: String? = null`.
  - `PlayerCardTools.kt`: a **Play on TV** button right after Watch together. It is greyed, with `castReason` as its description, when that is non-null.
  - `PlayerCardTransport.kt`: **Stop** where PiP's button sits, when `onStop` is non-null.
  - `TitleDetailScreen.kt` and `SeriesPage.kt`: a **Play on TV** pill after Play/Resume, casting what that pill would start at its resume point (`ResumePoint.resumeAt`). It is greyed with the reason.
  - `ui-mobile/.../ui/player/PlayerScreen.kt`: Play on TV asks with the player's own playback. On `On(id)`, it calls `at.replaceWithRemote(id)`, and the player's `stop()` runs as the frame leaves.
- Create:
  - `ui-mobile/.../ui/player/CastRemoteScreen.kt`: the backdrop dimmed where the video would be; the title and `RemoteUi.label` in the top bar; the notice pill; and `PlayerControlCard(player = remote.player, …)`. The card has `onEnterPip = null` and no stats, framing or report, with `onStop` set. Its subtitles and audio menus are built from `tracks` (Off + text; audio).
  - `ui-mobile/.../ui/catalog/CastAskDialog.kt`: Asking / the Busy question with Replace and Cancel / Told with OK.
- Test: `ui-mobile/src/test/kotlin/ui/player/CastRemoteScreenTest.kt`, `ui-mobile/src/test/kotlin/ui/catalog/PlayOnTvButtonTest.kt` (Robolectric Compose, as the existing `ui-mobile` tests do)

- [ ] **Step 1: Write the failing tests.**
  - `playOnTvSitsBesidePlay`.
  - `playOnTvIsGreyedWithTheReason`.
  - `theRemoteShowsPlayingOnTvAndStop`.
  - `theRemoteHasNoPictureInPicture`.
  - `theBusyQuestionOffersReplaceAndCancel`.
- [ ] **Step 2: Run** `./gradlew :ui-mobile:testDebugUnitTest --tests '*Cast*' --tests '*PlayOnTv*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): Play on TV on phone and tablet, and the remote screen`.

### Task 8.4: The bar

**Files:**
- Modify:
  - `feature/catalog/.../TogetherOffer.kt`: a `cast: Boolean` on `TogetherOffer`, true when `session.cast?.state == ON`. `offerOf` still hides titles not on this profile.
  - `HomeScreen.kt:111`: `TogetherJoinRow` only for `!cast`.
  - `ui-mobile/.../ui/LibraryFlow.kt`: a `CastBar` at the bottom, above the daily-limit notice's place, while an offer has `cast` and no `REMOTE` frame is on top.
- Create: `ui-mobile/.../ui/CastBar.kt`. It shows `barLine(title)` and a play/pause button, which moves through `CastRemoteViewModel` without opening it. Tapping opens `at.openRemote(id)`.
- Test: `ui-mobile/src/test/kotlin/ui/CastBarTest.kt`

- [ ] **Step 1: Write the failing tests:** `aCastShowsTheBar`, `anOfferShowsNoBar`, `aHiddenTitleShowsNoBar`, `homeShowsNoWatchingTogetherRowForACast`, `tappingTheBarOpensTheRemote`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(android): a Playing on TV bar while a cast plays`.

**Success criteria:** `./gradlew testDebugUnitTest detekt lint` passes. Side by side with the web preview, the remote and the bar read the same words and offer the same controls.
