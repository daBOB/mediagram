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

    /** What the last [open] was asked for — `null` until one lands. */
    var openedPlayWhenReady: Boolean? = null
        private set

    var stopCalled: Boolean = false
        private set

    var playCalled: Boolean = false
        private set

    /** What [positionMs]/[durationMs]/[bufferedPositionMs] answer; a test sets these to model where playback is. */
    var fakePositionMs: Long? = null
    var fakeDurationMs: Long? = null
    var fakeBufferedPositionMs: Long? = null

    /** What [isLoading] answers; a test sets this to model the load control having stopped. */
    var fakeIsLoading: Boolean = false

    /** Every rate [setPlaybackSpeed] was asked for, in order — a test's way of seeing the reset-then-correct sequence. */
    val speedCalls: MutableList<Float> = mutableListOf()

    /** The last rate asked for, or `null` if never. */
    val lastSpeed: Float? get() = speedCalls.lastOrNull()

    /** Mirrors what the real player last reported through [emitPlaying] — see [open]'s own synthetic emission. */
    private var isPlayingNow = false

    /**
     * The real `setMediaItem` fires a synchronous `onIsPlayingChanged(false)`
     * when it lands on a player that was actively playing something else —
     * an up-next switch made mid-title, never a fresh open or an
     * already-stopped one, where there is no change to report. Modelled
     * here the same way, so a test can pin what a subscriber does with it:
     * a save landing against the title just switched to, at its start
     * position, before a frame of it has played.
     */
    override fun open(setId: String, startAtMs: Long, playWhenReady: Boolean) {
        val changingWhilePlaying = isPlayingNow && setId != openedSetId
        openedSetId = setId
        openedStartAtMs = startAtMs
        openedPlayWhenReady = playWhenReady
        if (changingWhilePlaying) emitPlaying(false)
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

    override fun bufferedPositionMs(): Long? = fakeBufferedPositionMs

    override fun play() {
        playCalled = true
    }

    override fun isLoading(): Boolean = fakeIsLoading

    fun emitError(message: String) {
        listener?.onError(message)
    }

    fun emitPlaying(isPlaying: Boolean) {
        isPlayingNow = isPlaying
        listener?.onPlayingChanged(isPlaying)
    }

    fun emitEnded() {
        listener?.onEnded()
    }

    fun emitSeeked() {
        listener?.onSeeked()
    }
}
