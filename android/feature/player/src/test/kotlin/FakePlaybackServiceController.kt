package player

/** Records `start`/`stop` calls rather than touching an Android `Context` — see [PlaybackServiceController]. */
internal class FakePlaybackServiceController : PlaybackServiceController {
    var startCalls = 0
        private set

    var stopCalls = 0
        private set

    override fun start() {
        startCalls++
    }

    override fun stop() {
        stopCalls++
    }
}
