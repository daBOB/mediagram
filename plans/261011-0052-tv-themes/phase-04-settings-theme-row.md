# Phase 04 — Settings › Appearance › Theme

## Context
- Spec: § Settings › Appearance.
- `ui-tv/src/main/kotlin/ui/tv/settings/TvAppearanceSection.kt` (167 lines): an "Accent colour" row and an "Artwork" row of `TvArtworkSwatch`; `settings/TvSettingsScreen.kt:109` (the index status line) and `:146-154` (the wiring).
- The file would pass 200 lines with a third row, so the swatches move to their own file.

## Task 4.1: The Theme row

**Files:**
- Create: `ui-tv/src/main/kotlin/ui/tv/settings/TvAppearanceSwatches.kt`
- Modify: `ui-tv/src/main/kotlin/ui/tv/settings/TvAppearanceSection.kt`, `ui-tv/src/main/kotlin/ui/tv/settings/TvSettingsScreen.kt:109,146-154`
- Test: `ui-tv/src/test/kotlin/ui/tv/settings/TvAppearanceSectionTest.kt`

**Interfaces:**
- Consumes: `TvStyle` (1.1), `AppearanceViewModel.chooseStyle` (1.1), `TvStyle.look` (1.2), `TextStyle.inStyle` (2.2).
- Produces: `TvAppearanceSection(accent, backdrop, style: TvStyle, focusInContent, entryFocusRequester, onSelectAccent, onSelectBackdrop, onSelectStyle: (TvStyle) -> Unit)`; `internal fun TvChoiceSwatch(label: String, note: String, colour: Color, isSelected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, face: (@Composable () -> Unit)? = null)`.

- [ ] **Step 1: Write the failing tests**

In `TvAppearanceSectionTest`: add `private var pickedStyle: TvStyle? = null`; give `show` a `style: TvStyle = TvStyle.Default` parameter, passed as `style = style` and `onSelectStyle = { pickedStyle = it }`; import `designsystem.TvStyle`. Add:

```kotlin
    @Test
    fun theSelectedThemeIsAnnouncedAsSelected() {
        show(accent = Accent.CORAL, style = TvStyle.APPLE)
        compose.onNodeWithContentDescription("Apple TV style").assertIsSelected()
        compose.onNodeWithContentDescription("mediagram").assertIsNotSelected()
    }

    @Test
    fun pressingAThemeReachesChooseStyle() {
        show(accent = Accent.CORAL)
        compose.onNodeWithContentDescription("Google TV style").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(TvStyle.GOOGLE, pickedStyle)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.settings.TvAppearanceSectionTest'`
Expected: compilation fails — `style`/`onSelectStyle` are not parameters yet.

- [ ] **Step 3: Implement**

Create `TvAppearanceSwatches.kt` and move into it, unchanged, `TvAccentSwatch`, `artworkSwatchColor` and `SwatchSize` from `TvAppearanceSection.kt` (make `TvAccentSwatch` and `artworkSwatchColor` `internal`). Replace `TvArtworkSwatch` with one swatch both rows draw:

```kotlin
/**
 * A 16:9 choice with its label and note under it — an Artwork mode or a TV
 * style. [colour] fills it; [face], if any, is drawn on it (a style's own
 * "Aa", so the three themes differ at a glance on a dark ground).
 */
@Composable
internal fun TvChoiceSwatch(
    label: String,
    note: String,
    colour: Color,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    face: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall), modifier = modifier) {
        Surface(
            onClick = onSelect,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .semantics {
                        contentDescription = label
                        role = Role.RadioButton
                        selected = isSelected
                    },
            shape = TvFocus.surfaceShape(),
            colors = ClickableSurfaceDefaults.colors(containerColor = colour),
            scale = TvFocus.surfaceScale(),
            border = TvFocus.surfaceBorder(selected = isSelected),
            glow = TvFocus.surfaceGlow(),
        ) {
            if (face != null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { face() }
        }
        Text(text = label, style = TvType.labelLarge)
        Text(text = note, style = TvType.bodySmall.copy(fontSize = 12.tvSp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A TV style's own ground with "Aa" in its own face and ink — the swatch Settings shows for it. */
@Composable
internal fun TvStyleSwatch(
    style: TvStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neutrals = style.look.neutrals
    TvChoiceSwatch(
        label = style.label,
        note = style.note,
        colour = neutrals.ground,
        isSelected = isSelected,
        onSelect = onSelect,
        modifier = modifier,
        face = { Text(text = "Aa", style = TvType.Base.titleLarge.inStyle(style).copy(fontSize = 28.tvSp), color = neutrals.text) },
    )
}
```

(Imports as the moved code had them, plus `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Alignment`, `designsystem.TvStyle`, `designsystem.inStyle`, `ui.tv.look`.)

In `TvAppearanceSection.kt`:
- the signature gains `style: TvStyle` after `backdrop` and `onSelectStyle: (TvStyle) -> Unit` last;
- the Artwork row draws `TvChoiceSwatch(label = entry.label, note = entry.note, colour = artworkSwatchColor(entry), isSelected = entry == backdrop, onSelect = { onSelectBackdrop(entry) }, modifier = Modifier.weight(1f))`;
- after the Artwork `Column`, a third:
  ```kotlin
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
              Text(text = "Theme", style = SectionHead)
              Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                  for (entry in TvStyle.entries) {
                      TvStyleSwatch(style = entry, isSelected = entry == style, onSelect = { onSelectStyle(entry) }, modifier = Modifier.weight(1f))
                  }
              }
          }
  ```
- KDoc: after the Artwork sentence add "then the TV's own question, Theme: mediagram, Apple TV style or Google TV style ([TvStyle]), which the phone does not ask yet." The "Square, not the web's…" paragraph becomes "Square under mediagram, where [TvFocus] draws every television plate square; a style that rounds its cards rounds these too."

In `TvSettingsScreen.kt`:
- line 109: `SettingsSection.APPEARANCE to IndexStatus("${appearance.style.label} · ${appearance.accent.label} · ${appearance.backdrop.label}"),`
- the Appearance case passes `style = appearance.style` and `onSelectStyle = appearanceViewModel::chooseStyle`.
If `git grep -n "accent.label\|· Default\"" -- ui-tv/src/test` shows a test pinning the old status line, update its expected string the same way.

- [ ] **Step 4: Run the tests to see them pass**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS, including the four existing Appearance tests.

- [ ] **Step 5: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "feat(tv): Settings › Appearance offers the three TV themes"
```

## Success criteria
- Theme row of three swatches, each the style's ground with "Aa" in its face; choosing one applies at once (the theme reads the shared `StateFlow`).
- The index line reads "Apple TV style · Coral · Default".
