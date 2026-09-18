# Phase 4: The TV surface

**Context:** [plan.md](plan.md) · [phase 3](phase-03-login-and-catalog-mobile.md) · [spec §4, §5](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** High — the television is why this round exists.
- **Status:** Blocked by phase 3. Independent of phase 5.
- **Deliverable:** the same library, browsable with a D-pad and nothing else.

## Key insights

- **The Android skill does not cover television at all.** No reference file mentions `androidx.tv`, leanback, or 10-foot layout. Versions here are not skill-verified; pin `androidx.tv:tv-material` at the current stable and record which version was chosen in the commit message.
- Nothing in `feature:catalog` changes. This phase renders `CatalogUiState` and `LoginUiState` a second time and adds no logic. If a ViewModel needs editing to make the TV work, the boundary was drawn wrong — fix the boundary, not the ViewModel.
- Material 3's touch components do not take D-pad focus correctly. Use `androidx.tv:tv-material` components on this surface; do not mix them with `androidx.compose.material3` ones.
- A television crops the edges. Every screen needs overscan padding, and text needs the 10-foot scale.

## Related code files

- Create: `android/ui-tv/src/main/kotlin/{TvApp.kt,TvCatalogScreen.kt,TvLoginScreen.kt,TvSettingsScreen.kt}`
- Modify: `android/gradle/libs.versions.toml`, `android/core/designsystem/src/main/kotlin/Spacing.kt`, `android/app/src/main/kotlin/**/MainActivity.kt`

---

### Task 1: Add the TV artifacts

**Files:** Modify `android/gradle/libs.versions.toml`, `android/ui-tv/build.gradle.kts`

- [ ] **Step 1:** Add to the catalog a `tvMaterial` version at the current stable `androidx.tv:tv-material`, plus `androidx-tv-material` and `androidx-tv-foundation` library entries.
- [ ] **Step 2:** Add them to `:ui-tv` only. `:ui-mobile` must never see them.
- [ ] **Step 3:** Run `./gradlew help && ./gradlew :ui-tv:assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit — `build(android): add the television Compose artifacts` — naming the version pinned and that it is outside the skill's verified set.

---

### Task 2: Overscan and the 10-foot scale

**Files:** Modify `android/core/designsystem/src/main/kotlin/Spacing.kt`, `Theme.kt` · Test `android/core/designsystem/src/test/kotlin/SpacingTest.kt`

**Interfaces — Produces:** `object Overscan { val horizontal: Dp = 48.dp; val vertical: Dp = 27.dp }` and a `tvTypography()` scaling the shared type scale up.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun overscanIsFivePercentOfATypicalPanel() {
    assertEquals(48.dp, Overscan.horizontal)
    assertEquals(27.dp, Overscan.vertical)
}
```

- [ ] **Step 2:** Run `./gradlew :core:designsystem:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. 48dp × 27dp is 5% of a 960×540dp television surface, the standard safe area.
- [ ] **Step 4:** Run the test. Expected: PASS.
- [ ] **Step 5:** Commit — `feat(android): overscan-safe spacing and a ten-foot type scale`.

---

### Task 3: The TV catalog

**Files:** Create `android/ui-tv/src/main/kotlin/TvCatalogScreen.kt`

**Interfaces — Consumes:** phase 3's `CatalogUiState`, `Shelf`, `MediaSet` — unchanged.

- [ ] **Step 1:** Write `TvCatalogScreen(state: CatalogUiState, onOpen: (MediaSet) -> Unit)` using `androidx.tv.material3` components, shelves as focusable rows inside `Overscan` padding.
- [ ] **Step 2:** Give the first card of the first shelf initial focus with a `FocusRequester`, so the screen is usable the instant it appears. A television with nothing focused is a dead screen.
- [ ] **Step 3:** Scale the focused card and let it bring itself into view; focus must be legible from across a room, not a 1dp outline.
- [ ] **Step 4:** Write `android/ui-tv/src/androidTest/kotlin/TvCatalogFocusTest.kt`:

```kotlin
@Test
fun theFirstCardHoldsFocusWhenTheShelvesAppear() {
    composeRule.setContent { TvCatalogScreen(readyState(), onOpen = {}) }
    composeRule.onNodeWithText("Arrival").assertIsFocused()
}

@Test
fun dpadRightMovesAlongTheShelf() {
    composeRule.setContent { TvCatalogScreen(readyState(), onOpen = {}) }
    composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
    composeRule.onNodeWithText("Severance").assertIsFocused()
}
```

- [ ] **Step 5:** Run `./gradlew :ui-tv:connectedDebugAndroidTest` on a TV emulator. Expected: PASS, both.
- [ ] **Step 6:** Commit — `feat(android): browse the library on a television`.

---

### Task 4: TV sign-in and settings

**Files:** Create `android/ui-tv/src/main/kotlin/{TvLoginScreen.kt,TvSettingsScreen.kt,TvApp.kt}` · Modify `MainActivity.kt`

- [ ] **Step 1:** Render `LoginUiState` with TV components. Every field must be reachable and submittable with the D-pad alone.
- [ ] **Step 2:** `TvSettingsScreen` takes the package URL and key. It is unpleasant to type with a remote and that is the accepted cost this round (spec §1); leave a comment saying the field exists to be replaced by pairing, without naming a phase.
- [ ] **Step 3:** `TvApp` wires the three screens with the same start-destination rule as `MobileApp`. Call it from `MainActivity`'s television branch.
- [ ] **Step 4:** Install on a TV emulator or device. **Unplug the mouse.** Using only the D-pad: sign in, enter the URL and key, browse the library. Anything unreachable is a bug in this phase.
- [ ] **Step 5:** Confirm `feature:catalog` has no diff in this phase: `git diff --stat android/feature/`. Expected: empty. A non-empty diff means logic leaked into the surface.
- [ ] **Step 6:** Commit — `feat(android): sign in and configure from the sofa`.

## Todo list

- [ ] TV artifacts pinned and recorded
- [ ] Overscan and ten-foot type in the shared design system
- [ ] TV catalog with correct initial focus and D-pad traversal, tested on device
- [ ] TV login and settings reachable with a D-pad alone
- [ ] `feature:catalog` unchanged by this phase

## Success criteria

On a television, with only a D-pad: sign in, enter the package credentials,
and browse the real library with visible focus and no clipped edges. No file
under `android/feature/` changed.

## Risk assessment

| Risk | Mitigation |
|---|---|
| `androidx.tv` version is not skill-verified | Pin at current stable, record it, and verify on a device in task 3 step 5 rather than trusting the pin. |
| Material 3 and TV components get mixed | `:ui-mobile` and `:ui-tv` have disjoint dependencies; a mix does not compile. |
| Focus traps — a screen with no way back | Task 4 step 4's mouse-unplugged pass is the gate that catches it. |
| Logic leaks into `ui-tv` | Task 4 step 5 diffs `android/feature/` and requires it empty. |

## Security considerations

The key field is masked on the television too, where a screen is visible to a
whole room.

## Next steps

[Phase 5](phase-05-playback-media3.md) if not already done, then
[phase 6](phase-06-byte-truth-gate-and-docs.md).
