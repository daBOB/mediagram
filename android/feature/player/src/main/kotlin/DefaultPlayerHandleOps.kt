package player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import playback.setUri

/**
 * The two things [DefaultPlayerHandle] does directly to the real player —
 * split out to keep that file under the project's line guideline.
 */

/**
 * Loads [setId] for real: the two-argument `setMediaItem` overload, not
 * `setMediaItem(item)` then a seek, which starts a frame at zero and jumps
 * from it, briefly showing the top of the title before the resume point.
 * The singleton player never resets its own rate; [DefaultPlayerHandle]
 * corrects it to whatever this profile remembered once it knows one, but
 * this is the floor under that, so a title never inherits a leftover speed.
 */
internal fun Player.openReal(setId: String, startAtMs: Long, playWhenReady: Boolean) {
    setMediaItem(MediaItem.fromUri(setUri(setId)), startAtMs)
    prepare()
    this.playWhenReady = playWhenReady
    setPlaybackSpeed(1f)
}

/**
 * A settled player does not repeat the event that settled it, so a
 * subscriber that has just reset itself to "preparing" needs telling again
 * that playback is under way. A player still buffering is the one case to
 * stay quiet for: its own ready event is still coming, and reporting
 * "paused" ahead of it would replace a truthful spinner with a false still
 * frame.
 */
internal fun Player.republishTo(notifyPlaying: (Boolean) -> Unit) {
    if (playbackState == Player.STATE_BUFFERING) return
    notifyPlaying(isPlaying)
}
