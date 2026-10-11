# Phase 01 — Setting and look

## Context
- Spec: § Setting, § One look per theme.
- `core/designsystem/src/main/kotlin/Appearance.kt` (113 lines), `Backdrop.kt` (the pattern `TvStyle` copies), `feature/setup/src/main/kotlin/AppearanceViewModel.kt`.
- `ui-tv/src/main/kotlin/ui/tv/TvTheme.kt` (139), `TvApp.kt:50-52`, test base `ui-tv/src/test/kotlin/ui/tv/catalog/TvScreenStateTest.kt:53-60`.

## Task 1.1: The `TvStyle` setting

**Files:**
- Create: `core/designsystem/src/main/kotlin/TvStyle.kt`
- Modify: `core/designsystem/src/main/kotlin/Appearance.kt`
- Modify: `feature/setup/src/main/kotlin/AppearanceViewModel.kt`
- Test: `core/designsystem/src/test/kotlin/SharedPreferencesAppearanceSettingsTest.kt`, `feature/setup/src/test/kotlin/AppearanceViewModelTest.kt`

**Interfaces:**
- Produces: `enum class TvStyle(storageKey: String, label: String, note: String) { MEDIAGRAM, APPLE, GOOGLE }` with `TvStyle.Default` and `TvStyle.fromStorageKey(String?)`; `val LocalTvStyle: ProvidableCompositionLocal<TvStyle>`; `Appearance.style: TvStyle`; `AppearanceSettings.chooseStyle(TvStyle)`; `AppearanceViewModel.chooseStyle(TvStyle)`.

- [ ] **Step 1: Write the failing tests**

In `SharedPreferencesAppearanceSettingsTest`, add:

```kotlin
    @Test
    fun choosingATvStyleSurvivesAFreshInstance() {
        SharedPreferencesAppearanceSettings(context).chooseStyle(TvStyle.APPLE)

        val reopened = SharedPreferencesAppearanceSettings(context).appearance.value

        assertEquals(TvStyle.APPLE, reopened.style)
    }
```

and in `anUnrecognisedStoredValueFallsBackToTheDefault`, add `.putString("style", "sepia")` after the `backdrop` line (the test's `assertEquals(Appearance(), …)` then also pins the style's fallback).

In `AppearanceViewModelTest`, add (import `designsystem.TvStyle`):

```kotlin
    @Test
    fun choosingATvStyleReachesTheSharedSettings() {
        val settings = InMemoryAppearanceSettings()
        val model = AppearanceViewModel(settings)

        model.chooseStyle(TvStyle.GOOGLE)

        assertEquals(TvStyle.GOOGLE, model.state.value.style)
        assertEquals(TvStyle.GOOGLE, settings.appearance.value.style)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd android && ./gradlew :core:designsystem:testDebugUnitTest --tests 'designsystem.SharedPreferencesAppearanceSettingsTest' :feature:setup:testDebugUnitTest --tests 'setup.AppearanceViewModelTest'`
Expected: compilation fails — `TvStyle`, `chooseStyle` and `style` are unresolved.

- [ ] **Step 3: Implement**

Create `TvStyle.kt`:

```kotlin
package designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Settings › Appearance's television question: which of three looks the TV
 * draws in — mediagram's own magazine, or a look after Apple TV's or Google
 * TV's home screens. A property of this screen like the rest of
 * [Appearance]; the phone and tablet store it and ignore it until they get
 * themes of their own, a written difference in `docs/system-architecture.md`.
 */
enum class TvStyle(val storageKey: String, val label: String, val note: String) {
    MEDIAGRAM("mediagram", "mediagram", "Serif covers, posters, the rail"),
    APPLE("apple", "Apple TV style", "Black, bold titles, wide cards"),
    GOOGLE("google", "Google TV style", "Grey, rounded cards, a ring on focus"),
    ;

    companion object {
        val Default = MEDIAGRAM

        /** Anything unrecognised — a later version's value, a cleared preference — is Default, as [Backdrop] reads. */
        fun fromStorageKey(key: String?): TvStyle = entries.find { it.storageKey == key } ?: Default
    }
}

/**
 * The style every TV screen draws in, set once by `ui.tv.TvTheme` from
 * [Appearance.style] — static for [LocalBackdrop]'s reason: Settings is its
 * only writer, so the full recomposition a change triggers is correct.
 */
val LocalTvStyle = staticCompositionLocalOf { TvStyle.Default }
```

In `Appearance.kt`:
- the KDoc on `Appearance` becomes `/** This device's theme, accent, artwork mode and TV style together — Settings › Appearance's whole answer. */`, and the class gains a fourth property: `val style: TvStyle = TvStyle.Default,`
- `AppearanceSettings` gains `fun chooseStyle(style: TvStyle)`;
- `InMemoryAppearanceSettings` gains
  ```kotlin
      override fun chooseStyle(style: TvStyle) {
          _appearance.value = _appearance.value.copy(style = style)
      }
  ```
- `SharedPreferencesAppearanceSettings`: the initial `Appearance(...)` gains `style = TvStyle.fromStorageKey(preferences.getString(KEY_STYLE, null)),`; add
  ```kotlin
      override fun chooseStyle(style: TvStyle) {
          _appearance.value = _appearance.value.copy(style = style)
          preferences.edit().putString(KEY_STYLE, style.storageKey).apply()
      }
  ```
  and `const val KEY_STYLE = "style"` in its companion; the class KDoc's "a theme, an accent and a backdrop" becomes "a theme, an accent, a backdrop and a TV style".

In `AppearanceViewModel`, import `designsystem.TvStyle` and add `fun chooseStyle(style: TvStyle) = appearanceSettings.chooseStyle(style)`.

- [ ] **Step 4: Run the tests to see them pass**

Run the Step 2 command. Expected: PASS, including the untouched tests in both classes.

- [ ] **Step 5: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add core/designsystem/src/main/kotlin/TvStyle.kt core/designsystem/src/main/kotlin/Appearance.kt feature/setup/src/main/kotlin/AppearanceViewModel.kt core/designsystem/src/test/kotlin/SharedPreferencesAppearanceSettingsTest.kt feature/setup/src/test/kotlin/AppearanceViewModelTest.kt
git commit -m "feat(tv): a TV style setting beside theme, accent and artwork"
```

## Task 1.2: One look per style, provided by `TvTheme`

**Files:**
- Create: `ui-tv/src/main/kotlin/ui/tv/TvLook.kt`
- Modify: `ui-tv/src/main/kotlin/ui/tv/TvTheme.kt:41-72,103-123`, `ui-tv/src/main/kotlin/ui/tv/TvApp.kt:52`
- Modify (test base): `ui-tv/src/test/kotlin/ui/tv/catalog/TvScreenStateTest.kt:53-60`
- Test: `ui-tv/src/test/kotlin/ui/tv/TvLookTest.kt` (create), `ui-tv/src/test/kotlin/ui/tv/TvThemeTest.kt`

**Interfaces:**
- Consumes: `TvStyle`, `LocalTvStyle` (1.1).
- Produces: `internal data class TvNeutrals(ground, page, sunk, text, figures, rule, ruleStrong: Color)`; `internal enum class TvCardFocus { ACCENT_BORDER, LIFT, RING }`; `internal data class TvLook(neutrals, cardShape: Shape, cardFocus, focusScale: Float, pillFocusFills: Boolean, landscapeRows: Boolean)`; `internal val TvStyle.look: TvLook`; `internal val TvStyle.hasRail: Boolean`; `internal val currentTvLook: TvLook` (`@Composable @ReadOnlyComposable` getter); `internal fun tvColorScheme(accent: Color, neutrals: TvNeutrals = MediagramLook.neutrals)`; `TvTheme(accent, backdrop, style: TvStyle = TvStyle.Default, content)`; test base `show(style: TvStyle = TvStyle.Default, content)`.

- [ ] **Step 1: Write the failing tests**

Create `TvLookTest.kt`:

```kotlin
package ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import designsystem.Palette
import designsystem.TvStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each style's look carries the spec's values; mediagram's are today's, so nothing it draws moves. */
class TvLookTest {
    @Test
    fun mediagramKeepsTodaysSquareAccentFocusAndTheRail() {
        val look = TvStyle.MEDIAGRAM.look
        assertEquals(RectangleShape, look.cardShape)
        assertEquals(TvCardFocus.ACCENT_BORDER, look.cardFocus)
        assertEquals(TvFocus.Scale, look.focusScale)
        assertFalse(look.pillFocusFills)
        assertFalse(look.landscapeRows)
        assertEquals(Palette.Ground, look.neutrals.ground)
        assertTrue(TvStyle.MEDIAGRAM.hasRail)
    }

    @Test
    fun appleLiftsRoundedLandscapeCardsOnBlack() {
        val look = TvStyle.APPLE.look
        assertEquals(RoundedCornerShape(12.dp), look.cardShape)
        assertEquals(TvCardFocus.LIFT, look.cardFocus)
        assertEquals(1.10f, look.focusScale)
        assertTrue(look.pillFocusFills)
        assertTrue(look.landscapeRows)
        assertEquals(Color(0xFF000000), look.neutrals.ground)
        assertFalse(TvStyle.APPLE.hasRail)
    }

    @Test
    fun googleRingsRoundedLandscapeCardsOnDarkGrey() {
        val look = TvStyle.GOOGLE.look
        assertEquals(RoundedCornerShape(16.dp), look.cardShape)
        assertEquals(TvCardFocus.RING, look.cardFocus)
        assertEquals(1.05f, look.focusScale)
        assertTrue(look.pillFocusFills)
        assertTrue(look.landscapeRows)
        assertEquals(Color(0xFF202124), look.neutrals.ground)
        assertFalse(TvStyle.GOOGLE.hasRail)
    }
}
```

In `TvThemeTest`, give the private `show` a style (`style: TvStyle = TvStyle.Default` before `content`, passed as `TvTheme(accent = accent, style = style)`), import `designsystem.LocalTvStyle` and `designsystem.TvStyle`, and add:

```kotlin
    @Test
    fun appleAndGoogleSetTheirOwnNeutralsButKeepTheAccent() {
        val apple = tvColorScheme(Accent.Default.dark, TvStyle.APPLE.look.neutrals)
        assertEquals(Color(0xFF000000), apple.background)
        assertEquals(Color(0xFFFFFFFF), apple.onSurface)
        assertEquals(Color(0xFF1C1C1E), apple.surface)
        assertEquals(Accent.Default.dark, apple.primary)

        val google = tvColorScheme(Accent.Default.dark, TvStyle.GOOGLE.look.neutrals)
        assertEquals(Color(0xFF202124), google.background)
        assertEquals(Color(0xFFE8EAED), google.onSurface)
        assertEquals(Color(0xFF3C4043), google.surfaceVariant)
        assertEquals(Palette.Ochre, google.error)
    }

    @Test
    fun theThemeHandsItsStyleAndGroundToEveryScreen() {
        var style: TvStyle? = null
        var ground: Color? = null
        show(style = TvStyle.GOOGLE) {
            style = LocalTvStyle.current
            ground = MaterialTheme.colorScheme.background
        }
        assertEquals(TvStyle.GOOGLE, style)
        assertEquals(Color(0xFF202124), ground)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.TvLookTest' --tests 'ui.tv.TvThemeTest'`
Expected: compilation fails — `look`, `TvCardFocus`, `hasRail` and `TvTheme(style = …)` are unresolved.

- [ ] **Step 3: Implement**

Create `TvLook.kt`:

```kotlin
package ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import designsystem.LocalTvStyle
import designsystem.Palette
import designsystem.TvStyle

/**
 * The neutral roles a TV style sets its colour scheme from: the ground, the
 * plate a card sits on, the sunk fill, text, figures and the two rules. The
 * accent and the two status colours ([Palette.Sage], [Palette.Ochre]) are
 * the same in every style — the accent is the viewer's own choice.
 */
internal data class TvNeutrals(
    val ground: Color,
    val page: Color,
    val sunk: Color,
    val text: Color,
    val figures: Color,
    val rule: Color,
    val ruleStrong: Color,
)

/** How a focused card says so: mediagram's accent border, Apple TV's lift and shadow, Google TV's white ring. */
internal enum class TvCardFocus { ACCENT_BORDER, LIFT, RING }

/**
 * Every value a TV style varies besides its layout. Layout — which bar,
 * which cover — is picked by each seam from [TvStyle] directly, since only
 * the chrome, the Home cover and the row cards differ in structure at all.
 */
internal data class TvLook(
    val neutrals: TvNeutrals,
    val cardShape: Shape,
    val cardFocus: TvCardFocus,
    val focusScale: Float,
    /** A focused pill or button turns white with dark ink, rather than gaining a ring. */
    val pillFocusFills: Boolean,
    /** Rows of titles show 16:9 stills rather than posters; walls stay posters in every style. */
    val landscapeRows: Boolean,
)

internal val MediagramLook =
    TvLook(
        neutrals = TvNeutrals(Palette.Ground, Palette.Page, Palette.Sunk, Palette.Text, Palette.Figures, Palette.Rule, Palette.RuleStrong),
        cardShape = RectangleShape,
        cardFocus = TvCardFocus.ACCENT_BORDER,
        focusScale = TvFocus.Scale,
        pillFocusFills = false,
        landscapeRows = false,
    )

/** Apple's dark system greys (gray 6, 5, 4 and the secondary label) on black. */
internal val AppleLook =
    TvLook(
        neutrals =
            TvNeutrals(
                ground = Color(0xFF000000),
                page = Color(0xFF1C1C1E),
                sunk = Color(0xFF2C2C2E),
                text = Color(0xFFFFFFFF),
                figures = Color(0xFFA1A1A6),
                rule = Color(0xFF2C2C2E),
                ruleStrong = Color(0xFF3A3A3C),
            ),
        cardShape = RoundedCornerShape(12.dp),
        cardFocus = TvCardFocus.LIFT,
        focusScale = 1.10f,
        pillFocusFills = true,
        landscapeRows = true,
    )

/** Google's dark greys: grey 900 ground, its two surfaces, grey 200 text, grey 500 figures, grey 700 and 600 rules. */
internal val GoogleLook =
    TvLook(
        neutrals =
            TvNeutrals(
                ground = Color(0xFF202124),
                page = Color(0xFF303134),
                sunk = Color(0xFF3C4043),
                text = Color(0xFFE8EAED),
                figures = Color(0xFF9AA0A6),
                rule = Color(0xFF3C4043),
                ruleStrong = Color(0xFF5F6368),
            ),
        cardShape = RoundedCornerShape(16.dp),
        cardFocus = TvCardFocus.RING,
        focusScale = 1.05f,
        pillFocusFills = true,
        landscapeRows = true,
    )

internal val TvStyle.look: TvLook
    get() =
        when (this) {
            TvStyle.MEDIAGRAM -> MediagramLook
            TvStyle.APPLE -> AppleLook
            TvStyle.GOOGLE -> GoogleLook
        }

/** Only mediagram keeps the library rail; the other two styles put its rows behind the avatar. */
internal val TvStyle.hasRail: Boolean get() = this == TvStyle.MEDIAGRAM

/** The look every composable under [TvTheme] draws in. */
internal val currentTvLook: TvLook
    @Composable
    @ReadOnlyComposable
    get() = LocalTvStyle.current.look
```

In `TvTheme.kt`:
- `tvColorScheme(accent: Color)` becomes `tvColorScheme(accent: Color, neutrals: TvNeutrals = MediagramLook.neutrals)`. Inside it, every `Palette.Ground` becomes `neutrals.ground`, `Palette.Page` → `neutrals.page`, `Palette.Sunk` → `neutrals.sunk`, `Palette.Text` → `neutrals.text`, `Palette.Figures` → `neutrals.figures`, `Palette.Rule` → `neutrals.rule`, `Palette.RuleStrong` → `neutrals.ruleStrong`. `Palette.Sage` and `Palette.Ochre` stay. Its KDoc's last sentence becomes: "The colours handed to `darkColorScheme` below are [Palette]'s own values for mediagram — not a second set retyped for the television — and a style's [TvNeutrals] otherwise."
- `TvTheme` gains `style: TvStyle = TvStyle.Default` after `backdrop`; `CompositionLocalProvider` gains `LocalTvStyle provides style`; `colorScheme = tvColorScheme(accentColor, style.look.neutrals)`. Add to its KDoc: "[style] picks the TV's look ([TvLook]) and is provided as [LocalTvStyle] for the seams whose layout differs by style."
- Imports: `designsystem.LocalTvStyle`, `designsystem.TvStyle`.

In `TvApp.kt:52`: `TvTheme(accent = appearance.accent, backdrop = appearance.backdrop, style = appearance.style) {`.

In `TvScreenStateTest`, `show` becomes:

```kotlin
    protected fun show(
        style: TvStyle = TvStyle.Default,
        content: @Composable () -> Unit,
    ) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent { TvTheme(style = style) { content() } }
        }
        compose.waitForIdle()
    }
```

(import `designsystem.TvStyle`; every existing `show { … }` call still compiles).

- [ ] **Step 4: Run the tests to see them pass**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS — the new tests and every existing ui-tv test (mediagram's scheme is unchanged; `coreRolesComeFromThePalette` still holds).

- [ ] **Step 5: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src/main/kotlin/ui/tv/TvLook.kt ui-tv/src/main/kotlin/ui/tv/TvTheme.kt ui-tv/src/main/kotlin/ui/tv/TvApp.kt ui-tv/src/test/kotlin/ui/tv/TvLookTest.kt ui-tv/src/test/kotlin/ui/tv/TvThemeTest.kt ui-tv/src/test/kotlin/ui/tv/catalog/TvScreenStateTest.kt
git commit -m "feat(tv): each TV style carries its own look, provided by the theme"
```

## Success criteria
- A stored style survives a restart; anything unrecognised reads as mediagram.
- `TvTheme(style = …)` changes the ground and neutral roles and provides `LocalTvStyle`; mediagram's scheme equals `Palette` role for role.
