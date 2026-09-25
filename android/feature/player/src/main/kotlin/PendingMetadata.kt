package player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/**
 * The most recent [DefaultPlayerHandle.setMetadata], reapplied on every
 * real load rather than left on whichever `MediaItem` [Player.openReal]
 * just replaced (with none of its own) — split out of [DefaultPlayerHandle]
 * to keep that file under the project's line guideline. Without this, a
 * cold start (the resolve that supplies it can finish before the player
 * itself is ready) and a retry (a real reload of the *same* title, whose
 * metadata already resolved once and never will again) both silently lost
 * it.
 */
internal class PendingMetadata {
    private var last: MediaMetadata? = null

    fun remember(metadata: MediaMetadata) {
        last = metadata
    }

    fun reapply(player: Player) {
        last?.let(player::setMetadataReal)
    }
}
