# Phase 2: Play, pause and the position readout

**Deliverable:** a viewer can pause the film and see where they are in it. The
bar appears on a tap, takes itself away while the film runs, and stays while it
is paused.

Nothing in this phase seeks. Skip and the scrubber are phase 3, behind the
hardware gate.

## Context

- Spec §3 (what gets built), §4 (how it behaves)
- `PlayerScreenTest.kt` — why decisions go in pure functions in this module
- `PlayerScreen.kt:44` — the hold that keeps seeking out of this phase

## Key insight

`ui-mobile` has no Compose test rule, and adding one is its own piece of work.
So every decision that can be stated as a function is stated as one and proved
on the JVM, and the composable is left with nothing to decide. This is the
arrangement `shouldStopOnDispose` already chose; these are two more of it.

---

### Task 1: A time a viewer can read

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/PlayerClock.kt`
- Test: `android/ui-mobile/src/test/kotlin/PlayerClockTest.kt`

**Interfaces — Produces:** `internal fun clockTime(ms: Long): String`. Tasks 3
uses it for both ends of the readout; phase 3 uses it for the scrub label.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The same shape the web player prints, because a viewer who uses both should
 * not have to read two clocks. `format.js` is the reference.
 */
class PlayerClockTest {

    @Test
    fun anEpisodeIsMinutesAndSeconds() {
        assertEquals("58:02", clockTime(3_482_000))
    }

    @Test
    fun aFilmGainsAnHourAndPadsTheMinutes() {
        assertEquals("1:58:02", clockTime(7_082_000))
    }

    /** Padded to `0:58:02` it reads as a stopwatch rather than a running time. */
    @Test
    fun anHourThatIsNotThereIsNotPrinted() {
        assertEquals("0:09", clockTime(9_000))
    }

    /** Truncated, not rounded: a clock shows 12:34 until 12:35 has arrived. */
    @Test
    fun aPartSecondHasNotHappenedYet() {
        assertEquals("0:12", clockTime(12_999))
    }

    /**
     * `durationMs` is `C.TIME_UNSET` — a large negative — until the player
     * knows the length, and the bar is drawn before it does.
     */
    @Test
    fun aLengthNobodyKnowsYetIsZero() {
        assertEquals("0:00", clockTime(Long.MIN_VALUE))
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*PlayerClockTest*'
```

Expected: FAIL to compile — `unresolved reference: clockTime`.

- [ ] **Step 3: Implement**

```kotlin
package ui

/**
 * A position as a viewer reads it: `1:58:02` for a film, `58:02` for an
 * episode, `0:09` for the first few seconds.
 *
 * Mirrors `clockTime` in the web player's `format.js`, down to dropping the
 * hour when there isn't one and padding the minutes only when there is. Two
 * surfaces over one library should not print a time two ways.
 *
 * Truncates rather than rounds, and treats anything negative as zero: media3
 * reports a length it does not know yet as `C.TIME_UNSET`.
 */
internal fun clockTime(ms: Long): String {
    val total = if (ms > 0L) ms / 1_000L else 0L
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (hours == 0L) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    }
}
```

- [ ] **Step 4: Run the test**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*PlayerClockTest*'
```

Expected: PASS, all five.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/PlayerClock.kt \
        android/ui-mobile/src/test/kotlin/PlayerClockTest.kt
git commit -m "feat(android): print a position the way the web player does"
```

---

### Task 2: When the bar is on screen

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ControlsVisibility.kt`
- Test: `android/ui-mobile/src/test/kotlin/ControlsVisibilityTest.kt`

**Interfaces — Produces:** `internal fun controlsMayShow(state: PlayerUiState): Boolean`,
`internal fun controlsShouldFade(isPlaying: Boolean): Boolean`, and
`internal const val CONTROLS_LINGER_MS = 4_000L`. Task 3 calls all three.
Phase 3 adds an `isScrubbing` parameter to `controlsShouldFade`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui

import player.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsVisibilityTest {

    @Test
    fun thereIsNothingToControlWhileASetIsPreparing() {
        assertFalse(controlsMayShow(PlayerUiState.Preparing))
    }

    @Test
    fun thereIsNothingToControlOverAnError() {
        assertFalse(controlsMayShow(PlayerUiState.Failed("no route to the channel")))
    }

    @Test
    fun aPlayingSetHasControls() {
        assertTrue(controlsMayShow(PlayerUiState.Playing))
    }

    @Test
    fun aPausedSetHasControls() {
        assertTrue(controlsMayShow(PlayerUiState.Paused))
    }

    @Test
    fun aRunningFilmTakesItsControlsBack() {
        assertTrue(controlsShouldFade(isPlaying = true))
    }

    /** The tap that would bring them back is the one a viewer cannot see. */
    @Test
    fun aPausedPictureKeepsThemOrThereIsNoWayOut() {
        assertFalse(controlsShouldFade(isPlaying = false))
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*ControlsVisibilityTest*'
```

Expected: FAIL to compile — `unresolved reference: controlsMayShow`.

- [ ] **Step 3: Implement**

```kotlin
package ui

import player.PlayerUiState

/**
 * When the control bar may be on screen, and when it goes away on its own.
 *
 * Pure, and kept apart from the composable, because this module has no Compose
 * test rule — `PlayerScreenTest` says so. A decision that lives in a function
 * can be proved; the same decision inside a `LaunchedEffect` cannot, here,
 * today.
 */

/** How long the bar stays after a tap, while the film is running. */
internal const val CONTROLS_LINGER_MS = 4_000L

/**
 * Whether there is anything to control.
 *
 * A set that is preparing has no playhead to move and nothing to pause, and a
 * bar drawn over an error invites a press that cannot do anything — worse than
 * no bar, because it looks like the error might be dismissible.
 */
internal fun controlsMayShow(state: PlayerUiState): Boolean =
    state == PlayerUiState.Playing || state == PlayerUiState.Paused

/**
 * Whether the bar should take itself away.
 *
 * A running film gets its picture back; a paused one keeps its controls,
 * because nothing else on screen offers a way to start again and the tap that
 * would bring them back is invisible.
 */
internal fun controlsShouldFade(isPlaying: Boolean): Boolean = isPlaying
```

- [ ] **Step 4: Run the test**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*ControlsVisibilityTest*'
```

Expected: PASS, all six.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ControlsVisibility.kt \
        android/ui-mobile/src/test/kotlin/ControlsVisibilityTest.kt
git commit -m "feat(android): decide when the player's controls are on screen"
```

---

### Task 3: The bar, and the screen that shows it

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/PlayerControls.kt`
- Modify: `android/ui-mobile/src/main/kotlin/PlayerScreen.kt`

**Interfaces — Consumes:** `clockTime` (Task 1); `controlsMayShow`,
`controlsShouldFade`, `CONTROLS_LINGER_MS` (Task 2).
**Produces:** `@Composable fun PlayerControls(player: Player, modifier: Modifier = Modifier)`.
Phase 3 adds skip buttons and a scrubber inside it, and gives it an
`onScrubbingChanged: (Boolean) -> Unit` parameter.

These land together because neither is verifiable alone in this module: the bar
has no test rule to render it, and the wiring has nothing to wire without it.
The verification is the device, at step 4.

- [ ] **Step 1: Write `PlayerControls.kt`**

```kotlin
// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import designsystem.Spacing

/**
 * How often the readout catches up with the playhead. Twice a second: a clock
 * printing whole seconds needs no more, and a tick is a recomposition.
 */
private const val TICK_MS = 500L

/** Enough to keep white legible over a bright frame without hiding it. */
private const val SCRIM_ALPHA = 0.55f

/**
 * The transport bar.
 *
 * Everything shown here is a fact ExoPlayer already keeps, read through
 * media3's own state holders rather than carried through the ViewModel — see
 * `feature/player/build.gradle.kts` for where that line is drawn and why. The
 * holders start and stop observing with the composition, so nothing here runs
 * a timer or removes a listener.
 *
 * Glyphs rather than icons: this module has no Material icons dependency, and
 * `PlayerScreen` already draws its back arrow as text. Four more characters do
 * not earn an artifact.
 */
@Composable
fun PlayerControls(player: Player, modifier: Modifier = Modifier) {
    val playPause = rememberPlayPauseButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .padding(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphButton(
            glyph = if (playPause.showPlay) "▶" else "⏸",
            description = if (playPause.showPlay) "Play" else "Pause",
            enabled = playPause.isEnabled,
            onClick = playPause::onClick,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TimeText(clockTime(progress.currentPositionMs))
            TimeText(clockTime(progress.durationMs))
        }
    }
}

/**
 * A control drawn as a character, named for a screen reader.
 *
 * The name is not decoration: a glyph has no accessible text of its own, so
 * without this the button announces itself as nothing at all.
 */
@Composable
private fun GlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = glyph,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
}
```

- [ ] **Step 2: Wire it into `PlayerScreen.kt`**

Add these imports:

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
```

Replace the screen's doc comment — the old one describes a player with no
controls, and will otherwise outlive the thing it describes:

```kotlin
/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing and stopping playback when
 * this leaves composition for real — not on a rotation, which destroys
 * and recreates this same composition too (there is no
 * `android:configChanges`) while the singleton player/ViewModel underneath
 * survive regardless; see [shouldStopOnDispose].
 *
 * A tap toggles the transport bar, which takes itself away while a film runs
 * and stays while it is paused; see [controlsShouldFade]. There is no seek
 * control yet — see [PlayerControls].
 */
```

Inside the composable, after the `KeepScreenOnWhile` call, add the visibility
state and its timer:

```kotlin
    // Shown when the screen opens, so a viewer finds out the bar is there at
    // all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    LaunchedEffect(controlsShown, state) {
        if (!controlsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = state is PlayerUiState.Playing)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        controlsShown = false
    }
```

Give the `Box` the tap gesture:

```kotlin
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controlsShown = !controlsShown } },
        contentAlignment = Alignment.Center,
    ) {
```

And draw the bar alongside the picture, replacing `player?.let { Video(it) }`:

```kotlin
        player?.let { current ->
            Video(current)
            if (controlsShown && controlsMayShow(state)) {
                PlayerControls(
                    player = current,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
```

The back `IconButton` stays exactly where it is, with its comment. It is a
child of the `Box`, so it consumes its own taps and does not toggle the bar.

- [ ] **Step 3: Build and check the line budget**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest
wc -l ui-mobile/src/main/kotlin/PlayerScreen.kt ui-mobile/src/main/kotlin/PlayerControls.kt
```

Expected: PASS, and both files under 200 lines. If `PlayerScreen.kt` has gone
over, move `CenteredSpinner` and `CenteredError` into a new
`ui-mobile/src/main/kotlin/PlayerOverlays.kt` — they are the two pieces with no
tie to the screen's own state.

- [ ] **Step 4: Prove it on the phone**

```bash
cd android && ./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

Open any set and confirm, by eye:

1. The bar is there when the picture starts, and gone about four seconds later.
2. A tap brings it back; another tap dismisses it.
3. Pause stops the picture, the glyph becomes `▶`, and the bar **stays**.
4. The elapsed time advances while playing and holds while paused.
5. The length on the right is the film's, not `0:00`.
6. A rotation does not restart the film.

Use `adb shell input swipe X Y X Y 150` rather than `input tap` if driving it
from the terminal — `input tap` is unreliable against Compose.

Record what happened in the phase's Review section, including anything that
looked wrong and was accepted.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/PlayerControls.kt \
        android/ui-mobile/src/main/kotlin/PlayerScreen.kt
git commit -m "feat(android): let a viewer pause the film and see where they are"
```

## Todo list

- [ ] `clockTime` prints what `format.js` prints
- [ ] The visibility decisions are pure and proved
- [ ] The bar draws play/pause and both ends of the clock
- [ ] A tap toggles it; it fades while playing and stays while paused
- [ ] Both files under 200 lines
- [ ] Pause, the advancing clock and a rotation all confirmed on a real phone

## Success criteria

A film on the phone can be paused and resumed, the elapsed time advances, and
the bar behaves as described without covering the picture permanently.
`./gradlew :ui-mobile:testDebugUnitTest` passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The tap gesture swallows presses meant for the back arrow or the bar | Both are children of the `Box` and consume first. Confirmed by eye at step 4, items 2 and 3. |
| `PlayerScreen.kt` crosses 200 lines | Step 3 measures it and names the extraction to make if it has. |
| `progress.durationMs` is `C.TIME_UNSET` when the bar first draws | `clockTime` treats anything negative as zero, proved in Task 1's last test. |
| A recomposition twice a second is wasteful | It redraws two `Text`s inside a bar that is only composed while shown. If it shows up, raise `TICK_MS` — the clock prints whole seconds either way. |

## Next steps

Phase 3 adds the two controls that seek, behind the hardware gate.

## Review

_Filled in when the phase completes — including what step 4 showed._
