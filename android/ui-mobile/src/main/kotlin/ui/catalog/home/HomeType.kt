package ui.catalog.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The web's own `clamp(min, fraction·W, max)` idiom (`home.css`'s cover and
 * feature type, `theme.css`'s own gutter) — a size that answers to the
 * window rather than sitting at one fixed number on every device. [width]
 * carries whatever the caller measures the fraction against — the window's
 * width for most of these, its height for the cover's own `vh` clamp — so
 * this stays one pure function rather than a text-sizing one and a
 * spacing one that happen to do the same arithmetic.
 */
internal fun fluid(
    min: Float,
    fraction: Float,
    max: Float,
    width: Float,
): Float = (width * fraction).coerceIn(min, max)

/** The page's own side margin, both edges — the web's `--gutter` (`theme.css:112`, `clamp(16px, 3.2vw, 56px)`). */
internal fun gutterFor(width: Dp): Dp = fluid(16f, 0.032f, 56f, width.value).dp

/** Below this, the web drops to its phone layout: the cover grows with its own content, features stack in one column (`home.css:348`). */
internal val CompactBreakpoint = 900.dp

/** Below this (and above [CompactBreakpoint]), features hold two columns and the cover's own side column is hidden — the tablet's own width (`home.css:342`). */
internal val WideBreakpoint = 1180.dp

/**
 * An extent, spelled the way the web's own `countOf` does: `"three shows"`,
 * `"one show"`, `"170 lessons"` (`format.js:171-175`). [ui.common.catalog.countOf]
 * (ui-common) is the rest of this app's own, plainer `"$count $noun"` — this
 * one is only for the captions the web's own `collectionGrid` spells the
 * same way (Latest series, Latest courses); [catalog.spelledCountOf]
 * (feature:catalog) is where the actual word list and pluralisation rule
 * live, shared with the Settings tally and the television's own Home.
 */
internal fun countOf(
    count: Int,
    noun: String,
): String = catalog.spelledCountOf(count, noun)
