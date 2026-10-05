# Phase 03: Android shared player model

## Context links

- [spec.md](spec.md) — Behaviour (Transport, Tools, Episode sidebar, Hiding), Keys and gestures, Testing; the User decisions table is binding.
- [plan.md](plan.md) — Global Constraints, Review Focus 1, 4, 5 (owned here for Android).
- Web reference for the CC rule: `web/public/lib/playback/subtitle-picker.js:106-113` (`toggle()`: `last` in memory per title, else `toggleOn`).
- Catalogue grouping this mirrors (cannot import — feature modules do not import each other): `android/feature/catalog/src/main/kotlin/Shelves.kt:140` (`trailOf`).
- Memory: `bump-versions-by-pattern` (the lead bumps, not this phase).

## Overview

- **Priority:** P1 — phases 04 and 05 consume this phase's types verbatim.
- **Status:** pending.
- **Depends on:** nothing (runs beside the web phases 01–02). Blocks 04 and 05.
- **What:** everything both Android cards need that is not a composable: the episode-list model, Previous / a picked row / Restart in `PlayerViewModel`, `hasPrevious`/`inRun` in the up-next state, the 15 s skip, the "menu or sidebar open" hold on auto-hide, the CC rule pinned in the card's terms, and the shared card fill, menu placement and ⏮ icon in `ui-common`.

## Key insights

- **The run is already the play order.** `runFor` (`feature/catalog/src/main/kotlin/RunFor.kt:17`) hands the player the collection flattened by `playOrder`; an explicit run (a list, the Kids wall) wins over it (`ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt:142`, `ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt:103`). So the episode list keeps run order and only *groups*; it never re-sorts.
- **Grouping mirrors the catalogue's `trailOf`** (`Shelves.kt:140`): `path` folders, else `chapter`, else an episode's "Season N", else a lesson's "Chapter N". The one deliberate change: an episode with no season, and an id the catalogue does not know, go to one section placed **last** ("Other", or "Episodes" when nothing else exists), per Review Focus 5.
- **One catalogue read, not one per row.** `CatalogRepository.sets()` (`core/data/src/main/kotlin/CatalogRepository.kt:35`) once per run change: a course runs to 162 lessons, and `mediaSet(id)` is one core crossing each (`CatalogRepository.kt:162`). Re-read only when a run holds an id the last listing lacked.
- **Previous and a picked row take Next's switch.** `UpNextController.switchTo` (`UpNextController.kt:179`) saves, then asks the UI to move via `pendingSwitch`; `PlayerNavigationEffects` (`ui-common/src/main/kotlin/ui/player/PlayerLifecycle.kt:93-98`) performs it on both surfaces. The TV's own `previousInRun` + direct `onSwitch` (`ui-tv/.../TvPlayerRun.kt:13-19,61-66`) becomes redundant; phase 05 deletes it.
- **A seek back off the end must clear "ended".** Today `ended` (`UpNextController.kt:42`) is cleared only by a new title or a switch; `onSeeked` (`:107`) re-evaluates with it still set, so Restart (or a scrub back) after the credits keeps the countdown running and switches titles under a viewer watching again. Fix at the root, in `onSeeked`: clear it unless the playhead is still on the end. Unknown position/length leaves it set (today's behaviour).
- **Skip clamping is media3's on the phone, the TV's own on the D-pad.** The phone's −15/+15, double tap and PiP buttons call `seekBack()`/`seekForward()`, which `BasePlayer.seekToOffset` clamps to `[0, duration]`; the TV D-pad uses `TvPlayerRemote.seekBy` (`ui-tv/.../TvPlayerRemote.kt:167-175`), which clamps the same way. This phase only moves the distance (`SKIP_MS`, made public so the TV derives `SKIP_SECONDS` from it) and owns the *after* of the edge: the ended/up-next path.
- **CC already matches the spec — and the web.** `SubtitleChoiceController.toggle` (`:126`) turns on `toggleOn(last, preferred, audio, tracks)` (`SubtitleChoice.kt:67`): the last language chosen for the title this session, else the profile language, else the audio language, else the first track — exactly `subtitle-picker.js:106-113`. No logic change; Task 8 pins it in the card's terms. "Disabled without tracks" reads `PlayerChoices.ccVisible` (`PlayerChoices.kt:49`).
- **`PlayerViewModel.kt` is 198 lines and `UpNextController.kt` 199.** New VM API goes to a new `PlayerViewModelRun.kt` (the codebase's split idiom, cf. `PlayerViewModelDelegates.kt`); `UpNextController` stays ~200 by moving its playhead readings to `UpNextPlayhead.kt` and inlining the one-use `ensureTicking`.
- **No two modules may share a file facade.** `ui-common` and `ui-mobile` both use package `ui.player`; a top-level-function file of the same name in both would be a duplicate class in `:app`. The shared card file is `PlayerCardSurface.kt`; phase 04 must not reuse that name.
- **Interim on `main`:** after this phase the phone and the TV's on-screen skips say 15 (they read the player's increments) while the TV D-pad still moves 10 until phase 05 lands. Accepted: 04 and 05 follow directly.

## Requirements

Functional:
- `episodeListOf(openId, run, sets, watch)` builds sections → rows (id, number, title, runtime, watched, progress, current), one row per distinct id (both surfaces key rows by id); `null` for an empty run, and for an open title the catalogue knows to have no `show` (a film played from a hand-picked list keeps ⏮/⏭ but has no list).
- `PlayerViewModel.episodes` stays current as the run, the open title, the catalogue and the watch snapshot move; null after `stop()`.
- `previous()` opens the title before the open one through Next's switch; nothing on the first or with no run. Never a restart.
- `playFromRun(setId)` opens a picked row through the same switch; the open row or an id outside the run does nothing.
- `restart()` seeks to 0:00 and leaves play/pause alone.
- `UpNextUiState.hasPrevious`, `.run`, `.inRun` (⏮/⏭ hidden when `!inRun`, disabled per `hasPrevious`/`hasNext`).
- `SKIP_MS = 15_000L`, public.
- `controlsShouldFade(..., menuOrSidebarOpen)` keeps the card up while a menu or the sidebar is open.
- Shared look in `ui-common`: `Modifier.playerCard()`, `CARD_FILL_ALPHA`, `cardMenuOffset(...)`, `TransportIcons.Previous`.

Non-functional: no new dependencies; no composables in `feature/player`; files ≤ ~200 lines; comments explain why and never cite plans.

## Related code files

- **Create:**
  - `android/feature/player/src/main/kotlin/EpisodeList.kt`
  - `android/feature/player/src/main/kotlin/EpisodeListFlow.kt`
  - `android/feature/player/src/main/kotlin/PlayerViewModelRun.kt`
  - `android/feature/player/src/main/kotlin/UpNextPlayhead.kt`
  - `android/ui-common/src/main/kotlin/ui/player/PlayerCardSurface.kt`
  - tests: `feature/player/src/test/kotlin/{UpNextRunStepsTest,UpNextSeekEdgesTest,EpisodeListTest,PlayerEpisodesWiringTest,PlayerRunStepsTest,SubtitleCardToggleTest}.kt`, `ui-common/src/test/kotlin/ui/player/CardMenuPlacementTest.kt`
- **Modify:**
  - `android/core/playback/src/main/kotlin/PlayerFactory.kt` (:165-170)
  - `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt` (:115-135)
  - `android/feature/player/src/main/kotlin/UpNext.kt` (after :57)
  - `android/feature/player/src/main/kotlin/UpNextState.kt` (:7-21)
  - `android/feature/player/src/main/kotlin/UpNextController.kt` (:96-118, :136-198)
  - `android/feature/player/src/main/kotlin/PlayerViewModel.kt` (after :89)
  - `android/feature/player/src/main/kotlin/ControlsVisibility.kt` (:34-45)
  - `android/feature/player/src/test/kotlin/MediaSetFixtures.kt` (:14-45)
  - `android/feature/player/src/test/kotlin/ControlsVisibilityTest.kt` (append)
  - `android/ui-common/src/main/kotlin/ui/player/TransportIcons.kt` (after :83)
- **Delete:** none (phase 05 deletes the TV's `previousInRun`).

## Success criteria

- `cd android && ./gradlew -q :core:playback:testDebugUnitTest :feature:player:testDebugUnitTest :ui-common:testDebugUnitTest` exits 0.
- `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest` still exits 0 (nothing on the surfaces changed shape).
- Review Focus 1 (after-the-edge half), 4 and 5 each have a named passing test (listed in Tasks 3, 8, 2/4/6).
- `wc -l` of every touched `feature/player` main file ≤ 200.

## Risks

| Risk | L × I | Mitigation |
|---|---|---|
| Clearing `ended` on seek breaks the seek-to-end path (skip lands on the last frame) | M × H | Clear only when the playhead is off the end (`isAtEnd`, 1 s slack); unknown readings leave it set. Task 3 pins both the stays-ended and the lands-on-end-then-switches cases. |
| `episodes` flow recomputes every countdown second | M × L | Built from `upNext.map { it.run }.distinctUntilChanged()`, not the whole up-next state. |
| A run with a permanently unknown id re-reads the catalogue | L × L | Only on a *changed* run (`distinctUntilChanged`), never per tick. |
| Kotlin facade clash `PlayerCardSurfaceKt` | L × H | Unique name, flagged to phase 04 (Key insights). |
| TV D-pad at 10 s while buttons say 15 until 05 merges | H × L | Documented; 05 follows. |

Rollback: every task is its own commit; revert in reverse order. Nothing persists data, so no migration.

## Security considerations

None new: no input crosses a trust boundary; the catalogue and watch state are read only.

## Interfaces this phase produces (phases 04 and 05 consume these verbatim)

```kotlin
// android/core/playback — package playback
const val SKIP_MS = 15_000L

// android/feature/player — package player
data class EpisodeRow(
    val setId: String,
    val number: String,        // episodeLabel(set): "S1E4", "4", "" when unnumbered or unknown
    val title: String,         // set.title, or UNKNOWN_TITLE for an id the catalogue does not know
    val runtimeSecs: Int?,     // set.durationSecs; format with catalog.humanDuration
    val watched: Boolean,
    val progress: Float?,      // 0..1, null when unstarted, finished, or runtime unknown
    val current: Boolean,      // the open title: "Now playing", not actionable
)
data class EpisodeSection(val title: String, val rows: List<EpisodeRow>)
data class EpisodeList(val sections: List<EpisodeSection>, val currentSection: Int)
const val OTHER_SECTION = "Other"
const val EPISODES_SECTION = "Episodes"
const val UNKNOWN_TITLE = "Unknown title"
fun episodeListOf(openId: String, run: List<String>, sets: Map<String, MediaSet>, watch: WatchSnapshot): EpisodeList?
fun previousInQueue(run: List<String>, setId: String): String?

data class UpNextUiState(
    /* existing fields unchanged: phase, titleLine, countdownSecondsLeft, hasNext, awaitingStart */
    val run: List<String> = emptyList(),
    val hasPrevious: Boolean = false,
) { val inRun: Boolean get() = run.isNotEmpty() }

class PlayerViewModel { val episodes: StateFlow<EpisodeList?> /* member */ }
fun PlayerViewModel.previous()
fun PlayerViewModel.playFromRun(setId: String)
fun PlayerViewModel.restart()

fun controlsShouldFade(isPlaying: Boolean, isScrubbing: Boolean, menuOrSidebarOpen: Boolean = false): Boolean

// android/ui-common — package ui.player (file PlayerCardSurface.kt)
const val CARD_FILL_ALPHA = 0.78f
fun Modifier.playerCard(): Modifier
fun cardMenuOffset(anchor: IntRect, card: IntRect, menu: IntSize, gap: Int): IntOffset
// TransportIcons.kt
TransportIcons.Previous: ImageVector
```

Surface rules both cards follow (decided here so 04 and 05 cannot drift):
- ⏮ and ⏭ are shown iff `upNext.inRun`; enabled iff `upNext.hasPrevious` / `upNext.hasNext`. ☰ is shown iff `episodes != null`.
- CC is enabled iff `choices.ccVisible`; ▾ is enabled iff `choices.ccVisible || choices.subtitleStyleVisible`; its menu lists language rows only when `subtitleOptions` is non-empty, then "Style…" when `subtitleStyleVisible`.
- Rows are unique by `setId` (`episodeListOf` drops a repeated id), so `items(key = { it.setId })` / `key(row.setId)` is safe on both surfaces.
- The sidebar opens on `currentSection`; a row with `current` reads "Now playing" and does nothing when picked; picking any other row calls `playFromRun(row.setId)` and closes the sidebar.
- Card, menus and sidebar all draw `Modifier.playerCard()`.

## Tasks

### Task 1: Skip is fifteen seconds

**Files:**
- Modify: `android/core/playback/src/main/kotlin/PlayerFactory.kt` (:165-170)
- Test (rewrite): `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt` (:115-135, `aSkipMovesTenSecondsInEitherDirection`)

**Interfaces:**
- Consumes: `buildPlayer(context, counters, lan, currentCore)` (`PlayerFactory.kt:109`), which sets `setSeekBackIncrementMs(SKIP_MS)`/`setSeekForwardIncrementMs(SKIP_MS)` (`:125-126`); `FakeCore` (`core/testing/src/main/kotlin/testing/FakeCore.kt`).
- Produces: `const val SKIP_MS = 15_000L` (package `playback`, public).

- [ ] **Step 1: Write the failing test.** Replace the KDoc and test at `PlayerFactoryTest.kt:115-135` with:

```kotlin
    /**
     * Both directions, explicitly, and the one number every skip reads.
     * media3 defaults to five seconds back and fifteen forward, so a card
     * whose buttons both say fifteen would be telling a viewer something
     * the player does not do going back.
     */
    @Test
    fun aSkipMovesFifteenSecondsInEitherDirection() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val player = buildPlayer(context, PlaybackCounters()) { FakeCore() }

            try {
                assertEquals(15_000L, SKIP_MS)
                assertEquals(SKIP_MS, player.seekBackIncrement)
                assertEquals(SKIP_MS, player.seekForwardIncrement)
            } finally {
                player.release()
            }
        }
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :core:playback:testDebugUnitTest --tests 'playback.PlayerFactoryTest'` — expected FAIL (compile: `SKIP_MS` is private).
- [ ] **Step 3: Minimal implementation.** Replace `PlayerFactory.kt:165-170` with:

```kotlin
/**
 * How far one skip moves, every way a viewer can skip: the card's −15 and
 * +15, the phone's double tap, picture-in-picture's own buttons and the
 * television's D-pad — the web's `SKIP_SECONDS`, so a viewer who uses more
 * than one surface skips the same distance on each. Long enough to clear a
 * missed line and a beat of the scene around it in one press.
 */
const val SKIP_MS = 15_000L
```

- [ ] **Step 4: Run** the Step 2 command — expected PASS.
- [ ] **Step 5: Commit.**
  `git add android/core/playback/src/main/kotlin/PlayerFactory.kt android/core/playback/src/test/kotlin/PlayerFactoryTest.kt`
  `git commit -m "feat(playback): skip fifteen seconds each way"`

### Task 2: Previous and a picked row in the run

**Files:**
- Modify: `android/feature/player/src/main/kotlin/UpNext.kt` (append after `nextInQueue`, :48-57)
- Modify: `android/feature/player/src/main/kotlin/UpNextState.kt` (:7-21)
- Modify: `android/feature/player/src/main/kotlin/UpNextController.kt` (:96-104, :117-118, :136-164, :171, :179-198)
- Create: `android/feature/player/src/main/kotlin/UpNextPlayhead.kt`
- Test: `android/feature/player/src/test/kotlin/UpNextRunStepsTest.kt`

**Interfaces:**
- Consumes: `nextInQueue` (`UpNext.kt:54`); `UpNextController.switchTo` (`UpNextController.kt:179`), `publish` (`:190`), `evaluate` (`:141`), `remainingSeconds` (`:159`); `UpNextSwitcher.request(id, run, gate, runtimeForGate)` (`UpNextSwitcher.kt:38`); `PlayerSession.openSetId` (`PlayerSession.kt:20`); `buildController(scope, handle, catalogRepository, openSet)` (`UpNextControllerTest.kt:115`).
- Produces:
  - `fun previousInQueue(run: List<String>, setId: String): String?`
  - `UpNextUiState.run: List<String>`, `UpNextUiState.hasPrevious: Boolean`, `UpNextUiState.inRun: Boolean`
  - `fun UpNextController.playFromRun(setId: String)`
  - `internal fun PlayerHandle.remainingSeconds(runtimeSecs: Int?): Double?`

- [ ] **Step 1: Write the failing test.** Create `UpNextRunStepsTest.kt`:

```kotlin
package player

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The run as the card's ⏮ and ⏭ and the episode sidebar walk it: what the
 * up-next state says about either end, and a title picked by hand going
 * through the one switch [UpNextController] already makes for Next.
 */
class UpNextRunStepsTest {

    @Test
    fun previousInQueueIsNextInQueuesMirror() {
        assertEquals("a", previousInQueue(listOf("a", "b", "c"), "b"))
        assertNull(previousInQueue(listOf("a", "b"), "a"))
        assertNull(previousInQueue(listOf("a", "b"), "z"))
        assertNull(previousInQueue(emptyList(), "a"))
    }

    @Test
    fun theFirstTitleOfARunHasANextButNoPrevious() = runTest {
        val (controller, session) = buildController(this)
        session.open("e1")
        controller.startTitle("e1", listOf("e1", "e2", "e3"))
        runCurrent()

        val state = controller.state.value
        assertTrue(state.inRun)
        assertFalse(state.hasPrevious)
        assertTrue(state.hasNext)
        controller.stop()
    }

    @Test
    fun theLastTitleHasAPreviousButNoNext() = runTest {
        val (controller, session) = buildController(this)
        session.open("e3")
        controller.startTitle("e3", listOf("e1", "e2", "e3"))
        runCurrent()

        assertTrue(controller.state.value.hasPrevious)
        assertFalse(controller.state.value.hasNext)
        controller.stop()
    }

    @Test
    fun aFilmIsInNoRunAtAll() = runTest {
        val (controller, session) = buildController(this)
        session.open("film")
        controller.startTitle("film", emptyList())
        runCurrent()

        val state = controller.state.value
        assertFalse(state.inRun)
        assertFalse(state.hasPrevious)
        assertFalse(state.hasNext)
    }

    @Test
    fun aTitlePickedFromTheRunAsksForTheSwitchNextTakes() = runTest {
        val (controller, session) = buildController(this)
        session.open("e2")
        controller.startTitle("e2", listOf("e1", "e2", "e3"))
        runCurrent()

        controller.playFromRun("e1")

        assertEquals(PendingPlayerSwitch("e1", listOf("e1", "e2", "e3")), controller.pendingSwitch.value)
        controller.stop()
    }

    @Test
    fun theOpenTitleOrOneOutsideTheRunIsNoSwitch() = runTest {
        val (controller, session) = buildController(this)
        session.open("e2")
        controller.startTitle("e2", listOf("e1", "e2"))
        runCurrent()

        controller.playFromRun("e2")
        controller.playFromRun("elsewhere")

        assertNull(controller.pendingSwitch.value)
        controller.stop()
    }

    /** The run is ids, and ⏮/⏭ walk ids: one the catalogue has never heard of is still a step. */
    @Test
    fun aRunHoldingAnIdTheCatalogueDoesNotKnowStillSteps() = runTest {
        val (controller, session) = buildController(this) // the fake catalogue knows none of these
        session.open("e1")
        controller.startTitle("e1", listOf("ghost", "e1", "e2"))
        runCurrent()

        assertTrue(controller.state.value.hasPrevious)
        controller.playFromRun("ghost")

        assertEquals("ghost", controller.pendingSwitch.value?.setId)
        controller.stop()
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.UpNextRunStepsTest'` — expected FAIL (compile: `previousInQueue`, `inRun`, `hasPrevious`, `playFromRun` unresolved).
- [ ] **Step 3: Minimal implementation.**

  a. `UpNext.kt` — append after `nextInQueue` (:57):

```kotlin

/**
 * What comes before [setId] in [run], or `null` at its start or for a title
 * the run does not hold — [nextInQueue]'s mirror, for the card's ⏮ and the
 * television's Previous key.
 */
fun previousInQueue(run: List<String>, setId: String): String? {
    val at = run.indexOf(setId)
    return if (at <= 0) null else run[at - 1]
}
```

  b. `UpNextState.kt` — replace `UpNextUiState` (:7-21) with (existing fields and docs unchanged, three members added):

```kotlin
data class UpNextUiState(
    val phase: UpNextPhase = UpNextPhase.HIDDEN,
    /** The next title's own line (`titleLine`); blank until it resolves. */
    val titleLine: String = "",
    /** Seconds left in the countdown; `null` outside [UpNextPhase.COUNTING]. */
    val countdownSecondsLeft: Int? = null,
    /** Whether a next title exists at all — true even once [phase] is hidden by a cancel. */
    val hasNext: Boolean = false,
    /**
     * An unattended switch is under way: the countdown has run out and the
     * player is paused on the next title waiting on the autoplay gate.
     * Kept apart from [phase] — the switch itself changes which title
     * [phase] describes, but the screen must stay awake through both.
     */
    val awaitingStart: Boolean = false,
    /** The run the open title plays in — what ⏮, ⏭ and the episode sidebar walk; empty for a film opened on its own. */
    val run: List<String> = emptyList(),
    /** Whether a title comes before the open one in [run] — ⏮ is disabled on the first. */
    val hasPrevious: Boolean = false,
) {
    /** Whether there is a run at all: without one, ⏮ and ⏭ are hidden rather than disabled. */
    val inRun: Boolean get() = run.isNotEmpty()
}
```

  c. Create `UpNextPlayhead.kt` (moved verbatim from `UpNextController.remainingSeconds`, `:159-164`, as a handle reading):

```kotlin
package player

/*
 * Where the playhead stands against the open title's end, as
 * [UpNextController] reads it — split out of that file to keep it under
 * the project's line guideline.
 */

/**
 * Seconds left in a title [runtimeSecs] long, or `null` for a runtime this
 * device does not know — kept apart from a real zero, which is the one value
 * that would otherwise open the card at once.
 */
internal fun PlayerHandle.remainingSeconds(runtimeSecs: Int?): Double? {
    val runtime = runtimeSecs?.toDouble() ?: return null
    if (runtime <= 0) return null
    val posMs = positionMs() ?: return null
    return runtime - posMs / 1_000.0
}
```

  d. `UpNextController.kt`:
  - In `onPlayingChanged` (:99) replace `ensureTicking()` with `tickerJob?.cancel(); tickerJob = scope.runTicker(::evaluate)`, and delete `ensureTicking` (:136-139) with its trailing blank line.
  - Replace `playNow` (:118) with:

```kotlin
    fun playNow() {
        nextId?.let { switchTo(it, gate = false) }
    }

    /** A title of the run picked by hand — Previous, or a sidebar row — through [playNow]'s own switch; the open title, or one outside the run, is none. */
    fun playFromRun(setId: String) {
        if (setId != session.openSetId && setId in run) switchTo(setId, gate = false)
    }
```

  - In `evaluate` (:150) replace `remainingSeconds = remainingSeconds(),` with `remainingSeconds = handle.remainingSeconds(openSet.value?.durationSecs),` and delete the private `remainingSeconds()` (:159-164) with its trailing blank line.
  - In `startCountdownIfNeeded` (:171) replace `onFinished = { switchTo(gate = true) },` with `onFinished = { nextId?.let { switchTo(it, gate = true) } },`.
  - Replace the head of `switchTo` (:179-181):

```kotlin
    private fun switchTo(id: String, gate: Boolean) {
        stopCountdown()
```

    (the `val id = nextId ?: return` line goes; the rest of the body, including its save comment and `switcher.request(id, run, gate, nextSet?.durationSecs?.toDouble())`, is unchanged — only the countdown's own switch is gated, and it only ever goes to the next title, so `nextSet`'s runtime is the right one whenever it is read.)
  - In `publish` (:190-198) add two arguments after `awaitingStart = switcher.awaitingStart,`:

```kotlin
            run = run,
            hasPrevious = session.openSetId?.let { previousInQueue(run, it) } != null,
```

- [ ] **Step 4: Run** the Step 2 command — expected PASS. Then `cd android && ./gradlew -q :feature:player:testDebugUnitTest` — expected PASS (UpNextControllerTest, UpNextSwitchTest, PlayerViewModelSwitchTest unchanged). `wc -l android/feature/player/src/main/kotlin/UpNextController.kt` ≤ 200 (≈195).
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/main/kotlin/UpNext.kt android/feature/player/src/main/kotlin/UpNextState.kt android/feature/player/src/main/kotlin/UpNextController.kt android/feature/player/src/main/kotlin/UpNextPlayhead.kt android/feature/player/src/test/kotlin/UpNextRunStepsTest.kt`
  `git commit -m "feat(player): previous and picked titles through the up-next switch"`

### Task 3: A seek back off the end stops the countdown

**Files:**
- Modify: `android/feature/player/src/main/kotlin/UpNextController.kt` (`onSeeked`, :106-109)
- Modify: `android/feature/player/src/main/kotlin/UpNextPlayhead.kt` (append)
- Test: `android/feature/player/src/test/kotlin/UpNextSeekEdgesTest.kt`

**Interfaces:**
- Consumes: `UpNextController.onSeeked`/`onEnded`/`ended` (`UpNextController.kt:107,112,42`); `PlayerHandle.positionMs()`/`durationMs()` (`PlayerHandle.kt:45,48`); `FakePlayerHandle.fakePositionMs`/`fakeDurationMs` (`FakePlayerHandle.kt:37-38`); `buildController` (`UpNextControllerTest.kt:115`); `fakeMediaSet` (`MediaSetFixtures.kt:14`).
- Produces: `internal fun PlayerHandle.isAtEnd(): Boolean`.

- [ ] **Step 1: Write the failing test.** Create `UpNextSeekEdgesTest.kt`:

```kotlin
package player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The card's −15, +15 and ↺ at the edges of a title, as the up-next card
 * sees them. The player clamps a skip itself — media3's own `seekBack`/
 * `seekForward` on the phone, `seekBy` on the television — so +15 in the
 * last seconds lands on the end, and media3 then reports the title ended:
 * the ordinary ended path, which must still count down and switch. A seek
 * back off the end is not the end any more, and must not.
 */
class UpNextSeekEdgesTest {

    private val tenMinutes = MutableStateFlow<MediaSet?>(fakeMediaSet("s1", durationSecs = 600))

    @Test
    fun aSkipThatLandsOnTheEndRunsTheOrdinaryCountdownAndSwitch() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 590_000L; fakeDurationMs = 600_000L }
        val (controller, session) = buildController(this, handle, openSet = tenMinutes)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()

        handle.fakePositionMs = 600_000L // +15 from 9:50, clamped to the end
        controller.onSeeked()
        controller.onEnded() // media3 reports the end the clamped seek reached
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals("s2", controller.pendingSwitch.value?.setId)
        controller.stop()
    }

    @Test
    fun aSeekBackOffTheEndStopsTheCountdown() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 600_000L; fakeDurationMs = 600_000L }
        val (controller, session) = buildController(this, handle, openSet = tenMinutes)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        handle.fakePositionMs = 0L // Restart, or a scrub back to the top
        controller.onSeeked()

        assertEquals(UpNextPhase.HIDDEN, controller.state.value.phase)
        advanceTimeBy(11_000)
        runCurrent()
        assertNull(controller.pendingSwitch.value, "a viewer watching again from the top is not switched away")
    }

    @Test
    fun aSeekThatStaysOnTheEndKeepsCounting() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 600_000L; fakeDurationMs = 600_000L }
        val (controller, session) = buildController(this, handle, openSet = tenMinutes)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()

        controller.onSeeked() // +15 pressed again on the last frame

        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)
        controller.stop()
    }

    /** With no position or length to read, a seek cannot say it left the end, so the ending stands — today's behaviour. */
    @Test
    fun aSeekNothingCanMeasureLeavesAnEndingAlone() = runTest {
        val (controller, session) = buildController(this, FakePlayerHandle())
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()

        controller.onSeeked()

        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)
        controller.stop()
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.UpNextSeekEdgesTest'` — expected FAIL: `aSeekBackOffTheEndStopsTheCountdown` (phase stays COUNTING and the switch fires).
- [ ] **Step 3: Minimal implementation.**

  a. Append to `UpNextPlayhead.kt`:

```kotlin

/**
 * Whether the playhead sits on the title's last frame. `true` when either
 * reading is missing: a seek nothing can measure leaves an ending as it was
 * rather than guessing it away.
 */
internal fun PlayerHandle.isAtEnd(): Boolean {
    val position = positionMs() ?: return true
    val length = durationMs() ?: return true
    return position >= length - END_SLACK_MS
}

/** A clamped skip lands on the length exactly; a second's slack covers a player that rounds it to a frame. */
private const val END_SLACK_MS = 1_000L
```

  b. Replace `onSeeked` (`UpNextController.kt:107-109`; its one-line KDoc at :106 stays) with:

```kotlin
    fun onSeeked() {
        if (session.openSetId == null) return
        // Off the end is not the end any more (Restart, −15 from the last frame): left set, the countdown would switch titles under a viewer watching again.
        if (ended && !handle.isAtEnd()) ended = false
        evaluate()
    }
```

- [ ] **Step 4: Run** the Step 2 command — expected PASS; then the whole `:feature:player:testDebugUnitTest` — PASS. `wc -l UpNextController.kt` ≤ 200 (≈198: 199 − 4 after Task 2, + 3 here).
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/main/kotlin/UpNextController.kt android/feature/player/src/main/kotlin/UpNextPlayhead.kt android/feature/player/src/test/kotlin/UpNextSeekEdgesTest.kt`
  `git commit -m "fix(player): a seek back from the end stops the up-next countdown"`

### Task 4: The episode list

**Files:**
- Create: `android/feature/player/src/main/kotlin/EpisodeList.kt`
- Modify: `android/feature/player/src/test/kotlin/MediaSetFixtures.kt` (:14-45 — add `chapter`, `path`)
- Test: `android/feature/player/src/test/kotlin/EpisodeListTest.kt`

**Interfaces:**
- Consumes: `MediaSet` (`core/model/src/main/kotlin/MediaSet.kt:9`: `show`, `chapter`, `path`, `season`, `episodeFirst`, `durationSecs`, `title`, `kind`); `episodeLabel(set)` (`core/model/src/main/kotlin/EpisodeLabel.kt:14`); `WatchSnapshot.watched/progress` (`core/model/src/main/kotlin/WatchSnapshot.kt:67`); `ResumePoint.watchedFraction` (`core/data/src/main/kotlin/ResumePoint.kt:85`), `ProgressPoint` (`:120`).
- Produces: `EpisodeRow`, `EpisodeSection`, `EpisodeList`, `OTHER_SECTION`, `EPISODES_SECTION`, `UNKNOWN_TITLE`, `episodeListOf(...)` — signatures in "Interfaces this phase produces" above.

- [ ] **Step 1: Write the failing test.**

  a. `MediaSetFixtures.kt` — add two parameters after `fsk: String? = null,` (:26) and pass them through in place of the hard-coded `chapter = null,` / `path = null,` (:34-35):

```kotlin
    fsk: String? = null,
    totalBytes: Long = 0,
    chapter: String? = null,
    path: String? = null,
): MediaSet = MediaSet(
    setId = setId,
    kind = kind,
    title = title,
    rawTitle = rawTitle,
    show = show,
    chapter = chapter,
    path = path,
```

  (keep `totalBytes` where it is; the two new parameters go last so every existing positional caller is unchanged.)

  b. Create `EpisodeListTest.kt`:

```kotlin
package player

import model.Kind
import model.Progress
import model.WatchSnapshot
import model.Watched
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [episodeListOf]: the run as the episode sidebar draws it, on the phone and the television alike. */
class EpisodeListTest {

    private fun episode(id: String, season: Int?, number: Int?, durationSecs: Int? = 1_500) =
        fakeMediaSet(id, kind = Kind.EPISODE, title = "Title $id", show = "A Show", season = season, episodeFirst = number, durationSecs = durationSecs)

    private fun lesson(id: String, number: Int?, chapter: String? = null, path: String? = null) =
        fakeMediaSet(id, kind = Kind.TUTORIAL, title = "Lesson $id", show = "A Course", episodeFirst = number, chapter = chapter, path = path)

    private fun watch(watched: List<String> = emptyList(), progress: List<Progress> = emptyList()) =
        WatchSnapshot.Empty.copy(watched = watched.map { Watched(it, 1L) }, progress = progress)

    private val show = listOf(episode("a1", 1, 1), episode("a2", 1, 2), episode("b1", 2, 1), episode("b2", 2, 2))
    private val sets = show.associateBy { it.setId }
    private val run = show.map { it.setId }

    @Test
    fun aShowGroupsByItsSeasonsInRunOrderAndOpensOnTheCurrentOne() {
        val list = assertNotNull(episodeListOf("b1", run, sets, watch()))

        assertEquals(listOf("Season 1", "Season 2"), list.sections.map { it.title })
        assertEquals(listOf("a1", "a2"), list.sections[0].rows.map { it.setId })
        assertEquals(1, list.currentSection)
    }

    @Test
    fun aRowCarriesItsNumberTitleAndRuntime() {
        val row = assertNotNull(episodeListOf("b1", run, sets, watch())).sections[0].rows[1]

        assertEquals(EpisodeRow("a2", "S1E2", "Title a2", 1_500, watched = false, progress = null, current = false), row)
    }

    @Test
    fun watchedProgressAndCurrentAreEachTheirOwnRows() {
        val list = assertNotNull(
            episodeListOf(
                "b1", run, sets,
                watch(watched = listOf("a1"), progress = listOf(Progress("a2", at = 750.0, duration = 1_500.0, updatedAt = 1L))),
            ),
        )
        val rows = list.sections.flatMap { it.rows }.associateBy { it.setId }

        assertTrue(rows.getValue("a1").watched)
        assertNull(rows.getValue("a1").progress)
        assertEquals(0.5f, rows.getValue("a2").progress)
        assertFalse(rows.getValue("a2").watched)
        assertTrue(rows.getValue("b1").current)
        assertEquals(1, rows.values.count { it.current })
    }

    /** A finished title is ticked, not ruled: a position left at the credits would draw a full bar beside the tick. */
    @Test
    fun aWatchedTitleDrawsNoProgressLineEvenWithAPositionLeft() {
        val list = assertNotNull(episodeListOf("b1", run, sets, watch(watched = listOf("a2"), progress = listOf(Progress("a2", 1_490.0, 1_500.0, 1L)))))

        assertNull(list.sections[0].rows[1].progress)
    }

    @Test
    fun aRuntimeNobodyKnowsDrawsNoProgressLineAndNoRuntime() {
        val unmeasured = sets + ("a2" to episode("a2", 1, 2, durationSecs = null))
        val row = assertNotNull(episodeListOf("b1", run, unmeasured, watch(progress = listOf(Progress("a2", 300.0, null, 1L))))).sections[0].rows[1]

        assertNull(row.progress)
        assertNull(row.runtimeSecs)
    }

    @Test
    fun aFilmHasNoList() {
        val film = fakeMediaSet("f1", kind = Kind.MOVIE)

        assertNull(episodeListOf("f1", emptyList(), mapOf("f1" to film), watch()))
        // A film opened from a hand-picked list walks the list with ⏮/⏭, but it is no series to list.
        assertNull(episodeListOf("f1", listOf("f1", "f2"), mapOf("f1" to film), watch()))
    }

    @Test
    fun oneSeasonIsOneSection() {
        val list = assertNotNull(episodeListOf("a1", listOf("a1", "a2"), sets, watch()))

        assertEquals(listOf("Season 1"), list.sections.map { it.title })
        assertEquals(0, list.currentSection)
    }

    /** A course reads in the folders it was uploaded in — the catalogue's own sections. */
    @Test
    fun aCourseGroupsByItsFolders() {
        val lessons = listOf(
            lesson("l1", 1, path = "Basics/1. Start"),
            lesson("l2", 2, path = "Basics/1. Start"),
            lesson("l3", 1, chapter = "2. Broker"),
        )
        val list = assertNotNull(episodeListOf("l3", lessons.map { it.setId }, lessons.associateBy { it.setId }, watch()))

        assertEquals(listOf("Basics › 1. Start", "2. Broker"), list.sections.map { it.title })
        assertEquals("1", list.sections[0].rows[0].number)
        assertEquals(1, list.currentSection)
    }

    @Test
    fun aCourseWithNoFoldersIsOneList() {
        val lessons = listOf(lesson("l1", 1), lesson("l2", 2))
        val list = assertNotNull(episodeListOf("l1", listOf("l1", "l2"), lessons.associateBy { it.setId }, watch()))

        assertEquals(1, list.sections.size)
        assertEquals(listOf("l1", "l2"), list.sections.single().rows.map { it.setId })
    }

    /** What the catalogue cannot place still lists — last, so the seasons it can place keep their order. */
    @Test
    fun anUnknownIdAndASeasonlessEpisodeGoLast() {
        val extra = episode("x1", season = null, number = null)
        val messy = listOf("ghost", "a1", "x1", "a2")
        val list = assertNotNull(episodeListOf("a1", messy, sets + ("x1" to extra), watch()))

        assertEquals(listOf("Season 1", OTHER_SECTION), list.sections.map { it.title })
        val other = list.sections.last().rows
        assertEquals(listOf("ghost", "x1"), other.map { it.setId })
        assertEquals(UNKNOWN_TITLE, other[0].title)
        assertEquals("", other[0].number)
        assertNull(other[0].runtimeSecs)
        assertEquals("", other[1].number)
        assertEquals(0, list.currentSection)
    }

    @Test
    fun aRunOfNothingPlaceableIsOneEpisodesSection() {
        val list = assertNotNull(episodeListOf("ghost-1", listOf("ghost-1", "ghost-2"), emptyMap(), watch()))

        assertEquals(listOf(EPISODES_SECTION), list.sections.map { it.title })
        assertTrue(list.sections.single().rows[0].current)
    }

    /** Rows are keyed by id on both surfaces (a lazy list throws on a repeated key), so a list holding a title twice lists it once. */
    @Test
    fun aTitleTheRunHoldsTwiceIsListedOnce() {
        val list = assertNotNull(episodeListOf("a1", listOf("a1", "a2", "a1"), sets, watch()))

        assertEquals(listOf("a1", "a2"), list.sections.single().rows.map { it.setId })
    }

    @Test
    fun anOpenTitleTheRunDoesNotHoldOpensOnTheFirstSection() {
        val list = assertNotNull(episodeListOf("elsewhere", run, sets, watch()))

        assertEquals(0, list.currentSection)
        assertTrue(list.sections.flatMap { it.rows }.none { it.current })
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.EpisodeListTest'` — expected FAIL (compile: `episodeListOf`, `EpisodeRow`, … unresolved).
- [ ] **Step 3: Minimal implementation.** Create `EpisodeList.kt`:

```kotlin
package player

import data.ProgressPoint
import data.ResumePoint
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import model.episodeLabel

/*
 * The run the open title plays in, as the episode sidebar draws it on the
 * phone and the television alike — one model, so the two surfaces cannot
 * group or mark a season differently. Pure: the flow that keeps it current
 * is `EpisodeListFlow.kt`.
 */

/** One title of the run, as one sidebar row. */
data class EpisodeRow(
    val setId: String,
    /** `S1E4` for an episode, `4` for a lesson — the catalogue's own [episodeLabel]; empty when unnumbered or unknown. */
    val number: String,
    val title: String,
    /** The catalogue's runtime, in whole seconds; `null` when it records none. */
    val runtimeSecs: Int?,
    /** Watched to the end: drawn faint with a ✓, and still playable. */
    val watched: Boolean,
    /** How far in, 0 to 1, for a title started and not finished; `null` otherwise, or with no runtime to measure against. */
    val progress: Float?,
    /** The open title: "Now playing", and nothing to press. */
    val current: Boolean,
)

/** One season, or one section of a course: what the sidebar shows under its header. */
data class EpisodeSection(val title: String, val rows: List<EpisodeRow>)

/** The whole run, opening on [currentSection] — the section holding the open title, or the first. */
data class EpisodeList(val sections: List<EpisodeSection>, val currentSection: Int)

/** Heads what nothing could be placed for — an id the catalogue does not know, an episode with no season — when other sections exist. */
const val OTHER_SECTION = "Other"

/** Heads the same rows when they are the whole run. */
const val EPISODES_SECTION = "Episodes"

/** What a row says for an id the catalogue does not know, rather than print the raw id back at the viewer. */
const val UNKNOWN_TITLE = "Unknown title"

/**
 * [run] grouped into sections in run order — the run is already the
 * collection's play order, so nothing is re-sorted, only grouped — with
 * each row marked against [watch]. `null` for no run, and for an open title
 * the catalogue knows to belong to no show: a film played from a hand-picked
 * list still walks that list with ⏮/⏭, but it is no series or course to list.
 *
 * Progress is the web's own rule (`progressRuleFor`): any recorded position
 * against a known runtime, so the sidebar and the season page agree about
 * the same row; a watched row is ticked instead.
 */
fun episodeListOf(openId: String, run: List<String>, sets: Map<String, MediaSet>, watch: WatchSnapshot): EpisodeList? {
    if (run.isEmpty()) return null
    if (sets[openId]?.let { it.show == null } == true) return null
    val watched = watch.watched.mapTo(HashSet()) { it.setId }
    val positions = watch.progress.associateBy { it.setId }
    val grouped = LinkedHashMap<String?, MutableList<EpisodeRow>>()
    // Once each: a hand-built list may hold a title twice, and both sidebars key their rows by id.
    for (id in run.distinct()) {
        val set = sets[id]
        val done = id in watched
        val progress = if (done) null else ResumePoint.watchedFraction(positions[id]?.let { ProgressPoint(it.at, it.duration) })
        val row = EpisodeRow(
            setId = id,
            number = set?.let(::episodeLabel).orEmpty(),
            title = set?.title ?: UNKNOWN_TITLE,
            runtimeSecs = set?.durationSecs,
            watched = done,
            progress = progress?.toFloat(),
            current = id == openId,
        )
        grouped.getOrPut(set?.let(::sectionOf)) { mutableListOf() } += row
    }
    val placed = grouped.mapNotNull { (title, rows) -> title?.let { EpisodeSection(it, rows) } }
    val unplaced = grouped[null]?.let { EpisodeSection(if (placed.isEmpty()) EPISODES_SECTION else OTHER_SECTION, it) }
    val sections = placed + listOfNotNull(unplaced)
    val current = sections.indexOfFirst { section -> section.rows.any(EpisodeRow::current) }
    return EpisodeList(sections, current.coerceAtLeast(0))
}

/**
 * The section [set] sits in: the folder trail the catalogue shelves it
 * under (`Shelves.kt`'s `trailOf`, which this module may not import), or
 * `null` for an episode with no season — placed last rather than given one.
 */
private fun sectionOf(set: MediaSet): String? {
    set.path?.split('/')?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }?.let { return it.joinToString(" › ") }
    set.chapter?.takeIf(String::isNotBlank)?.let { return it }
    if (set.kind == Kind.EPISODE) return set.season?.let { "Season $it" }
    return "Chapter ${set.season ?: 1}"
}
```

- [ ] **Step 4: Run** the Step 2 command — expected PASS; then the whole `:feature:player:testDebugUnitTest` — PASS (the fixture change is additive).
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/main/kotlin/EpisodeList.kt android/feature/player/src/test/kotlin/EpisodeListTest.kt android/feature/player/src/test/kotlin/MediaSetFixtures.kt`
  `git commit -m "feat(player): episode list grouped by season or course section"`

### Task 5: `PlayerViewModel.episodes`, kept current

**Files:**
- Create: `android/feature/player/src/main/kotlin/EpisodeListFlow.kt`
- Modify: `android/feature/player/src/main/kotlin/PlayerViewModel.kt` (insert after :89)
- Test: `android/feature/player/src/test/kotlin/PlayerEpisodesWiringTest.kt`

**Interfaces:**
- Consumes: `episodeListOf` (Task 4); `UpNextUiState.run` (Task 2); `PlayerViewModel._openSetId` (`PlayerViewModel.kt:65`), `upNextController` (`:88`), constructor `catalogRepository` (`:33`), `repository.snapshot` (`core/data/src/main/kotlin/WatchStateRepository.kt:60`); `CatalogRepository.sets()` (`CatalogRepository.kt:35`); `safely` (`Safely.kt:6`); test helpers `buildViewModel` (`PlayerViewModelTestFixture.kt:23`), `installMainDispatcher` (`TestMainDispatcher.kt:18`), `WatchStateFixture` (`core/testing/src/main/kotlin/testing/WatchStateFixture.kt:33`), `WatchStateRepository.setWatched` (`WatchStateRepository.kt:98`).
- Produces: `val PlayerViewModel.episodes: StateFlow<EpisodeList?>` (member); `internal fun CoroutineScope.episodeListFlow(catalogRepository: CatalogRepository, openSetId: StateFlow<String?>, upNext: StateFlow<UpNextUiState>, watch: StateFlow<WatchSnapshot>): StateFlow<EpisodeList?>`.

- [ ] **Step 1: Write the failing test.** Create `PlayerEpisodesWiringTest.kt`:

```kotlin
package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Kind
import org.junit.After
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PlayerViewModel.episodes] — the episode sidebar's list, kept current as the run and the watch state move. */
class PlayerEpisodesWiringTest {

    private val episodes = listOf(
        fakeMediaSet("e1", kind = Kind.EPISODE, show = "A Show", season = 1, episodeFirst = 1, durationSecs = 1_200),
        fakeMediaSet("e2", kind = Kind.EPISODE, show = "A Show", season = 1, episodeFirst = 2, durationSecs = 1_200),
        fakeMediaSet("e3", kind = Kind.EPISODE, show = "A Show", season = 2, episodeFirst = 1, durationSecs = 1_200),
    )
    private val catalog = FakeCatalogRepository(episodes.associateBy { it.setId })
    private val run = episodes.map { it.setId }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anEpisodeOpenedInItsRunListsTheShowOnItsOwnSeason() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog)

        vm.open("e3", run)
        advanceUntilIdle()

        val list = assertNotNull(vm.episodes.value)
        assertEquals(listOf("Season 1", "Season 2"), list.sections.map { it.title })
        assertEquals(1, list.currentSection)
        assertTrue(list.sections[1].rows.single().current)
    }

    @Test
    fun aFilmWithNoRunHasNone() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf("f1" to fakeMediaSet("f1"))))

        vm.open("f1")
        advanceUntilIdle()

        assertNull(vm.episodes.value)
    }

    /** Finishing a title — here, or on another device a sync just brought in — re-ticks its row without the sidebar being reopened. */
    @Test
    fun aTitleFinishedWhileOpenIsTickedAtOnce() = runTest {
        installMainDispatcher()
        val watch = WatchStateFixture()
        val vm = buildViewModel(repository = watch.repository, catalogRepository = catalog)
        vm.open("e2", run)
        advanceUntilIdle()

        watch.repository.setWatched("e1", true)
        advanceUntilIdle()

        assertTrue(assertNotNull(vm.episodes.value).sections[0].rows[0].watched)
    }

    /** The catalogue finishing its own load after the title opened hands the run over late; the list follows it. */
    @Test
    fun aRunHandedOverLateBuildsTheList() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog)
        vm.open("e1")
        advanceUntilIdle()
        assertNull(vm.episodes.value)

        vm.updateRun("e1", run)
        advanceUntilIdle()

        assertEquals(3, assertNotNull(vm.episodes.value).sections.sumOf { it.rows.size })
    }

    @Test
    fun leavingThePlayerClearsTheList() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog)
        vm.open("e1", run)
        advanceUntilIdle()

        vm.stop()
        advanceUntilIdle()

        assertNull(vm.episodes.value)
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.PlayerEpisodesWiringTest'` — expected FAIL (compile: `episodes` unresolved).
- [ ] **Step 3: Minimal implementation.**

  a. Create `EpisodeListFlow.kt`:

```kotlin
package player

import data.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.MediaSet
import model.WatchSnapshot

/**
 * [episodeListOf], kept current: the open title, its run as the up-next
 * state carries it, the catalogue's sets and the watch snapshot — any of
 * them moving re-derives the list, so a title finished here or synced in
 * from another device is ticked without the sidebar being reopened.
 *
 * Only the run is read off [upNext], and only when it changes: the rest of
 * that state moves every second of a countdown, and the list has nothing
 * to redo for any of it.
 *
 * The catalogue is read whole, once, rather than a set at a time: a course
 * runs to 162 lessons, and one listing is one crossing into the core where
 * per-row lookups would be one each. It is read again only for a run
 * holding an id the last listing lacked — a library refreshed while the
 * player was open. A failed read lists every row as unknown rather than
 * breaking the player.
 */
internal fun CoroutineScope.episodeListFlow(
    catalogRepository: CatalogRepository,
    openSetId: StateFlow<String?>,
    upNext: StateFlow<UpNextUiState>,
    watch: StateFlow<WatchSnapshot>,
): StateFlow<EpisodeList?> {
    val run = upNext.map { it.run }.distinctUntilChanged()
    val sets = MutableStateFlow<Map<String, MediaSet>>(emptyMap())
    launch {
        run.collectLatest { ids ->
            if (ids.any { it !in sets.value }) {
                sets.value = safely(emptyList()) { catalogRepository.sets() }.associateBy(MediaSet::setId)
            }
        }
    }
    return combine(openSetId, run, sets, watch) { open, ids, known, snapshot ->
        open?.let { episodeListOf(it, ids, known, snapshot) }
    }.stateIn(this, SharingStarted.Eagerly, null)
}
```

  b. `PlayerViewModel.kt` — insert after `val upNext: StateFlow<UpNextUiState> = upNextController.state` (:89):

```kotlin
    /** The run as seasons or course sections of rows, for the episode sidebar — null with no run, or for a film; see [episodeListOf]. */
    val episodes: StateFlow<EpisodeList?> = viewModelScope.episodeListFlow(catalogRepository, _openSetId, upNextController.state, repository.snapshot)
```

  (`catalogRepository` is the constructor parameter at :33, readable from an initializer; `_openSetId` (:65) and `upNextController` (:88) are declared above it. `stop()` already clears `_openSetId` (:132) and the up-next state (:135), so the list goes with them.)

- [ ] **Step 4: Run** the Step 2 command — expected PASS; then `:feature:player:testDebugUnitTest` and `:ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest` (their fixtures' relaxed `CatalogRepository` mocks answer `sets()` with an empty list) — PASS. `wc -l PlayerViewModel.kt` = 200 (two lines, no blank, directly under `upNext`).
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/main/kotlin/EpisodeListFlow.kt android/feature/player/src/main/kotlin/PlayerViewModel.kt android/feature/player/src/test/kotlin/PlayerEpisodesWiringTest.kt`
  `git commit -m "feat(player): expose the open run's episode list"`

### Task 6: Previous, a picked row and Restart on the ViewModel

**Files:**
- Create: `android/feature/player/src/main/kotlin/PlayerViewModelRun.kt`
- Test: `android/feature/player/src/test/kotlin/PlayerRunStepsTest.kt`

**Interfaces:**
- Consumes: `previousInQueue`, `UpNextUiState.run/hasPrevious/inRun`, `UpNextController.playFromRun` (Task 2); `PlayerViewModel.session` (`PlayerViewModel.kt:63`), `upNextController` (`:88`), `upNext` (`:89`), `handle` (`:28`), `pendingSwitch` (`:104`); `PlayerHandle.player` (`PlayerHandle.kt:21`); `FakePlayerHandle.installPlayer` (`FakePlayerHandle.kt:14`).
- Produces: `fun PlayerViewModel.previous()`, `fun PlayerViewModel.playFromRun(setId: String)`, `fun PlayerViewModel.restart()`.

- [ ] **Step 1: Write the failing test.** Create `PlayerRunStepsTest.kt`:

```kotlin
package player

import androidx.media3.common.Player
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The card's ↺ and ⏮ and a row picked in the episode sidebar, through [PlayerViewModel]. */
class PlayerRunStepsTest {

    private val run = listOf("e1", "e2", "e3")

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun previousOpensTheTitleBeforeThroughTheSwitchNextTakes() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("e2", run)
        advanceUntilIdle()

        vm.previous()

        assertEquals(PendingPlayerSwitch("e1", run), vm.pendingSwitch.value)
    }

    @Test
    fun previousOnTheFirstTitleDoesNothing() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("e1", run)
        advanceUntilIdle()

        vm.previous()

        assertFalse(vm.upNext.value.hasPrevious)
        assertNull(vm.pendingSwitch.value)
    }

    @Test
    fun aFilmHasNoRunToStepThrough() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("f1")
        advanceUntilIdle()

        vm.previous()

        assertFalse(vm.upNext.value.inRun)
        assertNull(vm.pendingSwitch.value)
    }

    @Test
    fun aRowPickedInTheSidebarOpensThroughTheSameSwitch() = runTest {
        installMainDispatcher()
        val vm = buildViewModel()
        vm.open("e1", run)
        advanceUntilIdle()

        vm.playFromRun("e3")

        assertEquals(PendingPlayerSwitch("e3", run), vm.pendingSwitch.value)
    }

    /** ↺ is a seek, never a reopen: whatever play or pause was, stays. */
    @Test
    fun restartSeeksToTheTopAndLeavesPlayAndPauseAlone() = runTest {
        installMainDispatcher()
        val player = mockk<Player>(relaxed = true)
        val vm = buildViewModel(FakePlayerHandle().apply { installPlayer(player) })
        vm.open("e2", run)
        advanceUntilIdle()

        vm.restart()

        verify { player.seekTo(0L) }
        verify(exactly = 0) { player.play() }
        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.playWhenReady = any() }
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.PlayerRunStepsTest'` — expected FAIL (compile: `previous`, `playFromRun`, `restart` unresolved).
- [ ] **Step 3: Minimal implementation.** Create `PlayerViewModelRun.kt`:

```kotlin
package player

/*
 * The card's own steps through the run and back to the top of the title —
 * split out of [PlayerViewModel] to keep that file under the project's line
 * guideline, as `PlayerViewModelDelegates.kt` is.
 */

/**
 * Opens the title before the open one in its run, through the switch Next
 * takes — so it saves where this one stood and moves the library to match.
 * Nothing at the start of a run or without one, and never a restart:
 * [restart] is that, a button of its own.
 */
fun PlayerViewModel.previous() {
    val open = session.openSetId ?: return
    previousInQueue(upNext.value.run, open)?.let(upNextController::playFromRun)
}

/** A row picked in the episode sidebar, opened the same way; the open row, or one the run does not hold, does nothing. */
fun PlayerViewModel.playFromRun(setId: String) = upNextController.playFromRun(setId)

/**
 * Back to 0:00 of the open title. A seek, so it leaves play and pause as
 * they were; a countdown already running after the credits stops, since
 * the seek has left the end (`UpNextController.onSeeked`).
 */
fun PlayerViewModel.restart() {
    handle.player.value?.seekTo(0L)
}
```

- [ ] **Step 4: Run** the Step 2 command — expected PASS; then `:feature:player:testDebugUnitTest` — PASS.
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/main/kotlin/PlayerViewModelRun.kt android/feature/player/src/test/kotlin/PlayerRunStepsTest.kt`
  `git commit -m "feat(player): previous, pick from run and restart on the player"`

### Task 7: The card stays up while a menu or the sidebar is open

**Files:**
- Modify: `android/feature/player/src/main/kotlin/ControlsVisibility.kt` (:34-45)
- Test: `android/feature/player/src/test/kotlin/ControlsVisibilityTest.kt` (append inside the class)

**Interfaces:**
- Consumes: `controlsShouldFade(isPlaying, isScrubbing)` (`ControlsVisibility.kt:42`) — callers `ui-mobile/.../PlayerScreenLifecycle.kt:34`, `ui-tv/.../TvPlayerScreenEffects.kt:36` stay source-compatible (new parameter defaults to `false`).
- Produces: `fun controlsShouldFade(isPlaying: Boolean, isScrubbing: Boolean, menuOrSidebarOpen: Boolean = false): Boolean`.

- [ ] **Step 1: Write the failing test.** Append to `ControlsVisibilityTest`:

```kotlin

    /**
     * A menu or the episode sidebar is a viewer in the middle of a choice:
     * the card going would take the choice with it, and on a television
     * the remote's focus too.
     */
    @Test
    fun anOpenMenuOrSidebarKeepsThemUp() {
        assertFalse(controlsShouldFade(isPlaying = true, isScrubbing = false, menuOrSidebarOpen = true))
    }
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.ControlsVisibilityTest'` — expected FAIL (compile: no parameter `menuOrSidebarOpen`).
- [ ] **Step 3: Minimal implementation.** Replace `ControlsVisibility.kt:34-45` with:

```kotlin
/**
 * Whether the bar should take itself away.
 *
 * A running film gets its picture back; a paused one keeps its controls,
 * because nothing else on screen offers a way to start again and the tap that
 * would bring them back is invisible. A drag in progress keeps them too — the
 * slider cannot be pulled out from under the thumb holding it — and so does an
 * open menu or episode sidebar, a choice the viewer is in the middle of.
 */
fun controlsShouldFade(
    isPlaying: Boolean,
    isScrubbing: Boolean,
    menuOrSidebarOpen: Boolean = false,
): Boolean = isPlaying && !isScrubbing && !menuOrSidebarOpen
```

- [ ] **Step 4: Run** the Step 2 command — expected PASS.
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/main/kotlin/ControlsVisibility.kt android/feature/player/src/test/kotlin/ControlsVisibilityTest.kt`
  `git commit -m "feat(player): controls stay up while a menu or the sidebar is open"`

### Task 8: CC on brings back the title's language, else today's choice

**Files:**
- Test: `android/feature/player/src/test/kotlin/SubtitleCardToggleTest.kt`
- No main-code change: the rule is already `SubtitleChoiceController.toggle` (`SubtitleChoiceController.kt:126`) over `toggleOn` (`SubtitleChoice.kt:67`), the web's `subtitle-picker.js:106-113`.

**Interfaces:**
- Consumes: `PlayerViewModel.toggleSubtitles()` (`PlayerViewModelDelegates.kt:41`); `PlayerChoices.ccVisible` (`PlayerChoices.kt:49`), `subtitlesOn` (`:52`); `subtitledShow`, `withSubtitles`, `selected` (`SubtitleTestFixtures.kt`); `FakePlayerPreferences` (`FakePlayerPreferences.kt:16`); `SUBTITLES_OFF` (`SubtitleChoice.kt:7`).
- Produces: nothing new — pins `ccVisible` as the card's CC enabled state and `toggleSubtitles()` as its press.

- [ ] **Step 1: Write the test.** Create `SubtitleCardToggleTest.kt`:

```kotlin
package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The card's CC button over [toggleSubtitles]: on brings back the language
 * last chosen for the title, and with none it picks what the player picks
 * today — the web's own `c` key rule. With no regular track it has nothing
 * to act on, which the card draws as disabled off [PlayerChoices.ccVisible].
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleCardToggleTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val show = subtitledShow

    /** "Off" remembered for the show is no language to bring back: on falls through to the profile's own. */
    @Test
    fun onWithNoRememberedLanguagePicksTheProfileLanguage() = runTest {
        installMainDispatcher()
        val preferences = FakePlayerPreferences(
            mapOf(("p1" to "key:show-x") to mapOf("subtitle" to SUBTITLES_OFF), ("p1" to "profile") to mapOf("subtitle" to "en")),
        )
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = preferences,
        )
        vm.open(show.setId)
        advanceUntilIdle()
        assertEquals(SUBTITLES_OFF, selected(vm))

        vm.toggleSubtitles()
        advanceUntilIdle()

        assertEquals("en", selected(vm))
    }

    @Test
    fun onAfterOffBringsBackTheLanguageChosenForTheTitle() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(
            catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de", "en"))),
            preferences = FakePlayerPreferences(mapOf(("p1" to "key:show-x") to mapOf("subtitle" to "en"))),
        )
        vm.open(show.setId)
        advanceUntilIdle()

        vm.toggleSubtitles() // off
        vm.toggleSubtitles() // on
        advanceUntilIdle()

        assertEquals("en", selected(vm), "the show's own language, not the file's first track")
    }

    @Test
    fun aTitleWithARegularTrackHasCcToActOn() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to withSubtitles(show, "de"))))
        vm.open(show.setId)
        advanceUntilIdle()

        assertTrue(vm.choices.value.ccVisible)
    }

    @Test
    fun aTitleWithNoRegularTrackHasNothingForCcToActOn() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf(show.setId to show)))
        vm.open(show.setId)
        advanceUntilIdle()

        assertFalse(vm.choices.value.ccVisible)
        vm.toggleSubtitles()
        advanceUntilIdle()
        assertFalse(vm.choices.value.subtitlesOn)
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :feature:player:testDebugUnitTest --tests 'player.SubtitleCardToggleTest'` — expected **PASS on first run**: this pins existing behaviour the card relies on (Review Focus 4). A FAIL means the card's assumption is wrong — stop and report; do not change the rule here.
- [ ] **Step 3: Implementation:** none.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit.**
  `git add android/feature/player/src/test/kotlin/SubtitleCardToggleTest.kt`
  `git commit -m "test(player): pin the CC toggle rule the control card relies on"`

### Task 9: The card's shared look, menu placement and ⏮ icon

**Files:**
- Create: `android/ui-common/src/main/kotlin/ui/player/PlayerCardSurface.kt`
- Modify: `android/ui-common/src/main/kotlin/ui/player/TransportIcons.kt` (insert after `Next`, :73-83)
- Test: `android/ui-common/src/test/kotlin/ui/player/CardMenuPlacementTest.kt`

**Interfaces:**
- Consumes: `designsystem.Radius.card` (`core/designsystem/src/main/kotlin/Spacing.kt:25`, 12 dp); `SCRIM_ALPHA` (`ui-common/.../PlayerScrim.kt:10`, for the doc contrast); `TransportIcons.icon(...)` (`TransportIcons.kt:85`).
- Produces: `const val CARD_FILL_ALPHA = 0.78f`; `fun Modifier.playerCard(): Modifier`; `fun cardMenuOffset(anchor: IntRect, card: IntRect, menu: IntSize, gap: Int): IntOffset`; `TransportIcons.Previous: ImageVector`.

- [ ] **Step 1: Write the failing test.** Create `CardMenuPlacementTest.kt`:

```kotlin
package ui.player

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Test
import kotlin.test.assertEquals

/** Where a card's menu opens: directly above the button that opened it, inside the card's width. */
class CardMenuPlacementTest {
    private val card = IntRect(left = 100, top = 800, right = 900, bottom = 1_000)

    @Test
    fun aMenuSitsDirectlyAboveItsButtonCentredOnIt() {
        val anchor = IntRect(left = 400, top = 900, right = 460, bottom = 948)

        assertEquals(IntOffset(330, 692), cardMenuOffset(anchor, card, IntSize(200, 200), gap = 8))
    }

    @Test
    fun aMenuOverTheCardsLeftEdgeStaysInsideIt() {
        val anchor = IntRect(left = 110, top = 900, right = 158, bottom = 948)

        assertEquals(100, cardMenuOffset(anchor, card, IntSize(200, 100), gap = 8).x)
    }

    @Test
    fun aMenuOverTheCardsRightEdgeStaysInsideIt() {
        val anchor = IntRect(left = 850, top = 900, right = 898, bottom = 948)

        assertEquals(700, cardMenuOffset(anchor, card, IntSize(200, 100), gap = 8).x)
    }

    @Test
    fun aMenuWiderThanTheCardStartsAtItsLeftEdge() {
        val anchor = IntRect(left = 400, top = 900, right = 460, bottom = 948)

        assertEquals(100, cardMenuOffset(anchor, card, IntSize(1_000, 100), gap = 8).x)
    }

    @Test
    fun aMenuTallerThanTheRoomAboveStopsAtTheTop() {
        val anchor = IntRect(left = 400, top = 300, right = 460, bottom = 348)

        assertEquals(0, cardMenuOffset(anchor, card, IntSize(200, 600), gap = 8).y)
    }
}
```

- [ ] **Step 2: Run it.** `cd android && ./gradlew -q :ui-common:testDebugUnitTest --tests 'ui.player.CardMenuPlacementTest'` — expected FAIL (compile: `cardMenuOffset` unresolved).
- [ ] **Step 3: Minimal implementation.**

  a. Create `PlayerCardSurface.kt` (named so no `ui.player` file in `ui-mobile` can share its facade class):

```kotlin
package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import designsystem.Radius

/**
 * The player card's fill: black at 78%. Darker than [SCRIM_ALPHA], since a
 * card has no fade to lean on — its edge is a hard line across the picture.
 */
const val CARD_FILL_ALPHA = 0.78f

/** The card's hairline, about 8% white — the web card's own border. */
private const val CARD_EDGE_ALPHA = 0.08f

private val CardShape = RoundedCornerShape(Radius.card)

/**
 * The web's frosted card, without the frost: the same radius and hairline,
 * filled with a dark tint instead of a blur. A deliberate difference — the
 * video draws on its own surface, which is what keeps HDR and Dolby Vision
 * passthrough working, and blurring it would need a TextureView, which
 * breaks both. No shadow, as nowhere else in this design. The card, its
 * menus and the episode sidebar all wear this, on the phone and the
 * television alike.
 */
fun Modifier.playerCard(): Modifier =
    background(Color.Black.copy(alpha = CARD_FILL_ALPHA), CardShape)
        .border(1.dp, Color.White.copy(alpha = CARD_EDGE_ALPHA), CardShape)

/**
 * Where a card menu goes, in whatever coordinates [anchor] (the button that
 * opened it) and [card] are measured in: its bottom [gap] above the button,
 * centred on it, never out past either side of the card, and never off the
 * top — a long language list scrolls inside its own height instead.
 */
fun cardMenuOffset(anchor: IntRect, card: IntRect, menu: IntSize, gap: Int): IntOffset {
    val centred = anchor.center.x - menu.width / 2
    val x = centred.coerceIn(card.left, maxOf(card.left, card.right - menu.width))
    val y = maxOf(0, anchor.top - gap - menu.height)
    return IntOffset(x, y)
}
```

  b. `TransportIcons.kt` — insert after the `Next` icon (:83), before `private fun icon`:

```kotlin

    /** Next's mirror — the bar on the left, the triangle pointing back at it. */
    val Previous: ImageVector = icon("Previous") {
        moveTo(6f, 6f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(12f)
        horizontalLineTo(6f)
        close()
        moveTo(9.5f, 12f)
        lineToRelative(8.5f, 6f)
        verticalLineTo(6f)
        close()
    }
```

  and extend the object's KDoc first sentence (:18) to "play, pause, the two skips, next and previous". (Restart ↺, ☰ and ⓘ draw plainly as characters — none has an emoji presentation — so they stay glyphs, like the gear did.)

- [ ] **Step 4: Run** the Step 2 command — expected PASS; then `cd android && ./gradlew -q :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest` — PASS.
- [ ] **Step 5: Commit.**
  `git add android/ui-common/src/main/kotlin/ui/player/PlayerCardSurface.kt android/ui-common/src/main/kotlin/ui/player/TransportIcons.kt android/ui-common/src/test/kotlin/ui/player/CardMenuPlacementTest.kt`
  `git commit -m "feat(ui): shared player card surface, menu placement and previous icon"`

## Test matrix

| Concern | Unit (pure) | Integration (VM / controller) | End-to-end (phase 06 device walk) |
|---|---|---|---|
| Episode grouping, watched/progress/current, messy runs | `EpisodeListTest` | `PlayerEpisodesWiringTest` | tablet + TV sidebar |
| Previous / picked row / no run | `UpNextRunStepsTest` (`previousInQueue`) | `UpNextRunStepsTest`, `PlayerRunStepsTest` | ⏮ on first/last |
| Restart keeps play state; countdown after a seek back | — | `PlayerRunStepsTest`, `UpNextSeekEdgesTest` | ↺ after credits |
| 15 s skip | — | `PlayerFactoryTest` (real ExoPlayer) | −15 at 0:05, +15 near end |
| CC rule / disabled | — | `SubtitleCardToggleTest` | CC on a title with no tracks |
| Auto-hide hold | `ControlsVisibilityTest` | phases 04/05 screen tests | menu open, wait |
| Menu placement | `CardMenuPlacementTest` | phases 04/05 | menu above its button |

## Next steps

- Phases 04 (phone) and 05 (TV) start from this merge, in separate worktrees, consuming "Interfaces this phase produces" verbatim.
- Phase 05 deletes `previousInRun` (`ui-tv/.../TvPlayerRun.kt:13-19`) in favour of `PlayerViewModel.previous()` and derives `SKIP_SECONDS` from `SKIP_MS`.

## Unresolved questions

None. The progress rule (web's `progressRuleFor`, not `resumeAt`) is ruled in plan.md's review log, 2026-10-05.
