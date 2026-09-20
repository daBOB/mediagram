package player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePlayerHandle : PlayerHandle {

    override val player: StateFlow<Player?> = MutableStateFlow(null)

    private var listener: PlayerHandle.Listener? = null

    var openedSetId: String? = null
        private set

    var stopCalled: Boolean = false
        private set

    override fun open(setId: String) {
        openedSetId = setId
    }

    override fun setListener(listener: PlayerHandle.Listener?) {
        this.listener = listener
    }

    override fun stop() {
        stopCalled = true
    }

    override fun release() = Unit

    fun emitError(message: String) {
        listener?.onError(message)
    }

    fun emitPosition(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        listener?.onPositionChanged(positionMs, durationMs, isPlaying)
    }
}
