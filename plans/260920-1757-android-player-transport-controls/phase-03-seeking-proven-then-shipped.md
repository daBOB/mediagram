# Phase 3: Seeking, proven then shipped

**Deliverable:** skip by ten seconds either way and a draggable scrubber — and
the first evidence that reading across a part boundary survives a seek.

## Context

- Spec §6 (proving the seek), §4 (the drag)
- `android/ui-mobile/src/main/kotlin/PlayerScreen.kt` — the hold this phase discharges
- `plans/260919-0034-android-foundation-phone-tablet-tv/phase-05-playback-media3.md` — task 4, step 5, the gate
- `web/public/lib/player.js:751` — the same drag problem, solved on the other surface

## Key insight

**Skip is a seek.** It is tempting to treat the two buttons as cheaper than the
slider and ship them first, but both land the player at an arbitrary byte
offset and both cross a part boundary at exactly the same risk. So both wait
for the same proof, and both arrive in the same commit as it.

The order is the point of this phase: **build, prove on hardware, then commit.**
A seek control committed before the gate runs is precisely what the hold in
`PlayerScreen` forbids, and "it will almost certainly pass" is the argument the
hold was written against.

---

### Task 1: A bar that does not vanish from under a thumb

**Files:**
- Modify: `android/ui-mobile/src/main/kotlin/ControlsVisibility.kt`
- Test: `android/ui-mobile/src/test/kotlin/ControlsVisibilityTest.kt`

**Interfaces — Consumes:** `controlsShouldFade(isPlaying: Boolean)` from phase 2.
**Produces:** `controlsShouldFade(isPlaying: Boolean, isScrubbing: Boolean): Boolean`.
Task 2 passes the second argument from the screen.

This is a decision, not a seek, so it commits on its own ahead of the gate.

- [ ] **Step 1: Write the failing test**

In `ControlsVisibilityTest.kt`, give the two existing fade cases their new
argument and add the third:

```kotlin
    @Test
    fun aRunningFilmTakesItsControlsBack() {
        assertTrue(controlsShouldFade(isPlaying = true, isScrubbing = false))
    }

    /** The tap that would bring them back is the one a viewer cannot see. */
    @Test
    fun aPausedPictureKeepsThemOrThereIsNoWayOut() {
        assertFalse(controlsShouldFade(isPlaying = false, isScrubbing = false))
    }

    /**
     * Four seconds is easily a long drag. A bar that timed out mid-scrub would
     * take the slider with it and drop the viewer wherever the thumb had got
     * to.
     */
    @Test
    fun aBarBeingDraggedStaysUnderTheThumb() {
        assertFalse(controlsShouldFade(isPlaying = true, isScrubbing = true))
    }
```

- [ ] **Step 2: Run the test**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*ControlsVisibilityTest*'
```

Expected: FAIL to compile — `too many arguments for controlsShouldFade`.

- [ ] **Step 3: Implement**

```kotlin
/**
 * Whether the bar should take itself away.
 *
 * A running film gets its picture back; a paused one keeps its controls,
 * because nothing else on screen offers a way to start again and the tap that
 * would bring them back is invisible. A drag in progress keeps them too — the
 * slider cannot be pulled out from under the thumb holding it.
 */
internal fun controlsShouldFade(isPlaying: Boolean, isScrubbing: Boolean): Boolean =
    isPlaying && !isScrubbing
```

- [ ] **Step 4: Run the test**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*ControlsVisibilityTest*'
```

Expected: PASS, all seven.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/ControlsVisibility.kt \
        android/ui-mobile/src/test/kotlin/ControlsVisibilityTest.kt
git commit -m "feat(android): keep the controls up while the slider is held"
```

---

### Task 2: The seek controls, proven then committed

**Files:**
- Modify: `android/ui-mobile/src/main/kotlin/PlayerControls.kt`
- Modify: `android/ui-mobile/src/main/kotlin/PlayerScreen.kt`
- Modify: `plans/260919-0034-android-foundation-phone-tablet-tv/phase-05-playback-media3.md`
- Modify: `plans/260919-0034-android-foundation-phone-tablet-tv/plan.md`

**Interfaces — Consumes:** `controlsShouldFade(isPlaying, isScrubbing)` (Task 1);
`clockTime` (phase 2); `seekBackIncrement`/`seekForwardIncrement` set to 10 000 ms (phase 1).
**Produces:** `PlayerControls(player, onScrubbingChanged: (Boolean) -> Unit, modifier)`.

**Steps 1 and 2 build. Step 4 proves. Step 6 commits. Do not reorder them.**

- [ ] **Step 1: Add the seek controls to `PlayerControls.kt`**

New imports:

```kotlin
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
```

Replace the body of `PlayerControls`:

```kotlin
@Composable
fun PlayerControls(
    player: Player,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)

    // Null except while a drag is under way, when it holds where the thumb is
    // rather than where the film is. A slider snapped back to the playhead
    // twice a second could not be dragged at all — the same problem the web
    // player solves by refusing to move its slider while it holds focus.
    var scrubbingTo by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(scrubbingTo == null) { onScrubbingChanged(scrubbingTo != null) }

    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val positionMs = scrubbingTo?.toLong() ?: progress.currentPositionMs.coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .padding(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Both labels are read back off the player rather than written
            // here, so a button cannot come to say one thing and do another.
            GlyphButton(
                glyph = "⏪",
                description = "Skip back ${seekBack.seekBackAmountMs / 1_000} seconds",
                enabled = seekBack.isEnabled,
                onClick = seekBack::onClick,
            )
            GlyphButton(
                glyph = if (playPause.showPlay) "▶" else "⏸",
                description = if (playPause.showPlay) "Play" else "Pause",
                enabled = playPause.isEnabled,
                onClick = playPause::onClick,
            )
            GlyphButton(
                glyph = "⏩",
                description = "Skip forward ${seekForward.seekForwardAmountMs / 1_000} seconds",
                enabled = seekForward.isEnabled,
                onClick = seekForward::onClick,
            )
        }
        Slider(
            value = positionMs.toFloat(),
            onValueChange = { scrubbingTo = it },
            // On release, not during: every position a thumb passes over
            // would otherwise be a seek, and every seek is a read from
            // Telegram at a fresh offset.
            onValueChangeFinished = {
                scrubbingTo?.let { player.seekTo(it.toLong()) }
                scrubbingTo = null
            },
            // Never an empty range: a set whose length is not known yet would
            // give 0f..0f, which Slider rejects.
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TimeText(clockTime(positionMs))
            TimeText(clockTime(durationMs))
        }
    }
}
```

- [ ] **Step 2: Thread the drag through `PlayerScreen.kt`**

Add the scrubbing state beside `controlsShown`, and give the timer its third key:

```kotlin
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(controlsShown, state, scrubbing) {
        if (!controlsShown) return@LaunchedEffect
        val fades = controlsShouldFade(
            isPlaying = state is PlayerUiState.Playing,
            isScrubbing = scrubbing,
        )
        if (!fades) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        controlsShown = false
    }
```

Pass the callback:

```kotlin
                PlayerControls(
                    player = current,
                    onScrubbingChanged = { scrubbing = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
```

And drop the last sentence of the screen's doc comment — the one that says
there is no seek control yet. Replace it with what is now true:

```kotlin
 * A tap toggles the transport bar, which takes itself away while a film runs
 * and stays while it is paused or being scrubbed; see [controlsShouldFade].
```

- [ ] **Step 3: Build and install — do not commit**

```bash
cd android && ./gradlew :ui-mobile:testDebugUnitTest :app:installDebug
wc -l ui-mobile/src/main/kotlin/PlayerControls.kt ui-mobile/src/main/kotlin/PlayerScreen.kt
```

Expected: tests PASS, install succeeds, both files under 200 lines.

- [ ] **Step 4: Run the gate**

Find a set that actually spans parts, and work out where the boundary falls in
time. The catalog has everything needed — `parts.byte_length` for part 0,
`sets.total`, and `sets.duration`:

```bash
gate="$(mktemp -d)"
adb exec-out run-as com.mediagram.android cat files/catalog/current/library.db > "$gate/library.db"
sqlite3 -header -column "$gate/library.db" "
  SELECT s.set_id, s.title, s.part_count, s.duration AS seconds,
         CAST(p.byte_length AS REAL) / s.total * s.duration AS boundary_at
  FROM sets s JOIN parts p ON p.set_id = s.set_id AND p.idx = 0
  WHERE s.part_count > 1 AND s.duration IS NOT NULL AND s.status = 'complete';"
```

`boundary_at` is where part 0 ends, in seconds, assuming the bitrate is even
across the file. It is an estimate and does not need to be better than one —
the point is to land near the boundary and then step across it from both sides.

**Clear the cache first, or the gate lies.** A 2 GiB LRU cache lives beside the
app, and a film already in it seeks instantly through bytes that never touch
the byte path this gate exists to test:

```bash
adb shell run-as com.mediagram.android rm -rf cache/mlib
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

Open that set and, in this order:

1. Drag the scrubber to roughly thirty seconds **before** `boundary_at`. Let it
   play through the boundary without touching anything. It must not stall, tear
   or fall back to the catalog.
2. Drag to roughly thirty seconds **after** `boundary_at` — a seek that lands
   in part 1 having never read it. It must start.
3. From there, skip back with `⏪` until the playhead crosses back into part 0.
   It must keep playing.
4. Drag to the last tenth of the film — the furthest part — and confirm it
   starts.
5. Check `adb logcat -d | grep -iE "mlib|ExoPlayer|part"` for anything that
   reads as an error even though playback continued.

Use `adb shell input swipe X Y X Y 150` rather than `input tap` if driving from
the terminal; `input tap` is unreliable against Compose.

**If any of these fail, stop.** Do not commit steps 1 and 2. Write what
happened into the Review section below, keep the working tree, and treat the
failure as its own piece of work — that is exactly the outcome the hold was
protecting against, and finding it here is the gate doing its job.

- [ ] **Step 5: Record the result in the android-foundation plan**

In `phase-05-playback-media3.md`, tick task 4's step 5 and append what was
observed — the set used, its part count, and where the boundary fell:

```markdown
- [x] **Step 5: Seek across a part boundary.** Pick a set with more than one part, read the first part's `byte_length` from the index, seek to just before and just after it, and confirm playback continues without stalling or artefacts. This is the case that breaks if offset accounting is wrong. Phone only; tablet and television wait for phase 4. Observed: <set, part count, boundary in seconds, what happened>.
```

Leave step 6 and the todo item "A seek across a part boundary is clean on all
three" unticked — only one of the three has been tried. In `plan.md`, extend
phase 5's status to `Complete — tasks 1–3 and the mobile half of task 4,
including its seek gate`.

- [ ] **Step 6: Commit**

```bash
cd /home/andre/Workspace/mediagram-android
./scripts/check.sh
git add android/ui-mobile/src/main/kotlin/PlayerControls.kt \
        android/ui-mobile/src/main/kotlin/PlayerScreen.kt \
        plans/260919-0034-android-foundation-phone-tablet-tv/
git commit -m "feat(android): let a viewer move through the film"
```

`scripts/check.sh` runs clippy, the Rust tests, the web tests and the Gradle
unit tests and lint — the whole gate, not a subset, because this commit touches
the surface a viewer actually holds.

## Todo list

- [ ] A drag keeps the bar on screen, proved as a pure decision
- [ ] Skip back and skip forward, labelled from the player's own increments
- [ ] A scrubber that follows the playhead except while held, seeking on release
- [ ] The cache cleared before the gate, so it measures the byte path
- [ ] Playback continues through a part boundary, from both sides
- [ ] Task 4 step 5 of the android-foundation plan ticked, with what was seen

## Success criteria

On a real phone, against a cleared cache, a multi-part film plays through its
first part boundary, starts from a seek that lands beyond it, and survives a
skip back across it. `./scripts/check.sh` passes. The hold in `PlayerScreen` is
gone because the thing it was waiting for has happened.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The gate fails and seeking across a boundary is genuinely broken | Step 4 says stop and keep the tree. Nothing that seeks has been committed at that point, which is the whole reason for the ordering. |
| A cached film makes the gate pass without testing anything | Step 4 clears `cache/mlib` through `run-as` before the run, and says why. |
| `boundary_at` is wrong because the bitrate is uneven | It only has to be close. Steps 1 and 3 cross the boundary by *playing* through it, which does not depend on the estimate at all. |
| No set in the library has more than one part | The query returns nothing, and the gate cannot run. Upload or find a file over 3.5 GiB first; a single-part library cannot answer this question. |
| A seek beyond the end trips the `> total` / `>= total` disagreement | Spec §5 records it. ExoPlayer clamps to duration, so step 4 item 4 stops at the last tenth rather than the final byte. |

## Next steps

Sub-project B — the audio track picker and the buffer readout. Both read from
the player, both attach on the transport side of the boundary this plan wrote
down, and `ProgressStateWithTickInterval` already exposes `bufferedPositionMs`
for the second of them.

## Review

_Filled in when the phase completes — especially what the gate showed._
