package ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import designsystem.LocalIndicationTone

/**
 * Presses inside [content] tint in [ink], the colour of the surface's own
 * text, so the tint shows on the surface it is drawn on. For a surface that
 * opens from over the video, where the player tints in white: a menu or a
 * dialog on the page's colours, the notice bar on their inverse. The focus
 * ring stays the stage coral (`copy(ink = )` leaves it): intended, it shows on
 * either surface.
 */
@Composable
internal fun IndicationInk(
    ink: Color,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIndicationTone provides LocalIndicationTone.current.copy(ink = ink), content = content)
}
