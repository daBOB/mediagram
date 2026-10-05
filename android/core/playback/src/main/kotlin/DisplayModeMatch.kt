package playback

import kotlin.math.abs
import kotlin.math.roundToInt

/** One of a display's modes: what `Display.Mode` says, without the framework type. */
data class Mode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshHz: Float,
)

/**
 * The mode to switch to so a film of [fps] plays without judder, or null for
 * "leave the display alone".
 *
 * Only modes at [current]'s own resolution are considered: changing the
 * resolution is a different, heavier switch than the viewer asked for. A mode
 * fits when its refresh rate is a whole multiple of [fps], within half a
 * percent, which absorbs the 1000/1001 rounding panels report. The smallest
 * multiple wins, since a 24 fps film on 24 Hz beats the same film on 48 Hz;
 * the smallest error breaks a tie. When the best is already the current mode
 * there is nothing to do.
 */
fun pickDisplayMode(
    current: Mode,
    supported: List<Mode>,
    fps: Float,
): Int? {
    if (!(fps > 0f)) return null
    val best =
        supported
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { mode ->
                val ratio = mode.refreshHz / fps
                val multiple = ratio.roundToInt()
                val error = abs(ratio - multiple) / multiple
                if (multiple >= 1 && error <= TOLERANCE) Triple(mode, multiple, error) else null
            }.minWithOrNull(compareBy({ it.second }, { it.third }))
            ?.first
    return best?.id?.takeIf { it != current.id }
}

private const val TOLERANCE = 0.005f
