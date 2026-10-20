package ui.chrome

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass

/** The window width the rail itself narrows at — the web's `--rail-width` breakpoint, `shell.css:169`. */
private val RailNarrowBreakpoint = 1180.dp
internal val RailWidthNarrow = 184.dp
private val RailWidthWide = 224.dp

/** The library rail's own width at this window. */
@Composable
internal fun railWidth(): Dp = if (LocalConfiguration.current.screenWidthDp.dp <= RailNarrowBreakpoint) RailWidthNarrow else RailWidthWide

/** How much of the window's start edge the rail takes where one is drawn: nothing unless the layout is EXPANDED. */
@Composable
internal fun railWidthShown(): Dp {
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    return if (expanded) railWidth() else 0.dp
}
