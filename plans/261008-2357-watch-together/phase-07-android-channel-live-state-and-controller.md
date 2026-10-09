# Phase 07 — Android: channel, live state, and the player's controller

**Spec:** `../spec.md` §1, §2, §5. **Status:** pending. **Depends on:** phases 05 and 06.

## Context

These facts come from mapping `android/`:

- **The follower goes in `feature/player`, not a new `feature/together`.** `settings.gradle.kts` forbids feature-to-feature dependencies, and the follower has to drive `PlayerHandle`, which lives in `feature/player`. The spec's §4 named a new module; this is flagged in the plan's review.
- **Shared, non-player state goes in `core/data`.** That covers the live session, availability and the pending Join, so the catalog screens (Home, title pages) can read it without touching the player.
- **Reaching the player.**
  - `PlayerHandle` (`feature/player/.../PlayerHandle.kt`) has `play()`, `pause()`, `positionMs()`, `setPlaybackSpeed()` and `player: StateFlow<Player?>`. It has no `seekTo`, so seek through `handle.player.value?.seekTo(ms)`.
  - Its single `Listener` slot is taken by `PlayerViewModel`.
  - Every `Player` call must run on main; `viewModelScope` is `Main.immediate`.
- **There is no single path for the viewer's commands.** The UI and the MediaSession both reach the media3 `Player` through `PlayerViewModel.player` (`PlayerViewModel.kt:60`) and `PlaybackService.kt:46`. The handle's own `play()`/`pause()` use the raw player.
  - So wrap what the UI and the MediaSession receive in a media3 `ForwardingPlayer`, `ViewerPlayer`. Every play, pause or seek through it is the viewer's.
  - The controller's own applied steps go through the raw handle and are never reported.
  - Speed already has a single viewer path: `PlayerViewModel.setSpeed` (`PlayerViewModelDelegates.kt:37`).
  - Restart goes through the raw player (`PlayerViewModelRun.kt:31-35`) and needs its own hook.
- **Opening another title** must go through the navigation path: `UpNextSwitcher.request(id, run, gate, runtimeForGate)` (`UpNextSwitcher.kt:37`), then `PlayerNavigationEffects`, then `PlayerViewModel.open(setId, run, …)`. Never call `handle.open` directly (`UpNextSwitcher.kt` doc).
- **Push events.** `LibraryEvents.events()` is shared (`SharedLibraryEvents`, `shareIn`), so a second collector is fine. Events arrive on `Dispatchers.Default`; hop to main.
- **Form factor:** `isTelevision(context)` (`core/data/.../Television.kt:13`). Tablet means `smallestScreenWidthDp >= 600`, the rule `BackdropWidth.kt:27-28` uses. Make it a public `isTablet(context)` beside `isTelevision`.
- **Durations:** `CatalogRepository.mediaSet(setId)?.durationSecs` (`CatalogRepository.kt:61`).
- **Kids gate:** `forKidsProfile(listOf(set), snapshot.kidsMarks, profile.kidsLimit).isNotEmpty()` (`core/model/.../AgeRating.kt:50`). The profile comes from `WatchStateRepository.chosenProfile`, and the marks from `.snapshot`.
- **Tests:** `FakePlayerHandle` and `TestMainDispatcher` are in `feature/player/src/test/kotlin/`, and `FakeCore`/`FakeCoreProvider` are in `core/testing`.

## Files

- Create: `android/core/data/src/main/kotlin/TogetherChannel.kt`: the interface and `CoreTogetherChannel`.
- Create: `android/core/data/src/main/kotlin/TogetherLive.kt`: the newest live session, availability and the pending Join.
- Modify: `android/core/data/src/main/kotlin/di/DataModule.kt`: provide both.
- Modify: `android/core/data/src/main/kotlin/Television.kt`: add `isTablet(context)`.
- Create: `android/feature/player/src/main/kotlin/together/ViewerPlayer.kt`
- Create: `android/feature/player/src/main/kotlin/together/ViewerRelay.kt`
- Create: `android/feature/player/src/main/kotlin/together/TogetherController.kt`
- Create: `android/feature/player/src/main/kotlin/together/TogetherUi.kt`
- Modify:
  - `PlayerViewModel.kt`: construct the controller, expose the wrapped `player`, `together.ui`, and `onCleared` leave;
  - `PlayerViewModelOpen.kt`: `together.open(setId)`;
  - `PlayerViewModelDelegates.kt`: `setSpeed` reports;
  - `PlayerViewModelRun.kt`: `restart` reports;
  - the PiP-dismissal path (`PlayerViewModel.kt:168`): leave;
  - `PlaybackService.kt:46`: wrap the session's player.
- Create: `android/core/testing/src/main/kotlin/testing/FakeTogetherLive.kt`, with settable `live`/`available`, a `refreshCount`, and a `joinOnOpen`/`takeJoin` pair (used by phases 07 and 08).
- Create: `android/core/data/src/test/kotlin/TogetherLiveTest.kt` and `android/feature/player/src/test/kotlin/together/TogetherControllerTest.kt`

## Interfaces

- **Consumes** from phase 05: `TogetherMessage`, `TogetherEdit`, `LibraryEvent.TOGETHER` and the six `CoreInterface` methods. From phase 06: everything in `model.together`.
- **Produces** for phase 08:

```kotlin
// core/data
data class LiveSession(val id: Int, val session: Session)
interface TogetherLive {
    val live: StateFlow<LiveSession?>
    val available: StateFlow<Boolean>
    suspend fun refresh()
    fun joinOnOpen(id: Int)
    fun takeJoin(): Int?
}
// feature/player
enum class TogetherButton { START, JOIN, LEAVE }
data class TogetherUi(val button: TogetherButton = TogetherButton.START, val enabled: Boolean = false, val reason: String? = null, val label: String? = null, val notice: String? = null)
// PlayerViewModel gains: val together: StateFlow<TogetherUi>; fun onTogetherButton(); fun leaveTogether()
```

## Tasks

### Task 7.1: Channel and live state (`core/data`)

- [ ] **Write failing tests**, `TogetherLiveTest.kt`, against `FakeCore` (phase 05):
  - `refresh()` with one live and one stale message in `togetherMessages` (stale = paused with `at` 4 h ago): `live` is the live one, the stale one is deleted, and `available` is true.
  - `refresh()` when `togetherRecent` throws: `available` is false, and `live` is kept.
  - A heard message that `parseSession` rejects changes nothing. A heard session with `ended: true` and the live id clears `live`.
  - `takeJoin()` returns the id from `joinOnOpen` once.
- [ ] **Implement `TogetherChannel.kt`:**

```kotlin
package data

/**
 * Watch-together session messages through the core. Only I/O: the rules are
 * in `model.together`. Every call needs the chosen library, and fails when
 * there is none.
 */
interface TogetherChannel {
    suspend fun recent(): List<TogetherMessage>
    suspend fun read(id: Int): String?
    suspend fun send(text: String): Int
    suspend fun edit(id: Int, text: String): TogetherEdit
    suspend fun delete(id: Int)
    /** Every together message the listener hears, as it is heard. */
    fun heard(): Flow<TogetherMessage>
}

class CoreTogetherChannel(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val events: LibraryEvents,
) : TogetherChannel {
    private suspend fun <T> library(call: suspend CoreInterface.(String) -> T): T {
        val handle = settings.read() ?: error("no library is chosen")
        return coreProvider.awaitCore().call(handle)
    }

    override suspend fun recent() = library { togetherRecent(it) }
    override suspend fun read(id: Int) = library { togetherRead(it, id) }
    override suspend fun send(text: String) = library { togetherSend(it, text) }
    override suspend fun edit(id: Int, text: String) = library { togetherEdit(it, id, text) }
    override suspend fun delete(id: Int) = library { togetherDelete(it, id) }
    override fun heard(): Flow<TogetherMessage> =
        events.events().filter { it == LibraryEvent.TOGETHER }.mapNotNull { coreProvider.coreOrNull()?.togetherHeard() }
}
```

  Use the module's real package and import style. `CoreProvider`, `LibrarySettings` and `LibraryEvents` are in the same module.
- [ ] **Implement `TogetherLive.kt`:**

```kotlin
/**
 * The newest live watch-together session, for the screens that offer Join:
 * Home, the title pages and the player. A stale session found while looking
 * is deleted, as the spec asks of whoever reads one next.
 */
class DefaultTogetherLive(
    private val channel: TogetherChannel,
    private val catalog: CatalogRepository,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : TogetherLive {
    private val _live = MutableStateFlow<LiveSession?>(null)
    override val live: StateFlow<LiveSession?> = _live.asStateFlow()
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()
    private var joinNext: Int? = null

    init {
        scope.launch {
            channel.heard().collect { message ->
                val session = parseSession(message.text) ?: return@collect
                _live.value = when {
                    !session.ended -> LiveSession(message.messageId, session)
                    _live.value?.id == message.messageId -> null
                    else -> _live.value
                }
            }
        }
    }

    override suspend fun refresh() {
        val found = runCatching { channel.recent() }.getOrElse {
            _available.value = false
            return
        }
        _available.value = true
        var newest: LiveSession? = null
        for (message in found) {
            val session = parseSession(message.text) ?: continue
            val duration = catalog.mediaSet(session.set)?.durationSecs?.toDouble()
            if (!isLive(session, duration, now())) {
                runCatching { channel.delete(message.messageId) }
                continue
            }
            if (newest == null) newest = LiveSession(message.messageId, session)
        }
        _live.value = newest
    }

    override fun joinOnOpen(id: Int) {
        joinNext = id
    }

    override fun takeJoin(): Int? = joinNext.also { joinNext = null }
}
```

- [ ] **Provide both in `DataModule`** as `@Singleton`. Give `DefaultTogetherLive` the `@AppScope` scope that `SharedLibraryEvents` uses (`DataModule.kt:127-137`).
- [ ] **Add `isTablet(context)`** to `Television.kt`: `context.resources.configuration.smallestScreenWidthDp >= 600`. Use it in `BackdropWidth.kt` too, so the two can't drift.
- [ ] **Run:** `./gradlew :core:data:testDebugUnitTest`. Expected: PASS.
- [ ] **Commit:** `feat(android): watch-together channel and live state`

### Task 7.2: The viewer's player and the controller (`feature/player`)

- [ ] **Write failing tests**, `TogetherControllerTest.kt`. Use `FakePlayerHandle` with a mocked media3 `Player` installed (`installPlayer`, as the existing tests do with mockk), a `FakeCore`-backed `CoreTogetherChannel`, a `FakeTogetherLive`, `MainDispatcherRule`, and a fixed clock:
  1. **Start.** `onButton()` with no live session sends a message, `writeSession(started(…))` with label `"Tablet"` from the injected label, and `ui.button` becomes `LEAVE`.
  2. **Follow a pause.** A heard edit from another screen with `playing:false` calls `handle.pause()`. `ui.notice` reads "Paused by TV" and is `null` again after 2 s of virtual time.
  3. **Ignore the echo.** A heard edit with `by` = this screen, while `current` is still its own write, causes no player call.
  4. **Report the viewer.** `viewer(PlaybackChange(playing = true))` edits the message once. Three viewer seeks within 300 ms edit once, with the last position.
  5. **Drift check.** 2 s later, with `fakePositionMs` 600 ms behind the expected position, it calls `player.seekTo`. With `playbackState == STATE_BUFFERING` it doesn't.
  6. **Moving to another title.**
     - `open("ep-2")` while in a session, not asked for by following, edits `set = ep-2, pos 0, playing`.
     - A heard edit with `set = ep-2` calls the injected `openTitle("ep-2")`, and the following `open("ep-2")` sends nothing.
  7. **Kids.** A heard edit moving to a set `allowed` rejects makes the controller leave, with the notice "Left watch together: not for this profile".
  8. **Flood wait.** `togetherEdit` returning `Wait(5)` delays 5 s and resends the newest text. If another screen's edit is heard during the wait, nothing is resent.
  9. **The end.** `togetherEdit` returning `Gone`, or a heard `ended: true`, ends the session with the notice "Watch together ended", and playback goes on.
  10. **The last screen.** `leave()` as the last member edits `ended: true`, then deletes the message.
  11. **The end of the last title.** `onTitleEnded(hasNext = false)` edits `finished(…)` and deletes, even with other members present. With `hasNext = true` it does nothing: Up next's open moves the session.
  12. **The re-read.** Every 30 s in a session, `togetherRead(id)` is called. A changed text is followed, a `null` ends the session, and a throw is ignored until the next tick.
- [ ] **Implement `ViewerPlayer.kt`:**

```kotlin
package player.together

/**
 * The player the viewer's controls and the media session hold. Every play,
 * pause and seek through it is the viewer's own, and is reported after it is
 * applied. The controller applies what it follows through the raw handle,
 * which never reports, so following can never echo back out.
 */
class ViewerPlayer(player: Player, private val report: (PlaybackChange) -> Unit) : ForwardingPlayer(player) {
    override fun play() { super.play(); report(PlaybackChange(playing = true)) }
    override fun pause() { super.pause(); report(PlaybackChange(playing = false)) }
    override fun setPlayWhenReady(playWhenReady: Boolean) { super.setPlayWhenReady(playWhenReady); report(PlaybackChange(playing = playWhenReady)) }
    override fun seekTo(positionMs: Long) { super.seekTo(positionMs); report(PlaybackChange(pos = positionMs / 1000.0)) }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) { super.seekTo(mediaItemIndex, positionMs); report(PlaybackChange(pos = positionMs / 1000.0)) }
    override fun seekBack() { super.seekBack(); report(PlaybackChange(pos = currentPosition / 1000.0)) }
    override fun seekForward() { super.seekForward(); report(PlaybackChange(pos = currentPosition / 1000.0)) }
}
```

  Check the media3 version in `libs.versions.toml`. On ≥ 1.1, `ForwardingPlayer` routes the media3 UI's `seekBack`/`seekForward` and `Util.handlePlayPauseButtonAction` through these overrides. Add a test that `handlePlayPauseButtonAction(viewerPlayer)` reports.
- [ ] **Implement `ViewerRelay.kt`.** A `@Singleton class ViewerRelay @Inject constructor() { var listener: ((PlaybackChange) -> Unit)? = null; fun report(change: PlaybackChange) { listener?.invoke(change) } }`. It is the bridge between the one handle's viewer surfaces and whichever controller owns the current player.
- [ ] **Implement `TogetherController.kt`.** This is the Kotlin twin of `web/public/lib/playback/together.js` and must stay under 200 lines. It holds:
  - the state `setId`, `id`, `current`, `expecting`, a pending `outgoing: Pair<Int, String>?` and the sender `Job`;
  - `_ui: MutableStateFlow<TogetherUi>`.

  Its dependencies: `channel: TogetherChannel`, `live: TogetherLive`, `handle: PlayerHandle`, `openTitle: (String) -> Unit`, `allowed: suspend (String) -> Boolean`, `label: String`, `scope: CoroutineScope`, `now: () -> Long`.

  ```kotlin
  private fun own(): ScreenState? {
      val player = handle.player.value ?: return null
      val rate = (player.playbackParameters.speed * 100).roundToInt() / 100.0
      return ScreenState(setId, player.playWhenReady, (handle.positionMs() ?: 0L) / 1000.0, rate)
  }

  private suspend fun apply() {
      val session = current ?: return
      val player = handle.player.value ?: return
      if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) return // nobody waits for a buffering screen
      val state = own() ?: return
      for (step in decide(state, session, now())) when (step) {
          is Step.Open -> { if (!allowed(step.set)) return leave("Left watch together: not for this profile"); expecting = step.set; openTitle(step.set); return }
          is Step.Rate -> handle.setPlaybackSpeed(step.rate.toFloat())
          is Step.Seek -> player.seekTo((step.pos * 1000).toLong())
          Step.Play -> handle.play()
          Step.Pause -> handle.pause()
      }
  }
  ```

  The other functions mirror `together.js` one for one:
  - `heard`: drop the queued write if `by != screen`, then `follow`, `changeOf` → notice, `refreshUi` and `apply`;
  - `start`: if a live session exists, join it instead;
  - `joinLive` and `leave`;
  - `viewer(change)`: a position-only change (a seek) settles for 300 ms; anything else is sent at once;
  - `onTitleEnded(hasNext)`: with no next title, `finished` plus delete;
  - a 30 s re-read loop while in a session, using `channel.read(id)`;
  - `open(setId)`, which also consumes `live.takeJoin()`;
  - the 2 s drift loop and the 2 s notice clear.

  The sender keeps only the newest text, like `hub.ts`'s `drain`. On `Wait(s)` it re-queues the text unless something newer or foreign replaced it, then `delay(s * 1000)`. On `Gone` it ends with "Watch together ended". The notice words are the web's (`together.js` `say`).
- [ ] **Wire the ViewModel:**
  - In `PlayerViewModel`, inject `TogetherChannel`, `TogetherLive`, `ViewerRelay`, `UpNextSwitcher` (already there), `WatchStateRepository` and `CatalogRepository`.
  - Build `val togetherController = TogetherController(..., openTitle = { id -> switcher.request(id, run = currentRun.takeIf { id in it } ?: emptyList(), gate = false, runtimeForGate = null) }, label = if (isTelevision(app)) "TV" else if (isTablet(app)) "Tablet" else "Phone", scope = viewModelScope)`.
  - Set `relay.listener = { togetherController.viewer(it) }` in `init`, and clear it in `onCleared()` after `togetherController.leave()`.
  - `val player = handle.player.map { it?.let { p -> ViewerPlayer(p, relay::report) } }.stateIn(viewModelScope, SharingStarted.Eagerly, null)`.
  - `val together = togetherController.ui`, `fun onTogetherButton() = togetherController.onButton()`, `fun leaveTogether() = togetherController.leave()`.
  - `PlayerViewModelOpen.open`: `togetherController.open(setId)` after the title state is installed.
  - `PlayerViewModelDelegates.setSpeed`: `choicesController.setSpeed(rate).also { togetherController.viewer(PlaybackChange(rate = rate.toDouble())) }`.
  - The ViewModel's `onEnded()` (the `PlayerHandle.Listener` callback): `togetherController.onTitleEnded(hasNext = <Up next has a next title>)`, using the same next the countdown uses.
  - `PlayerViewModelRun.restart`: after `player.seekTo(0L)`, `togetherController.viewer(PlaybackChange(pos = 0.0))`.
  - `pauseForPipDismissal`: `togetherController.leave()` first. Dismissing PiP stops this screen, and it must not pull the session with it.
- [ ] **`PlaybackService.kt:46`:** `MediaSession.Builder(this, ViewerPlayer(player, relay::report))`, with `relay` injected. Headset and lock-screen play/pause then count as the viewer's.
- [ ] **Run:** `./gradlew :feature:player:testDebugUnitTest :core:data:testDebugUnitTest lint`. Expected: PASS, and every existing player test still green.
- [ ] **Commit:** `feat(android): follow a watch-together session in the player`

## Todo

- [ ] 7.1 Channel and live state
- [ ] 7.2 Viewer player, controller and wiring

## Success criteria

- The controller tests cover every row of the spec's §5 failure table.
- The existing player tests are unchanged and green.
- No `Player` call happens off main.

## Risks

- **A media3 path that bypasses the overrides** (for example `seekToDefaultPosition` at the end of a title) goes unreported. The drift check then pulls this screen back to the session. That is the safe failure.
- **The `ForwardingPlayer` wrapper changes the object the UI holds.** Run the TV and phone player UI tests, and the media session, on both devices in phase 09.

## Security

The session JSON comes from the channel: it is the household's own account, but the data is still untrusted. `parseSession` rejects anything malformed. An `Open` step is checked against the kids gate. A set that isn't in the catalog fails to open, as Up next's own does, and leaves the session.

## Pre-flight rulings (binding; they override the text above where they differ)

- **Opening another title:** `UpNextSwitcher` is private inside `UpNextController` (`UpNextController.kt` ~32), and `PlayerViewModel` has no `switcher`/`currentRun`. Add `UpNextController.requestTitle(id)` → `switcher.request(id, run.takeIf { id in it } ?: emptyList(), gate = false, runtimeForGate = null)` (match the real parameter names). Wire `openTitle = upNextController::requestTitle`, and add `UpNextController.kt` to the files.
- **Leaving must survive the ViewModel:** `onCleared()` runs after `viewModelScope` is cancelled. The controller's `leave`/`end` writes run on the existing `@AppScope` `CoroutineScope` (`core/data/src/main/kotlin/di/AppScope.kt`), never `viewModelScope`. Add a test: the VM is cleared → the leave edit is sent, and the last screen ends and deletes.
- **Discovery keeps pushed sessions (spec §1):** `TogetherLive.refresh()` returns the newest live session from the 100-message read. If the session it last heard (by push or read) is missing from that read, it reads it by id and keeps it while it's live, so a busy channel can't let a second session start.
- **Flood wait follows the spec (§5):** the sender keeps only the latest own write. When the wait ends it sends it re-based on the newest known session: playback fields `set, playing, pos, rate, at, by` from the own write; `session, members, ended` from the newest known. If that one is ended or gone, it drops the write. A foreign edit during the wait doesn't cancel the own write. The web hub does the same, so keep the two identical in behaviour. Controller test 9 (flood) asserts it.
- **No process-lifetime update stream:** don't collect `heard()` forever on `@AppScope`. `LibraryEvents.kt` (~26-28, ~108-112) documents that the Telegram update stream is open only while someone subscribes. Expose together updates as a flow collected by the player controller's and `CatalogViewModel`'s own scopes (or `stateIn(WhileSubscribed)`), so the stream closes when neither is on screen.
- **File sizes:** pre-split the sender into `TogetherSender.kt` (the twin of the web hub's `write`/`drain`), and keep the `PlayerViewModel` wiring in a `PlayerViewModelTogether.kt` extension or helper file. `PlayerViewModel.kt` is already at its limit.
- Inject `@ApplicationContext` for `isTelevision`/`isTablet`. Cite `docs/mlib-spec.md` §7b in comments, not "the spec".
