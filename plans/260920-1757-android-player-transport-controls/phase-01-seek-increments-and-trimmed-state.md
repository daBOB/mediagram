# Phase 1: Seek increments and the trimmed state

**Deliverable:** a player that skips ten seconds in both directions, a
`PlayerUiState` that no longer carries a position nothing updates, and the
boundary between transport and the library written down where the next person
will look.

No UI in this phase. Nothing a viewer can see changes.

## Context

- Spec §2 (the boundary), §3 (what gets built, and the note on the trimmed fields)
- `docs/system-architecture.md` §2 — the 200-line limit
- media3 defaults: `DEFAULT_SEEK_BACK_INCREMENT_MS` is 5 000, `DEFAULT_SEEK_FORWARD_INCREMENT_MS` is 15 000

## Key insight

`PlayerUiState.Playing(positionMs, durationMs)` has never ticked.
`DefaultPlayerHandle` notifies only when playing starts or stops and when the
player reaches `STATE_READY`, so the numbers are a snapshot taken at two moments
and then left. Phase 2 reads position from media3 instead, which is what makes
the fields safe to remove rather than merely unused.

---

### Task 1: A skip moves ten seconds

**Files:**
- Modify: `android/core/playback/src/main/kotlin/PlayerFactory.kt`
- Test: `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt`

**Interfaces — Produces:** `buildPlayer(context, currentCore)` returns an
`ExoPlayer` whose `seekBackIncrement` and `seekForwardIncrement` are both
`10_000L`. Phase 2 reads those properties to label its buttons, so the label
cannot drift from the behaviour.

- [ ] **Step 1: Write the failing test**

Append to `PlayerFactoryTest.kt` (the class already has a
`@Before resetTheSharedCache` and runs under Robolectric — leave both alone):

```kotlin
    /**
     * Both directions, explicitly. media3 defaults to five seconds back and
     * fifteen forward, so a bar whose buttons both say ten would be telling
     * a viewer something the player does not do.
     */
    @Test
    fun aSkipMovesTenSecondsInEitherDirection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val player = buildPlayer(context) { FakeCore() }

        try {
            assertEquals(10_000L, player.seekBackIncrement)
            assertEquals(10_000L, player.seekForwardIncrement)
        } finally {
            player.release()
        }
    }
```

Add `import kotlin.test.assertEquals` to the file's imports.

- [ ] **Step 2: Run the test**

```bash
cd android && ./gradlew :core:playback:testDebugUnitTest --tests '*PlayerFactoryTest*'
```

Expected: FAIL — `expected:<10000> but was:<5000>`.

- [ ] **Step 3: Implement**

In `PlayerFactory.kt`, add the two builder calls and the constant. The
`setMediaSourceFactory` call and its comment block stay exactly as they are:

```kotlin
suspend fun buildPlayer(context: Context, currentCore: () -> CoreClient?): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory(context, currentCore)),
        )
        // Set in both directions because media3's defaults are not
        // symmetrical — five seconds back, fifteen forward. A control that
        // offers the same jump each way has to say so here; the buttons read
        // their labels back off the player rather than carry their own copy.
        .setSeekBackIncrementMs(SKIP_MS)
        .setSeekForwardIncrementMs(SKIP_MS)
        .build()

/**
 * How far one skip moves. Ten seconds is long enough to clear a line of
 * dialogue that was missed and short enough that two of them are not a
 * scene.
 */
private const val SKIP_MS = 10_000L
```

- [ ] **Step 4: Run the test**

```bash
cd android && ./gradlew :core:playback:testDebugUnitTest --tests '*PlayerFactoryTest*'
```

Expected: PASS, both cases.

- [ ] **Step 5: Commit**

```bash
git add android/core/playback/src/main/kotlin/PlayerFactory.kt \
        android/core/playback/src/test/kotlin/PlayerFactoryTest.kt
git commit -m "feat(android): skip the same ten seconds in both directions"
```

---

### Task 2: A state that only says what it knows

**Files:**
- Modify: `android/feature/player/src/main/kotlin/PlayerUiState.kt`
- Modify: `android/feature/player/src/main/kotlin/PlayerHandle.kt`
- Modify: `android/feature/player/src/main/kotlin/PlayerViewModel.kt`
- Modify: `android/feature/player/src/main/kotlin/DefaultPlayerHandle.kt`
- Modify: `android/feature/player/build.gradle.kts`
- Modify: `android/ui-mobile/src/main/kotlin/PlayerScreen.kt:65,76`
- Test: `android/feature/player/src/test/kotlin/FakePlayerHandle.kt`, `PlayerViewModelTest.kt`, `PlayerReopenTest.kt`, `DefaultPlayerHandleTest.kt`

**Interfaces — Consumes:** nothing from Task 1.
**Produces:** `PlayerUiState` = `Preparing`, `Playing`, `Paused` (all `data object`),
`Failed(message: String)`. `PlayerHandle.Listener.onPlayingChanged(isPlaying: Boolean)`
replaces `onPositionChanged(positionMs, durationMs, isPlaying)`.
`FakePlayerHandle.emitPlaying(isPlaying: Boolean)` replaces `emitPosition(...)`.
Phase 2 matches on these exact names.

- [ ] **Step 1: Write the failing tests**

This is a type change, so the tests move first and the module stops compiling
until the source follows. That compile failure *is* the red step.

In `PlayerViewModelTest.kt`, replace `positionUpdatesReflectWhetherThePlayerIsPlaying` entirely:

```kotlin
    @Test
    fun theStateFollowsWhetherThePlayerIsPlaying() = runTest {
        val handle = FakePlayerHandle()
        val vm = PlayerViewModel(handle)
        vm.open("s1")

        handle.emitPlaying(isPlaying = true)
        assertEquals(PlayerUiState.Playing, vm.state.value)

        handle.emitPlaying(isPlaying = false)
        assertEquals(PlayerUiState.Paused, vm.state.value)
    }
```

In `FakePlayerHandle.kt`, replace `emitPosition`:

```kotlin
    fun emitPlaying(isPlaying: Boolean) {
        listener?.onPlayingChanged(isPlaying)
    }
```

In `PlayerReopenTest.kt`: change the three assertions at lines 41, 50 and 67
from `PlayerUiState.Playing(0, DURATION_MS)` / `PlayerUiState.Playing(42_000, DURATION_MS)`
to `PlayerUiState.Playing`. Rename the second test and replace its position
line with a comment, so it claims only what it still proves:

```kotlin
    /**
     * Two things have to be true at once, and each is the other's failure
     * mode: a set that is still loaded must not be reloaded — that is what
     * keeps a viewer's place across a rotation — and a screen that has just
     * reset itself to "preparing" must be told the set is playing, or it
     * waits on an event that already happened.
     */
    @Test
    fun askingAgainForASetThatIsPlayingLeavesItLoadedAndSaysItIsPlaying() = runTest {
        val player = PreparablePlayer()
        val handle = DefaultPlayerHandle(CompletableDeferred(player.mock), this)
        advanceUntilIdle()
        val viewModel = PlayerViewModel(handle)

        viewModel.open("s1")

        viewModel.open("s1")

        verify(exactly = 1) { player.mock.setMediaItem(any<MediaItem>()) }
        verify(exactly = 1) { player.mock.prepare() }
        assertEquals(PlayerUiState.Playing, viewModel.state.value)
    }
```

Then strip the position plumbing from `PreparablePlayer`, which nothing reads
any more — delete `var positionMs = 0L`, the
`every { mock.currentPosition } answers { positionMs }` stub, the
`every { mock.duration } returns DURATION_MS` stub, and the now-unused
`private const val DURATION_MS = 90_000L` at the top of the file.

In `DefaultPlayerHandleTest.kt`, three anonymous listeners at lines 36, 59 and
131 change signature. Lines 36 and 131:

```kotlin
            override fun onPlayingChanged(isPlaying: Boolean) = Unit
```

Line 59:

```kotlin
            override fun onPlayingChanged(isPlaying: Boolean) {
                delivered = true
            }
```

While in that file, the `every { player.currentPosition } returns 5_000L` and
`every { player.duration } returns 10_000L` stubs in
`aListenerSetAfterReleaseStillReceivesEventsFromThePlayer` are also dead now —
delete both. The mock is `relaxed`, so nothing else needs them.

- [ ] **Step 2: Run the tests**

```bash
cd android && ./gradlew :feature:player:testDebugUnitTest
```

Expected: FAIL to compile — `unresolved reference: emitPlaying`,
`onPlayingChanged overrides nothing`, and `Playing` used where a constructor is
expected.

- [ ] **Step 3: Implement**

`PlayerUiState.kt` in full:

```kotlin
package player

/**
 * What the player screen renders; the television surface renders the same
 * states.
 *
 * No position here. Where the playhead is, is a fact the player keeps and the
 * surfaces read from it directly through media3's state holders — a second
 * copy carried through this state could only ever be the same number, later,
 * or a different one, wrongly.
 */
sealed interface PlayerUiState {
    data object Preparing : PlayerUiState
    data object Playing : PlayerUiState
    data object Paused : PlayerUiState
    data class Failed(val message: String) : PlayerUiState
}
```

`PlayerHandle.kt` — the listener method and its doc:

```kotlin
    /** Playback facts; [PlayerViewModel] maps these onto [PlayerUiState]. */
    interface Listener {
        fun onPlayingChanged(isPlaying: Boolean)
        fun onError(message: String)
    }
```

`PlayerViewModel.kt`:

```kotlin
    override fun onPlayingChanged(isPlaying: Boolean) {
        _state.value = if (isPlaying) PlayerUiState.Playing else PlayerUiState.Paused
    }
```

`DefaultPlayerHandle.kt` — rename `notifyPosition` to `notifyPlaying` and drop
the body that read the player:

```kotlin
    private fun notifyPlaying(isPlaying: Boolean) {
        listener?.onPlayingChanged(isPlaying)
    }
```

Update its three call sites: `onIsPlayingChanged` becomes
`override fun onIsPlayingChanged(isPlaying: Boolean) = notifyPlaying(isPlaying)`;
the `STATE_READY` branch calls `notifyPlaying(current.isPlaying)`; and
`republishPosition` is renamed `republishPlaybackState`, keeping its
doc comment but with its first line changed to read:

```kotlin
    /**
     * A settled player does not repeat the event that settled it, so a
     * subscriber that has just reset itself to "preparing" needs telling
     * again that playback is under way. A player still buffering is the one
     * case to stay quiet for: its own ready event is still coming, and
     * reporting "paused" ahead of it would replace a truthful spinner with a
     * false still frame.
     */
    private fun republishPlaybackState(player: Player) {
        if (player.playbackState == Player.STATE_BUFFERING) return
        notifyPlaying(player.isPlaying)
    }
```

`PlayerScreen.kt` — line 65 keeps working unchanged
(`state is PlayerUiState.Playing`), but line 76's `when` branch loses its `is`,
because objects are matched by equality:

```kotlin
            PlayerUiState.Playing, PlayerUiState.Paused -> Unit
```

`feature/player/build.gradle.kts` — extend the header comment with the rule
this phase exists to write down:

```kotlin
// ViewModels and UiState only, no composables — ui-mobile and ui-tv render
// this module's state independently.
//
// What belongs here and what does not: this module owns the library. Which
// set is open, whether it is preparing or has failed, stopping when a screen
// is left for good — anything that needs CoreClient. It does not own
// transport. Whether the player is playing, where the playhead is, how long
// the set runs, and seeking by an increment are facts ExoPlayer already
// keeps, and the surfaces read them through media3's own Compose state
// holders against the Player this module exposes. A copy of them routed
// through here could only be the same number later, or a different one.
```

- [ ] **Step 4: Run the tests**

```bash
cd android && ./gradlew :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest
```

Expected: PASS — every test in both modules.

- [ ] **Step 5: Commit**

```bash
git add android/feature/player android/ui-mobile/src/main/kotlin/PlayerScreen.kt
git commit -m "refactor(android): let the player state say only what it knows"
```

## Todo list

- [ ] Both seek increments are ten seconds, asserted
- [ ] `PlayerUiState` carries no position
- [ ] `onPlayingChanged` replaces `onPositionChanged` everywhere
- [ ] Dead mock stubs and `DURATION_MS` removed from the tests
- [ ] The transport/library boundary is written in `feature/player/build.gradle.kts`
- [ ] `:core:playback`, `:feature:player` and `:ui-mobile` unit tests all pass

## Success criteria

`./gradlew :core:playback:testDebugUnitTest :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest`
passes, `PlayerUiState` has no numeric fields, and a reader of
`feature/player/build.gradle.kts` can tell where a new player feature belongs
without asking.

## Risk assessment

| Risk | Mitigation |
|---|---|
| `buildPlayer` cannot construct a real `ExoPlayer` under Robolectric | It needs a Looper, and Robolectric's main thread has one. If it still fails, assert the two increments in an instrumented test on the device instead of dropping the assertion — the numbers are the whole point of Task 1. |
| Trimming hides a real regression in the reopen test | The guarantee that protects a viewer's place is `verify(exactly = 1) { setMediaItem }`, which is untouched. The renamed test states both guarantees it still makes. |
| `Playing` emitted twice in a row no longer re-emits | Correct, and wanted: identical `StateFlow` values collapse. Nothing downstream counts emissions — `PlayerScreen` reads the current value. |

## Next steps

Phase 2 builds the bar on top of this state. Nothing in phase 1 is visible to a
viewer, so it can land on its own.
