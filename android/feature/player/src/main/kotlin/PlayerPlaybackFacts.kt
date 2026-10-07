package player

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * The two facts [DefaultPlayerHandle] reads off the real player. `STATE_IDLE`
 * covers "never prepared", "just stopped" and "just errored" alike, and none
 * of those is a position or a length worth trusting.
 */
internal fun Player.trustedPositionMs(): Long? {
    if (playbackState == Player.STATE_IDLE) return null
    return currentPosition
}

internal fun Player.trustedDurationMs(): Long? {
    if (playbackState == Player.STATE_IDLE) return null
    return duration.takeIf { it != C.TIME_UNSET && it > 0 }
}

internal fun Player.trustedBufferedPositionMs(): Long? {
    if (playbackState == Player.STATE_IDLE) return null
    return bufferedPosition
}
