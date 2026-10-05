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
 * the smallest error breaks a tie.
 *
 * A [current] mode that already fits is kept, even when a smaller multiple is
 * on offer: every switch blanks the screen while HDMI re-syncs, and 30 fps on
 * 60 Hz or 24 fps on 120 Hz is already judder-free.
 */
fun pickDisplayMode(
    current: Mode,
    supported: List<Mode>,
    fps: Float,
): Int? {
    if (!(fps > 0f)) return null
    if (fit(current, fps) != null) return null
    return supported
        .filter { it.width == current.width && it.height == current.height }
        .mapNotNull { mode -> fit(mode, fps)?.let { mode to it } }
        .minWithOrNull(compareBy({ it.second.first }, { it.second.second }))
        ?.first
        ?.id
}

/** `(multiple, error)` when [mode]'s refresh is a whole multiple of [fps] within tolerance, else null. */
private fun fit(
    mode: Mode,
    fps: Float,
): Pair<Int, Float>? {
    val ratio = mode.refreshHz / fps
    val multiple = ratio.roundToInt()
    if (multiple < 1) return null
    val error = abs(ratio - multiple) / multiple
    return if (error <= TOLERANCE) multiple to error else null
}

/**
 * The preferred mode id to hold for a title of [fps]: the picked mode, or
 * [before] (what the window preferred when the screen opened, normally 0) when
 * nothing fits or the rate is unknown. [start] is the display's mode from
 * before any switch of ours, so an earlier title's switch never leaks into
 * this one's decision.
 */
fun displayModeToApply(
    before: Int,
    start: Mode,
    supported: List<Mode>,
    fps: Float,
): Int = pickDisplayMode(start, supported, fps) ?: before

private const val TOLERANCE = 0.005f
