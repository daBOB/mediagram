package designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing scale, used by both the mobile and television surfaces. */
object Spacing {
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp
    val extraLarge: Dp = 32.dp
}

/**
 * The margin a television screen is padded by instead of by window insets.
 * A phone or tablet is held close enough that its own bezel is the frame;
 * a TV set is not — it crops or scales the edges of whatever it's sent, by
 * an amount that varies from one set to the next. 48dp x 27dp is 5% of the
 * 960x540dp TV viewport on each axis, the broadcast-standard "TV-safe"
 * margin, carried into dp so the same numbers hold regardless of which set
 * ends up doing the cropping.
 */
object Overscan {
    val horizontal: Dp = 48.dp
    val vertical: Dp = 27.dp
}
