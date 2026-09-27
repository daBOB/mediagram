package ui.chrome

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The bar or header's own height above the content, on the frame currently
 * showing it — the seam [LibraryHome] hands down so its own content can draw
 * under it (Home, over the cover) or pad below it (every other department),
 * without either reaching back up for the bar's own layout. Zero away from
 * the root library, where [ui.LibraryScaffold]'s pushed-frame bar already
 * reserves its own space the ordinary way.
 */
val LocalTopChrome = compositionLocalOf { 0.dp }
