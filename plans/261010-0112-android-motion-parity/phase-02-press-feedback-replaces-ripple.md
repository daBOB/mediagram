# Phase 02 — Press feedback replaces ripple

**Spec:** `../spec.md` §1 (Wiring), §2 (Everywhere: taps and clicks). **Status:** pending. **Depends on:** 01.

## Context

- Inventory §4: `MediagramTheme` never overrides `LocalIndication`, so the ~60 foundation `.clickable` and `.selectable` sites ripple.
- **M3 owns `LocalIndication`.** `MaterialTheme` provides `LocalIndication` as ripple, so the override has to sit **inside** `MaterialTheme`'s content.
- **M3 widgets hard-code ripple.** Verified in material3 1.4.0. `LocalRippleConfiguration provides null` silences them; it doesn't replace them. Each M3 button therefore gets a wrapper that draws the theme's indication on its own interaction source.
- **The M3 buttons in `ui-mobile/src/main`:** 52 `TextButton`, 5 `Button`, 4 `OutlinedButton` and 6 `IconButton`, across 38 files.
  - Only `ui/settings/SettingsControls.kt` passes `shape`, `border`, `colors` or `contentPadding`, on its two `OutlinedButton`s.
  - Elsewhere the only parameter besides onClick, modifier and content is `enabled`.
- **Material 1.4 signatures (from the cached jar):**
  - `TextButton`, `Button` and `OutlinedButton` take `(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)`;
  - `IconButton` takes `(onClick, modifier, enabled, colors, interactionSource, shape, content)`;
  - `ButtonDefaults.textShape`, `.shape`, `.outlinedShape` and `.outlinedButtonBorder(enabled)` are composable getters.
- **Left silent on purpose** (the web's native inputs have no hover motion either): `RadioButton`, `Switch`, `Slider` and `Checkbox` lose Material's halo and keep their state change. `Tab` and `DropdownMenuItem` are replaced in phase 07 and are silent until then.

## Task 2.1: The theme turns ripple off and provides the indication

**Files:**
- Modify: `core/designsystem/src/main/kotlin/Theme.kt` (the `MediagramTheme` body)
- Test: `core/designsystem/src/test/kotlin/ThemeMotionLocalsTest.kt`

**Interfaces:**
- **Consumes:** `catalogueIndication`, `IndicationTone`, `LocalIndicationTone`, `LocalReducedMotion` and `rememberReducedMotion` (phase 01).
- **Produces:** under `MediagramTheme`, five locals:

  | Local | Value |
  |---|---|
  | `LocalIndication.current` | `catalogueIndication()` |
  | `LocalRippleConfiguration.current` | `null` |
  | `LocalIndicationTone.current` | `IndicationTone(ink = colorScheme.onSurface, ring = accent)` |
  | `LocalReducedMotion.current` | the system setting |

- [ ] **Step 1: Write the failing test**

```kotlin
package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeMotionLocalsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theThemeAnswersPressesTheWebsWayAndNeverWithRipple() {
        var indication: Indication? = null
        var ripple: RippleConfiguration? = RippleConfiguration()
        var tone: IndicationTone? = null
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme(appearance = Appearance(theme = ThemeChoice.LIGHT, accent = Accent.TEAL)) {
                    indication = LocalIndication.current
                    ripple = LocalRippleConfiguration.current
                    tone = LocalIndicationTone.current
                }
            }
        }
        compose.waitForIdle()
        assertEquals(catalogueIndication(), indication)
        assertNull(ripple)
        assertEquals(IndicationTone(ink = Palette.LightText, ring = Accent.TEAL.light), tone)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests 'designsystem.ThemeMotionLocalsTest'`
Expected: FAIL. `indication` is Material's ripple, not `catalogueIndication()`.

- [ ] **Step 3: Provide the locals in `MediagramTheme`**

In `Theme.kt`, replace the `CompositionLocalProvider(…) { MaterialTheme(…) }` block at the end of `MediagramTheme` with:

```kotlin
    val scheme = catalogueColorScheme(dark, accentColor)
    CompositionLocalProvider(
        LocalCatalogueTones provides if (dark) DarkTones else LightTones,
        LocalBackdrop provides appearance.backdrop,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CatalogueTypography,
            shapes = CatalogueShapes,
        ) {
            // Inside MaterialTheme, which provides ripple as LocalIndication: the
            // web answers a press with a quiet tint and a 0.98 snap, never a ripple
            // (theme.css:164-167). Material's own widgets hard-code their ripple,
            // so turning its configuration off silences them; ui.chrome's
            // Catalogue*Button wrappers draw this indication for them instead.
            CompositionLocalProvider(
                LocalIndication provides catalogueIndication(),
                LocalRippleConfiguration provides null,
                LocalIndicationTone provides IndicationTone(ink = scheme.onSurface, ring = accentColor),
                content = content,
            )
        }
    }
```

Add the imports `androidx.compose.foundation.LocalIndication` and `androidx.compose.material3.LocalRippleConfiguration`.

- [ ] **Step 4: Run the test to see it pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :core:designsystem:spotlessApply :core:designsystem:detekt
git add core/designsystem
git commit -m "feat(motion): the phone theme answers presses with the web's feedback, not ripple"
```

## Task 2.2: Material buttons draw the catalogue indication

**Files:**
- Create: `ui-mobile/src/main/kotlin/ui/chrome/CatalogueButtons.kt`
- Modify: the 38 files in `ui-mobile/src/main/kotlin` that import a material3 `TextButton`, `Button`, `OutlinedButton` or `IconButton`. The script in Step 4 finds and rewrites them.
- Test: `ui-mobile/src/test/kotlin/ui/chrome/CatalogueButtonsTest.kt`

**Interfaces (produced, used by phases 06–08):**
```kotlin
@Composable fun CatalogueTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit)
@Composable fun CatalogueButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit)
@Composable fun CatalogueOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled), contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit)
@Composable fun CatalogueIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit)
```

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/chrome/CatalogueButtonsTest.kt`:

```kotlin
package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import designsystem.MediagramTheme
import java.io.File
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueButtonsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun eachWrapperStillTakesItsClick() {
        val clicks = mutableListOf<String>()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme {
                    androidx.compose.foundation.layout.Column {
                        CatalogueTextButton(onClick = { clicks += "text" }) { Text("text") }
                        CatalogueButton(onClick = { clicks += "filled" }) { Text("filled") }
                        CatalogueOutlinedButton(onClick = { clicks += "outlined" }) { Text("outlined") }
                        CatalogueIconButton(onClick = { clicks += "icon" }) { Text("icon") }
                    }
                }
            }
        }
        listOf("text", "filled", "outlined", "icon").forEach { compose.onNodeWithText(it).performClick() }
        compose.waitForIdle()
        assertEquals(listOf("text", "filled", "outlined", "icon"), clicks)
    }

    /**
     * Under MediagramTheme a bare Material button answers a press with
     * nothing: the theme turns ripple off, and Material's buttons never read
     * LocalIndication. A new one slipping in would be a silent control.
     */
    @Test
    fun noScreenUsesABareMaterialButton() {
        val bare = Regex("""^import androidx\.compose\.material3\.(TextButton|Button|OutlinedButton|IconButton)$""", RegexOption.MULTILINE)
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "CatalogueButtons.kt" }
                .filter { bare.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "use ui.chrome's Catalogue*Button instead in: $offenders")
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.chrome.CatalogueButtonsTest'`
Expected: compilation fails, `CatalogueTextButton` unresolved.

- [ ] **Step 3: Write `CatalogueButtons.kt`**

```kotlin
package ui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/*
 * Material's buttons, answering a press the web's way. MediagramTheme turns
 * ripple off (the web sets no tap highlight and answers with a quiet tint and
 * a 0.98 snap, theme.css:164-167), and a Material button hard-codes its ripple
 * instead of reading LocalIndication, so a bare one would answer with nothing.
 * Each wrapper draws the theme's indication over its own shape, on the
 * interaction source it hands the button.
 */

@Composable
fun CatalogueTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, ButtonDefaults.textShape, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun CatalogueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, ButtonDefaults.shape, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun CatalogueOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, shape, LocalIndication.current),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun CatalogueIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, CircleShape, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

private fun Modifier.pressFeedback(source: InteractionSource, shape: Shape, indication: Indication): Modifier =
    clip(shape).indication(source, indication)
```

- [ ] **Step 4: Rewrite every site with one script**

Run from the worktree's `android/` directory:

```bash
cd ui-mobile/src/main/kotlin
for f in $(grep -rlE '^import androidx\.compose\.material3\.(TextButton|Button|OutlinedButton|IconButton)$' . | grep -v 'ui/chrome/CatalogueButtons.kt'); do
  perl -0pi -e '
    s/^import androidx\.compose\.material3\.TextButton$/import ui.chrome.CatalogueTextButton/m;
    s/^import androidx\.compose\.material3\.OutlinedButton$/import ui.chrome.CatalogueOutlinedButton/m;
    s/^import androidx\.compose\.material3\.IconButton$/import ui.chrome.CatalogueIconButton/m;
    s/^import androidx\.compose\.material3\.Button$/import ui.chrome.CatalogueButton/m;
    s/(?<![A-Za-z.])TextButton\(/CatalogueTextButton(/g;
    s/(?<![A-Za-z.])OutlinedButton\(/CatalogueOutlinedButton(/g;
    s/(?<![A-Za-z.])IconButton\(/CatalogueIconButton(/g;
    s/(?<![A-Za-z.])Button\(/CatalogueButton(/g;
  ' "$f"
done
# Files already in package ui.chrome need no import of their own package.
for f in $(grep -l '^package ui.chrome$' ui/chrome/*.kt); do
  perl -0pi -e 's/^import ui\.chrome\.Catalogue(Text|Outlined|Icon)?Button\n//mg' "$f"
done
cd -
```

Then:
- `git grep -n -E '(^|[^A-Za-z.])(TextButton|OutlinedButton|IconButton|Button)\(' -- 'ui-mobile/src/main/*.kt'` must print only the four calls inside `CatalogueButtons.kt`.
- `git diff --stat` should show the 38 files plus the new one.
- `ButtonDefaults` imports at the sites stay as they are.

- [ ] **Step 5: Compile, run the tests, and check them pass**

Run: `./gradlew :ui-mobile:spotlessApply :ui-mobile:compileDebugKotlin :ui-mobile:testDebugUnitTest`
Expected: compiles; every ui-mobile test passes, including both new ones.

**If a call site fails to compile because it passes a parameter the wrapper lacks,** add that one parameter to the wrapper with Material's own default and name. Don't revert the site to the bare button.

- [ ] **Step 6: Commit**

```bash
./gradlew :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): Material buttons answer presses with the catalogue indication"
```

## Success criteria

- Under `MediagramTheme`, no clickable in ui-mobile or ui-common ripples. Foundation clickables get the indication from `LocalIndication`, and M3 buttons from their wrapper.
- The guard test fails if a bare M3 button returns.
- `:core:designsystem` and `:ui-mobile` unit tests are green.
