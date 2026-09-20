package player

import androidx.media3.common.Player

class FakePlayerHandle : PlayerHandle {

    override val player: Player
        get() = throw UnsupportedOperationException("not exercised by this fake")

    private var listener: PlayerHandle.Listener? = null

    var openedSetId: String? = null
        private set

    override fun open(setId: String) {
        openedSetId = setId
    }

    override fun setListener(listener: PlayerHandle.Listener?) {
        this.listener = listener
    }

    override fun release() = Unit

    fun emitError(message: String) {
        listener?.onError(message)
    }

    fun emitPosition(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        listener?.onPositionChanged(positionMs, durationMs, isPlaying)
    }
}
