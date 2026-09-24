package player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePlayerHandle : PlayerHandle {

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player

    /** Installs a (typically mocked) player, so a test can exercise whatever attaches to [player] directly — [AudioChoiceController]'s own listener among them. */
    fun installPlayer(player: Player?) {
        _player.value = player
    }

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

    /** Every rate [setPlaybackSpeed] was asked for, in order — a test's way of seeing the reset-then-correct sequence. */
    val speedCalls: MutableList<Float> = mutableListOf()

    /** The last rate asked for, or `null` if never. */
    val lastSpeed: Float? get() = speedCalls.lastOrNull()

    override fun open(setId: String, startAtMs: Long) {
        openedSetId = setId
        openedStartAtMs = startAtMs
    }

    override fun setPlaybackSpeed(rate: Float) {
        speedCalls += rate
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
