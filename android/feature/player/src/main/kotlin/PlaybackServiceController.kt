package player

/**
 * Starts and stops `PlaybackService` alongside whatever [PlayerViewModel]
 * itself opens and stops. An interface so every existing [PlayerViewModel]
 * test can go on building one without an Android `Context` in hand — only
 * [AndroidPlaybackServiceController], the real DI-provided implementation,
 * ever touches one.
 */
interface PlaybackServiceController {
    /** Idempotent: a set already open when this runs again just restarts the same instance. */
    fun start()

    /** Idempotent: stopping an already-stopped service is a no-op. */
    fun stop()

    companion object {
        val Noop: PlaybackServiceController = object : PlaybackServiceController {
            override fun start() = Unit
            override fun stop() = Unit
        }
    }
}
