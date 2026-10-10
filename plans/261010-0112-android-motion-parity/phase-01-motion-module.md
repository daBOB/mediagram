# Phase 01 — Motion module

**Spec:** `../spec.md` §1. **Status:** pending. **Depends on:** none.

## Context

- Web tokens: `web/public/styles/theme.css:120-122` (`--ease`, `--ease-out`, `--hover`), `:164` (no tap highlight), `:167` (`button:active` 0.98), `:170` (`:focus-visible` 2px accent outline), `:180` (reduced motion → `none`).
- Package of every file in `core/designsystem/src/main/kotlin` is `designsystem` (files sit flat in that folder).
- `core/designsystem` keeps material3 as `implementation` so ui-tv never sees it. **Nothing in this phase may import `androidx.compose.material3`.**
- Robolectric harness to copy: `ui-mobile/src/test/kotlin/ui/profile/PinDialogTest.kt` (an `ActivityController<ComponentActivity>` driven from `createEmptyComposeRule()`).

## Task 1.1: Tokens, reduced motion and the three motion modifiers

**Files:**
- Create: `core/designsystem/src/main/kotlin/Motion.kt`
- Create: `core/designsystem/src/main/kotlin/Entrance.kt`
- Create: `core/designsystem/src/main/kotlin/Drift.kt`
- Create: `core/designsystem/src/main/kotlin/HoverMotion.kt`
- Modify: `core/designsystem/build.gradle.kts` (test dependencies)
- Test: `core/designsystem/src/test/kotlin/MotionModifiersTest.kt`
- Test: `core/designsystem/src/test/kotlin/EntranceReuseTest.kt`

**Interfaces (produced, used by every later phase):**
```kotlin
object Motion { val Ease; val EaseOut; val CssEase; val CssEaseOut; const val HOVER_MS = 160; fun <T> hover(): TweenSpec<T> }
@Composable fun rememberReducedMotion(): Boolean
val LocalReducedMotion: ProvidableCompositionLocal<Boolean>            // default false; MediagramTheme provides it in phase 02
fun Modifier.entrance(durationMs: Int, delayMs: Int = 0, fromY: Dp = 0.dp, fromScale: Float = 1f,
                      fadeMs: Int = durationMs, easing: Easing = Motion.Ease, seen: MutableState<Boolean>? = null): Modifier
fun Modifier.drift(from: Float, to: Float, durationMs: Int, easing: Easing, running: Boolean = true,
                   translate: Offset = Offset.Zero, restScale: Float = to): Modifier   // translate is a fraction of size
fun Modifier.hoverMotion(source: InteractionSource, durationMs: Int, lift: Dp = 0.dp, nudge: Dp = 0.dp,
                         zoom: Float = 1f, nudgeOnPress: Boolean = nudge != 0.dp): Modifier
```
- **`seen`:** the call site's saved flag. Pass `rememberSaveable { mutableStateOf(false) }` from inside a lazy item, so scrolling back doesn't replay. Leave it null where a fresh node per page visit is already right, as with `key(page) { … }`.

- [ ] **Step 1: Add the Compose test dependencies to `core/designsystem/build.gradle.kts`**

In `android { … }` add `testOptions.unitTests.isIncludeAndroidResources = true`. In `dependencies { … }`, after the existing `testImplementation` lines, add:

```kotlin
    // The motion modifiers are tested by composing them and reading their
    // layer-mapped bounds on a paused test clock, as ui-mobile's tests do.
    testImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
    debugImplementation(libs.findLibrary("androidx.compose.ui.test.manifest").get())
```

- [ ] **Step 2: Write the failing tests**

Create `core/designsystem/src/test/kotlin/MotionModifiersTest.kt`:

```kotlin
package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A 100dp box centred in a 200dp one rests at top = left = 50dp and
 * width = 100dp. Layer translation and scale show up in the node's
 * bounds in root, so the tests read motion from there; alpha does not,
 * and is left to the device walk (no pixel capture works here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionModifiersTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        // Two frames: one to compose, lay out and place; one for the nodes'
        // coroutines (animations, interaction collectors) to start.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().top.value
    private fun left(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().left.value
    private fun width(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().width.value

    @Test
    fun anEntranceRisesFromItsOffsetAndEndsAtRest() {
        show { Box(Modifier.size(100.dp).entrance(420, fromY = 10.dp).testTag("e")) }
        assertTrue(top("e") > 55f, "starts below its rest, top was ${top("e")}")
        compose.mainClock.advanceTimeBy(500)
        assertEquals(50f, top("e"), 0.5f)
    }

    @Test
    fun anEntranceScalesDownToRestAfterItsDelay() {
        show { Box(Modifier.size(100.dp).entrance(1400, delayMs = 200, fromScale = 1.04f).testTag("e")) }
        compose.mainClock.advanceTimeBy(150)
        assertEquals(104f, width("e"), 0.3f)
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(100f, width("e"), 0.3f)
    }

    @Test
    fun anEntranceAlreadySeenStartsAtRest() {
        val seen = mutableStateOf(true)
        show { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) }
        assertEquals(50f, top("e"), 0.5f)
    }

    @Test
    fun anEntranceMarksItselfSeenOncePlaced() {
        val seen = mutableStateOf(false)
        show { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) }
        assertTrue(seen.value)
    }

    @Test
    fun aPausedDriftHoldsWhereItIsAndResumesFromThere() {
        var running by mutableStateOf(true)
        show { Box(Modifier.size(100.dp).drift(1f, 1.09f, 8_500, Motion.CssEaseOut, running = running).testTag("d")) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { running = false }
        compose.mainClock.advanceTimeByFrame()
        val held = width("d")
        assertTrue(held > 100.5f && held < 108.5f, "mid-drift when paused, width was $held")
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(held, width("d"), 0.05f)
        compose.runOnUiThread { running = true }
        compose.mainClock.advanceTimeBy(9_000)
        assertEquals(109f, width("d"), 0.2f)
    }

    @Test
    fun underReducedMotionADriftShowsItsRestScaleAndNeverMoves() {
        show(reducedMotion = true) {
            Box(Modifier.size(100.dp).drift(1f, 1.09f, 8_500, Motion.CssEaseOut, restScale = 1f).testTag("d"))
        }
        assertEquals(100f, width("d"), 0.05f)
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(100f, width("d"), 0.05f)
    }

    @Test
    fun aDriftRunsOnceAndStopsAtItsEnd() {
        show { Box(Modifier.size(100.dp).drift(1.06f, 1f, 14_000, Motion.EaseOut).testTag("d")) }
        assertTrue(width("d") > 105f)
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(100f, width("d"), 0.05f)
    }

    @Test
    fun aPressAloneNeverLiftsArtworkButAHoverDoes() {
        val source = MutableInteractionSource()
        show { Box(Modifier.size(100.dp).hoverMotion(source, 700, lift = (-6).dp).testTag("h")) }
        compose.runOnUiThread { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(50f, top("h"), 0.3f)
        compose.runOnUiThread { source.tryEmit(HoverInteraction.Enter()) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(44f, top("h"), 0.3f)
    }

    @Test
    fun anArrowNudgesWhilePressedAndReturnsOnRelease() {
        val source = MutableInteractionSource()
        val press = PressInteraction.Press(Offset.Zero)
        show { Box(Modifier.size(100.dp).hoverMotion(source, 480, nudge = 4.dp).testTag("a")) }
        compose.runOnUiThread { source.tryEmit(press) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(54f, left("a"), 0.3f)
        compose.runOnUiThread { source.tryEmit(PressInteraction.Release(press)) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(50f, left("a"), 0.3f)
    }

    @Test
    fun aHoverZoomGrowsTheArtworkAroundItsCentre() {
        val source = MutableInteractionSource()
        show { Box(Modifier.size(100.dp).hoverMotion(source, 1100, zoom = 1.05f).testTag("z")) }
        compose.runOnUiThread { source.tryEmit(HoverInteraction.Enter()) }
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(105f, width("z"), 0.3f)
        assertEquals(47.5f, left("z"), 0.3f)
    }
}
```

Also create `core/designsystem/src/test/kotlin/EntranceReuseTest.kt`. A lazy list keeps an item's nodes when it parks the item off screen, and hands them back to the same item or another one when it returns (verified in the ui-android 1.11.4 bytecode); `ReusableContentHost` deactivates and reactivates content exactly that way:

```kotlin
package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A lazy list parks an item it scrolled away by deactivating it, keeping its
 * nodes, and hands them back (to the same item or another) when one comes
 * into view. A 100dp box in a 200dp one rests at top = 50dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntranceReuseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) { content() } }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun top() = compose.onNodeWithTag("e").getBoundsInRoot().top.value

    @Test
    fun anEntranceParkedMidRiseComesBackAtRest() {
        var active by mutableStateOf(true)
        val seen = mutableStateOf(false)
        show { ReusableContentHost(active) { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) } }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(top() > 52f, "mid-rise when parked, top was ${top()}")
        compose.runOnUiThread { active = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { active = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(50f, top(), 0.5f)
    }

    @Test
    fun aNodeHandedToAnotherItemPlaysThatItemsEntrance() {
        var active by mutableStateOf(true)
        var seen by mutableStateOf(mutableStateOf(false))
        show { ReusableContentHost(active) { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) } }
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnUiThread { active = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            seen = mutableStateOf(false)
            active = true
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(top() > 55f, "the other item rises in from below, top was ${top()}")
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests 'designsystem.MotionModifiersTest' --tests 'designsystem.EntranceReuseTest'`
Expected: compilation fails with unresolved references `entrance`, `drift`, `hoverMotion`, `LocalReducedMotion`, `Motion`.

- [ ] **Step 4: Write `Motion.kt`**

```kotlin
package designsystem

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The web player's motion tokens (`web/public/styles/theme.css:120-122`),
 * so a fade here runs on the same curve and clock as the same fade there.
 * The two CSS keyword curves are the ones the web's player controls and
 * Featured reel use instead of a token; naming them keeps those two places
 * from guessing at `ease`.
 */
object Motion {
    /** `--ease`: standard motion, a settling spring. */
    val Ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** `--ease-out`: the cover's slow zoom. */
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** CSS `ease`: the player's controls fading, the Featured reel's turn. */
    val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** CSS `ease-out`: the Featured poster's drift and its copy rising. */
    val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** `--hover`: a change of colour, border or background, never of size. */
    const val HOVER_MS = 160

    fun <T> hover(): TweenSpec<T> = tween(HOVER_MS, easing = Ease)
}

/**
 * Whether the system asks for no animation, which is the web's
 * `prefers-reduced-motion`: Android says so by turning animator durations
 * off. Read once, not observed. Compose's own tweens already follow the
 * setting live, and this only steers the few places whose end state is not
 * their rest (a drift, a timer), for a setting viewers rarely flip.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * [rememberReducedMotion], read once by the theme so the motion nodes can
 * reach it with `currentValueOf` and tests can set it outright.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }
```

- [ ] **Step 5: Write `Entrance.kt`**

```kotlin
package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A one-shot entrance from (alpha 0, [fromY] below, [fromScale]) to rest,
 * the shape of every web entrance: the page turn (`shell.css:161-163`), the
 * scroll reveal (`theme.css:206-209`), the art-in (`title-page.css:63`), the
 * menu (`title-page.css:46`). Position and scale run [durationMs], the fade
 * [fadeMs]; the web's reveal fades faster than it rises.
 *
 * It starts when first **placed**, not composed, so a lazy list composing an
 * item ahead of the viewport doesn't play it off screen. [delayMs] is part of
 * the tween, so "Remove animations" zeroes it with everything else. With
 * [seen] already true it starts at rest: the web's rule that a redraw or a
 * return never replays an entrance (`catalog.css:136`, `.settled`).
 *
 * [seen] is the same state for the same item from one composition to the
 * next. A different one means a different item, which a lazy list can hand
 * this node to, and the entrance starts over for it.
 */
fun Modifier.entrance(
    durationMs: Int,
    delayMs: Int = 0,
    fromY: Dp = 0.dp,
    fromScale: Float = 1f,
    fadeMs: Int = durationMs,
    easing: Easing = Motion.Ease,
    seen: MutableState<Boolean>? = null,
): Modifier = this then EntranceElement(durationMs, delayMs, fromY, fromScale, fadeMs, easing, seen)

private data class EntranceElement(
    val durationMs: Int,
    val delayMs: Int,
    val fromY: Dp,
    val fromScale: Float,
    val fadeMs: Int,
    val easing: Easing,
    val seen: MutableState<Boolean>?,
) : ModifierNodeElement<EntranceNode>() {
    override fun create() = EntranceNode(this)

    override fun update(node: EntranceNode) {
        node.spec = this
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "entrance"
    }
}

private class EntranceNode(spec: EntranceElement) : Modifier.Node(), LayoutModifierNode, LayoutAwareModifierNode {
    private var fade = Animatable(0f)
    private var move = Animatable(0f)
    private var started = false

    var spec: EntranceElement = spec
        set(value) {
            val anotherItem = value.seen !== field.seen
            field = value
            if (anotherItem) startOver()
        }

    init {
        startOver()
    }

    /** From the start, or straight at rest where [EntranceElement.seen] says this item has played. */
    private fun startOver() {
        started = spec.seen?.value == true
        val from = if (started) 1f else 0f
        fade = Animatable(from)
        move = Animatable(from)
    }

    // A lazy list parks an item scrolled out of view and hands its nodes
    // back when it returns, with the animation cancelled wherever it had got
    // to. Placed once, it has played: finish it, as a revealed web section
    // stays in, rather than leave it half-faded. The reuse remeasures this
    // node, so the next placement reads the new values.
    override fun onReset() {
        if (!started) return
        fade = Animatable(1f)
        move = Animatable(1f)
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        if (started) return
        started = true
        spec.seen?.value = true
        coroutineScope.launch { fade.animateTo(1f, tween(spec.fadeMs, spec.delayMs, spec.easing)) }
        coroutineScope.launch { move.animateTo(1f, tween(spec.durationMs, spec.delayMs, spec.easing)) }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                alpha = fade.value
                val moved = move.value
                translationY = spec.fromY.toPx() * (1f - moved)
                val scale = spec.fromScale + (1f - spec.fromScale) * moved
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
```

- [ ] **Step 6: Write `Drift.kt`**

```kotlin
package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A one-shot slow scale (and pan) from [from] to [to], the web's cover
 * zoom (`home.css:27,128`) and Featured poster drift (`playback.css:499-503`).
 * [translate] is a fraction of the size, as CSS `translate(-1.5%, -1%)` is.
 *
 * Paused ([running] false) it holds where it is and resumes from there on
 * the same curve, as CSS `animation-play-state: paused` does: the clock runs
 * linear and [easing] is applied when drawn. Under reduced motion it shows
 * [restScale] and never ticks, which is the web's `animation: none` (for the
 * reel that rest is 1, not the drift's end).
 */
fun Modifier.drift(
    from: Float,
    to: Float,
    durationMs: Int,
    easing: Easing,
    running: Boolean = true,
    translate: Offset = Offset.Zero,
    restScale: Float = to,
): Modifier = this then DriftElement(from, to, durationMs, easing, running, translate, restScale)

private data class DriftElement(
    val from: Float,
    val to: Float,
    val durationMs: Int,
    val easing: Easing,
    val running: Boolean,
    val translate: Offset,
    val restScale: Float,
) : ModifierNodeElement<DriftNode>() {
    override fun create() = DriftNode(this)

    override fun update(node: DriftNode) {
        node.spec = this
        node.sync()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drift"
    }
}

private class DriftNode(var spec: DriftElement) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    private val clock = Animatable(0f)
    private var job: Job? = null
    private var still = false

    override fun onAttach() {
        still = currentValueOf(LocalReducedMotion)
        sync()
    }

    fun sync() {
        if (!isAttached || still) return
        if (!spec.running) {
            job?.cancel()
            job = null
        } else if (job?.isActive != true && clock.value < 1f) {
            val left = ((1f - clock.value) * spec.durationMs).roundToInt()
            job = coroutineScope.launch { clock.animateTo(1f, tween(left, easing = LinearEasing)) }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                if (still) {
                    scaleX = spec.restScale
                    scaleY = spec.restScale
                } else {
                    val eased = spec.easing.transform(clock.value)
                    val scale = spec.from + (spec.to - spec.from) * eased
                    scaleX = scale
                    scaleY = scale
                    translationX = spec.translate.x * size.width * eased
                    translationY = spec.translate.y * size.height * eased
                }
            }
        }
    }
}
```

- [ ] **Step 7: Write `HoverMotion.kt`**

```kotlin
package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The web's hover motion: artwork lifts or zooms, arrows nudge toward where
 * they lead (`catalog.css:44-51`, `home.css:157-160,208-210`,
 * `title-page.css:26-27,84-86,178-180`). Controls never lift or grow, so
 * this goes on artwork and arrow glyphs only.
 *
 * It follows a mouse, trackpad or hovering stylus ([HoverInteraction]), the
 * web's `(hover: hover)` gate. A finger never lifts or zooms artwork; a
 * zoom still running after the tap navigated would be motion for nobody.
 * An arrow ([nudgeOnPress]) also nudges while pressed: a press is the touch
 * form of a hover, and the nudge is a control-level cue.
 */
fun Modifier.hoverMotion(
    source: InteractionSource,
    durationMs: Int,
    lift: Dp = 0.dp,
    nudge: Dp = 0.dp,
    zoom: Float = 1f,
    nudgeOnPress: Boolean = nudge != 0.dp,
): Modifier = this then HoverMotionElement(source, durationMs, lift, nudge, zoom, nudgeOnPress)

private data class HoverMotionElement(
    val source: InteractionSource,
    val durationMs: Int,
    val lift: Dp,
    val nudge: Dp,
    val zoom: Float,
    val nudgeOnPress: Boolean,
) : ModifierNodeElement<HoverMotionNode>() {
    override fun create() = HoverMotionNode(this)

    override fun update(node: HoverMotionNode) {
        val sourceChanged = node.spec.source != source
        node.spec = this
        if (sourceChanged) node.listen()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "hoverMotion"
    }
}

private class HoverMotionNode(var spec: HoverMotionElement) : Modifier.Node(), LayoutModifierNode {
    private val progress = Animatable(0f)
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private val presses = mutableSetOf<PressInteraction.Press>()
    private var listening: Job? = null

    override fun onAttach() = listen()

    fun listen() {
        if (!isAttached) return
        listening?.cancel()
        hovers.clear()
        presses.clear()
        listening =
            coroutineScope.launch {
                spec.source.interactions.collect { interaction ->
                    when (interaction) {
                        is HoverInteraction.Enter -> hovers += interaction
                        is HoverInteraction.Exit -> hovers -= interaction.enter
                        is PressInteraction.Press -> presses += interaction
                        is PressInteraction.Release -> presses -= interaction.press
                        is PressInteraction.Cancel -> presses -= interaction.press
                    }
                    val on = hovers.isNotEmpty() || (spec.nudgeOnPress && presses.isNotEmpty())
                    launch { progress.animateTo(if (on) 1f else 0f, tween(spec.durationMs, easing = Motion.Ease)) }
                }
            }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val p = progress.value
                translationY = spec.lift.toPx() * p
                translationX = spec.nudge.toPx() * p
                val scale = 1f + (spec.zoom - 1f) * p
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests 'designsystem.MotionModifiersTest' --tests 'designsystem.EntranceReuseTest'`
Expected: PASS, 12 tests.

- **If a hover or press test reads rest when it should have moved:** the emission was dropped because the node's collector had not subscribed yet. `tryEmit` on a `MutableInteractionSource` with no collector drops the event. Add one more `compose.mainClock.advanceTimeByFrame()` at the end of `show()`, not a sleep.
- **If `getBoundsInRoot` ignores the layer:** the tag must come after the motion modifier in the chain, which every test above already does. Report it as BLOCKED rather than switching to `Modifier.offset`.

- [ ] **Step 9: Lint and commit**

```bash
./gradlew :core:designsystem:spotlessApply :core:designsystem:detekt
git add core/designsystem
git commit -m "feat(motion): the web's motion tokens and its entrance, drift and hover helpers"
```

## Task 1.2: The catalogue indication

**Files:**
- Create: `core/designsystem/src/main/kotlin/CatalogueIndication.kt`
- Test: `core/designsystem/src/test/kotlin/CatalogueIndicationTest.kt`

**Interfaces (produced):**
```kotlin
data class IndicationTone(val ink: Color, val ring: Color)
val LocalIndicationTone: ProvidableCompositionLocal<IndicationTone>  // default: Palette.Text ink, the default accent's dark ring
fun catalogueIndication(pressScale: Float = 0.98f): IndicationNodeFactory  // equal for equal pressScale
```

- [ ] **Step 1: Write the failing tests**

Create `core/designsystem/src/test/kotlin/CatalogueIndicationTest.kt`:

```kotlin
package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The tint, the ring and the 0.98 snap are drawn, and this project's
 * Robolectric setup cannot capture pixels (see PageGroundColorTest in
 * ui-mobile), so those are checked on the device. What is checked here:
 * the factory is stable, so a recomposition never rebuilds every
 * clickable's node, and a clickable drawing it still takes its click.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueIndicationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theFactoryIsEqualForEqualPressScales() {
        assertEquals(catalogueIndication(), catalogueIndication())
        assertNotEquals(catalogueIndication(), catalogueIndication(pressScale = 1f))
    }

    @Test
    fun aClickableDrawingItStillTakesItsClickThroughHoverAndPress() {
        var clicks = 0
        val source = MutableInteractionSource()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                Box(
                    Modifier
                        .size(48.dp)
                        .testTag("c")
                        .clickable(interactionSource = source, indication = catalogueIndication()) { clicks++ },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { source.tryEmit(HoverInteraction.Enter()) }
        compose.onNodeWithTag("c").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests 'designsystem.CatalogueIndicationTest'`
Expected: compilation fails, `catalogueIndication` unresolved.

- [ ] **Step 3: Write `CatalogueIndication.kt`**

```kotlin
package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** What [catalogueIndication] draws in: the tint's ink, the focus ring's accent. */
data class IndicationTone(val ink: Color, val ring: Color)

/** Provided by the theme; the player provides white ink over video. */
val LocalIndicationTone = staticCompositionLocalOf { IndicationTone(ink = Palette.Text, ring = Accent.Default.resolve(dark = true)) }

/** The web's hover fills sit at 8–10% of the ink (`player-card.css:79-82`). */
private const val INK_ALPHA = 0.08f
private val RING = 2.dp

/**
 * The web's feedback in place of Material's ripple, which the web turns off
 * (`theme.css:164`, no tap highlight):
 * - a quiet ink tint while pressed, hovered or focused, fading on `--hover`;
 * - a 2dp accent ring on focus, at once (`theme.css:170`). Only a keyboard or
 *   D-pad gives a clickable focus, so this is the web's `:focus-visible`;
 * - a snapped shrink to [pressScale] while pressed (`theme.css:167`,
 *   `button:active`). Links on the web don't shrink, so link-type sites
 *   pass 1.
 */
fun catalogueIndication(pressScale: Float = 0.98f): IndicationNodeFactory = CatalogueIndication(pressScale)

private data class CatalogueIndication(val pressScale: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = CatalogueIndicationNode(interactionSource, pressScale)
}

private class CatalogueIndicationNode(
    private val source: InteractionSource,
    private val pressScale: Float,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val tint = Animatable(0f)
    private val presses = mutableSetOf<PressInteraction.Press>()
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private val focuses = mutableSetOf<FocusInteraction.Focus>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    is HoverInteraction.Enter -> hovers += interaction
                    is HoverInteraction.Exit -> hovers -= interaction.enter
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                }
                val lit = presses.isNotEmpty() || hovers.isNotEmpty() || focuses.isNotEmpty()
                launch { tint.animateTo(if (lit) INK_ALPHA else 0f, Motion.hover()) }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val tone = currentValueOf(LocalIndicationTone)
        val shrink = presses.isNotEmpty() && pressScale != 1f
        if (shrink) {
            scale(pressScale) {
                this@draw.drawContent()
                drawRect(tone.ink.copy(alpha = tint.value))
            }
        } else {
            drawContent()
            drawRect(tone.ink.copy(alpha = tint.value))
        }
        // ponytail: drawn on the bounds, not 4dp outside them as the web's
        // outline-offset is; a clipped control would cut an outside ring off.
        if (focuses.isNotEmpty()) drawRect(tone.ring, style = Stroke(RING.toPx()))
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS, every designsystem test including the 14 new ones.

- [ ] **Step 5: Lint and commit**

```bash
./gradlew :core:designsystem:spotlessApply :core:designsystem:detekt
git add core/designsystem
git commit -m "feat(motion): the catalogue indication, the web's tint, ring and press snap"
```

## Success criteria

- All 14 new tests pass, and `:core:designsystem:testDebugUnitTest` stays green.
- No file in this phase imports material3, and each is under 200 lines.
- Nothing calls these helpers yet. Phase 02 wires them into the theme.
