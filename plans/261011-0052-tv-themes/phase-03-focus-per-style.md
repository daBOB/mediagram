# Phase 03 — Focus per style

## Context
- Spec: § Focus, and the look table's "Card focus" and "Focused tab / button" rows.
- `ui-tv/src/main/kotlin/ui/tv/TvFocus.kt` (204 lines): every card and surface takes its shape, scale, border and glow from here. Its private `Shape = RectangleShape` (line 81) is the default every call site relies on.
- tv-material keeps `CardBorder`/`CardGlow`/`ClickableSurfaceColors` internals private, so the per-style values are computed by small plain functions that tests can call, and `TvFocus` wraps them.
- Pills: `chrome/TvChromeControls.kt` (`TvPill`, `TvRoundIconButton`, `TvAvatar`) and `catalog/home/TvCoverActions.kt` (`TvCoverPill`). `TvRoundIconButton` tints its icon with the fixed `ink` and `TvCoverPill` sets `color = ink` on its text — both must read the surface's content colour instead, or a white focus fill keeps light ink on it.

## Task 3.1: `TvFocus` reads the style

**Files:**
- Modify: `ui-tv/src/main/kotlin/ui/tv/TvFocus.kt`
- Create: `ui-tv/src/main/kotlin/ui/tv/TvPillFocus.kt`
- Modify: `ui-tv/src/main/kotlin/ui/tv/chrome/TvChromeControls.kt`, `ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCoverActions.kt:89-115`
- Test: `ui-tv/src/test/kotlin/ui/tv/TvFocusStylesTest.kt` (create)

**Interfaces:**
- Consumes: `currentTvLook`, `TvLook`, `TvCardFocus`, `TvStyle.look` (1.2).
- Produces: `internal fun focusedCardBorderOf(look: TvLook, accent: Color, shape: Shape): Border`; `internal fun focusedCardGlowOf(look: TvLook): Glow?`; `internal fun focusInkOf(look: TvLook, accent: Color): Color`; `@Composable internal fun TvFocus.pillColors(container: Color, content: Color, pressed: Color = container): ClickableSurfaceColors`; `@Composable internal fun TvFocus.pillBorder(): ClickableSurfaceBorder`; `internal fun pillFocusedColoursOf(look: TvLook, container: Color, content: Color): Pair<Color, Color>`; `internal val FocusFill`, `internal val FocusFillInk`. Every existing `TvFocus` function keeps its signature; their defaults now follow the style.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import designsystem.Accent
import designsystem.TvStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a focused card or pill draws in each style; mediagram's are today's. */
class TvFocusStylesTest {
    private val accent = Accent.Default.dark

    @Test
    fun mediagramCardsTakeTheAccentBorderAndNoGlow() {
        val border = focusedCardBorderOf(TvStyle.MEDIAGRAM.look, accent, RectangleShape)
        assertEquals(accent, border.border.brush.let { (it as androidx.compose.ui.graphics.SolidColor).value })
        assertEquals(TvFocus.BorderWidth, border.border.width)
        assertNull(focusedCardGlowOf(TvStyle.MEDIAGRAM.look))
    }

    @Test
    fun appleCardsLiftWithAShadowAndNoBorder() {
        assertEquals(Border.None, focusedCardBorderOf(TvStyle.APPLE.look, accent, RoundedCornerShape(12.dp)))
        val glow = checkNotNull(focusedCardGlowOf(TvStyle.APPLE.look))
        assertEquals(16.dp, glow.elevation)
        assertEquals(Color.Black.copy(alpha = 0.6f), glow.elevationColor)
    }

    @Test
    fun googleCardsTakeAWhiteRingAndNoGlow() {
        val shape = RoundedCornerShape(16.dp)
        val border = focusedCardBorderOf(TvStyle.GOOGLE.look, accent, shape)
        assertEquals(Color.White, (border.border.brush as androidx.compose.ui.graphics.SolidColor).value)
        assertEquals(shape, border.shape)
        assertNull(focusedCardGlowOf(TvStyle.GOOGLE.look))
    }

    @Test
    fun surfacesRingInTheAccentForMediagramAndWhiteOtherwise() {
        assertEquals(accent, focusInkOf(TvStyle.MEDIAGRAM.look, accent))
        assertEquals(Color.White, focusInkOf(TvStyle.APPLE.look, accent))
        assertEquals(Color.White, focusInkOf(TvStyle.GOOGLE.look, accent))
    }

    @Test
    fun aFocusedPillFillsWhiteWithDarkInkOnlyWhereTheStyleSaysSo() {
        val rest = Color(0x1FFFFFFF) to Color(0xFFF6F2EA)
        assertEquals(rest, pillFocusedColoursOf(TvStyle.MEDIAGRAM.look, rest.first, rest.second))
        assertEquals(FocusFill to FocusFillInk, pillFocusedColoursOf(TvStyle.APPLE.look, rest.first, rest.second))
        assertEquals(FocusFill to FocusFillInk, pillFocusedColoursOf(TvStyle.GOOGLE.look, rest.first, rest.second))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.TvFocusStylesTest'`
Expected: compilation fails — the five functions and two colours are unresolved.

- [ ] **Step 3: Implement in `TvFocus.kt`**

Add the plain functions above the object (imports `androidx.compose.runtime.ReadOnlyComposable`, `androidx.compose.ui.graphics.Color`, `androidx.tv.material3.Glow`):

```kotlin
/** A focused card's border in [look]: the accent (mediagram), a white ring (Google TV), or none (Apple TV lifts instead). */
internal fun focusedCardBorderOf(look: TvLook, accent: Color, shape: Shape): Border =
    when (look.cardFocus) {
        TvCardFocus.ACCENT_BORDER -> Border(border = BorderStroke(TvFocus.BorderWidth, accent), shape = shape)
        TvCardFocus.RING -> Border(border = BorderStroke(TvFocus.BorderWidth, Color.White), shape = shape)
        TvCardFocus.LIFT -> Border.None
    }

/** Apple TV style's lift: a soft shadow under the grown card. Null — no glow — in every other style. */
internal fun focusedCardGlowOf(look: TvLook): Glow? =
    if (look.cardFocus == TvCardFocus.LIFT) Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 16.dp) else null

/** The ring a focused plain surface (a swatch, a settings block, a dot) wears: the accent for mediagram, white otherwise. */
internal fun focusInkOf(look: TvLook, accent: Color): Color = if (look.cardFocus == TvCardFocus.ACCENT_BORDER) accent else Color.White
```

Inside `object TvFocus`:
- delete `private val Shape: Shape = RectangleShape`, and add instead
  ```kotlin
      /** Every TV container's corner in the current style — square for mediagram, like the web player's plates. */
      private val Shape: Shape
          @Composable
          @ReadOnlyComposable
          get() = currentTvLook.cardShape
  ```
  (the default parameters `shape: Shape = Shape` keep working: a composable function's defaults are read in composition);
- `cardScale()` and `surfaceScale()` use `currentTvLook.focusScale` for both `focusedScale` and `pressedScale`;
- `cardBorder(shape)` returns `CardDefaults.border(focusedBorder = focusedCardBorderOf(currentTvLook, MaterialTheme.colorScheme.primary, shape))`;
- `cardGlow()` returns `focusedCardGlowOf(currentTvLook)?.let { CardDefaults.glow(focusedGlow = it, pressedGlow = it) } ?: CardDefaults.glow()`;
- in `surfaceBorder`, `focusedBorder`'s colour `MaterialTheme.colorScheme.primary` becomes `focusInkOf(currentTvLook, MaterialTheme.colorScheme.primary)`;
- `fieldBorder` and `textStyle` keep the accent.
- KDoc: the object's "Square corners…" and "No glow, anywhere…" paragraphs gain one closing sentence each: "That is mediagram's look; Apple TV and Google TV styles round the corner ([TvLook.cardShape])." / "Apple TV style is the one exception: its focused card lifts on a soft shadow, the way that home screen says focus." The `Shape` KDoc moves to the getter as written above.

- [ ] **Step 4: Create `TvPillFocus.kt`**

```kotlin
package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults

/** The fill a focused pill or button takes in Apple TV and Google TV styles, and the ink set on it. */
internal val FocusFill = Color(0xFFFFFFFF)
internal val FocusFillInk = Color(0xFF0A0A0B)

/** A pill's focused fill and ink in [look]: white and dark where the style fills on focus, else its own rest colours. */
internal fun pillFocusedColoursOf(
    look: TvLook,
    container: Color,
    content: Color,
): Pair<Color, Color> = if (look.pillFocusFills) FocusFill to FocusFillInk else container to content

/**
 * A pill's colours: [container] and [content] at rest, [pressed] while
 * pressed; focused, [pillFocusedColoursOf]. Read the content colour through
 * `LocalContentColor` inside the pill, never a fixed ink, so the dark ink on
 * a white fill reaches the label and the icon.
 */
@Composable
internal fun TvFocus.pillColors(
    container: Color,
    content: Color,
    pressed: Color = container,
): ClickableSurfaceColors {
    val (focusedContainer, focusedContent) = pillFocusedColoursOf(currentTvLook, container, content)
    return ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = content,
        focusedContainerColor = focusedContainer,
        focusedContentColor = focusedContent,
        pressedContainerColor = if (currentTvLook.pillFocusFills) focusedContainer else pressed,
        pressedContentColor = focusedContent,
    )
}

/** A pill's border: mediagram's ring on focus; none where the fill says focus instead. */
@Composable
internal fun TvFocus.pillBorder(): ClickableSurfaceBorder =
    if (currentTvLook.pillFocusFills) ClickableSurfaceDefaults.border() else surfaceBorder(shape = PillShape)
```

- [ ] **Step 5: Move the pills onto it**

In `TvChromeControls.kt` (import `androidx.tv.material3.LocalContentColor`):
- `TvPill`: `colors = TvFocus.pillColors(container = fill, content = ink, pressed = ink.copy(alpha = 0.12f))`, `border = TvFocus.pillBorder()`.
- `TvRoundIconButton`: `colors = TvFocus.pillColors(container = Color.Transparent, content = ink)`, `border = TvFocus.pillBorder()`, and the icon's `colorFilter = ColorFilter.tint(LocalContentColor.current)`.
- `TvAvatar`: rest colours become a sunk circle where focus fills (the avatar's own light fill would hide a white focus fill):
  ```kotlin
      val fills = currentTvLook.pillFocusFills
      val container = if (fills) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground
      val content = if (fills) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background
  ```
  then `colors = TvFocus.pillColors(container = container, content = content)`, `border = TvFocus.pillBorder()`.

In `TvCoverActions.kt`, `TvCoverPill`:
- `colors = TvFocus.pillColors(container = if (filled) OnImage else Color(0x47080809), content = ink)`, `border = TvFocus.pillBorder()`;
- its `Text`'s style drops `color = ink` (`TvType.bodyMedium.copy(fontWeight = FontWeight.Medium)`), so it takes the surface's content colour. `TvCoverDot` keeps `surfaceBorder`.

Each composable's KDoc that says "the accent ring only while focused" gains: "— or, in Apple TV and Google TV styles, a white fill with dark ink ([TvFocus.pillColors])."

- [ ] **Step 6: Run the ui-tv suite**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS — the new test, and every existing focus, bar and cover test under mediagram (`TvFocus.Scale`, `BorderWidth`, square defaults unchanged).

- [ ] **Step 7: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "feat(tv): focus lifts, rings or fills the way each TV style shows it"
```

## Success criteria
- mediagram: accent border, square, 1.08, no glow — unchanged.
- Apple: rounded 12, no border, 1.10, shadow; pills fill white. Google: rounded 16, white ring, 1.05; pills fill white.
- Icon and label ink follow the pill's focused content colour.
