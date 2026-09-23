package player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePlayerHandle : PlayerHandle {

    override val player: StateFlow<Player?> = MutableStateFlow(null)

    private var listener: PlayerHandle.Listener? = null

    var openedSetId: String? = null
        private set

    var openedStartAtMs: Long? = null
        private set

    var stopCalled: Boolean = false
        private set

    /** What [positionMs]/[durationMs] answer; a test sets these to model where playback is. */
    var fakePositionMs: Long? = null
    var fakeDurationMs: Long? = null

    override fun open(setId: String, startAtMs: Long) {
        openedSetId = setId
        openedStartAtMs = startAtMs
    }

    override fun setListener(listener: PlayerHandle.Listener?) {
        this.listener = listener
    }

    override fun stop() {
        stopCalled = true
    }

    override fun release() = Unit

    override fun positionMs(): Long? = fakePositionMs

    override fun durationMs(): Long? = fakeDurationMs

    fun emitError(message: String) {
        listener?.onError(message)
    }

    fun emitPlaying(isPlaying: Boolean) {
        listener?.onPlayingChanged(isPlaying)
    }
}
