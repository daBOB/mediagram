package ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The window's own ground — every page-level container reaches for this,
 * never [ColorScheme.surface] (one step up from it in this palette, for a
 * plate or a raised panel rather than the page itself: a card, a dialog, a
 * bottom sheet). One shared, named token rather than each call site typing
 * `colorScheme.background` on its own, so a scrim that fades toward "the
 * page" and a container that draws "the page" can never name two different
 * colours by accident.
 */
internal val ColorScheme.pageGround: Color get() = background
