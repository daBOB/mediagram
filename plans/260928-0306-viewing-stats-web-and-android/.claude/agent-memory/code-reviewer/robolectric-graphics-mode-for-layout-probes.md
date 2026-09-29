---
name: robolectric-graphics-mode-for-layout-probes
description: Android ui-mobile Robolectric tests run LEGACY graphics (fake text metrics); use @GraphicsMode(NATIVE) + GetTextLayoutResult for text-size/wrap checks
metadata:
  type: project
---

In `android/ui-mobile` (Robolectric 4.16), tests default to LEGACY graphics: text measures ~1px per character, so autosize/wrap/truncation never happen, and an exact `onNodeWithText(...)` match passes even for an ellipsized string (semantics carry the full text). Tests asserting "title fits on one line" that way are vacuous.

**Why:** found reviewing the 0.71.1 department hero (2026-09-28); the implementer's three one-line tests could not fail.

**How to apply:** for layout/text probes add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (measurement works; only `captureToImage()` fails in this setup) and read `SemanticsActions.GetTextLayoutResult` (lineCount, hasVisualOverflow, line height ÷ lineHeight-em ≈ autosized font size). Pixel/colour checks are not possible in JVM tests here — verify colours from M3 bytecode (`javap` on the cached material3 aar) or on device. See [[scratchpad-is-shared-between-agents]].
