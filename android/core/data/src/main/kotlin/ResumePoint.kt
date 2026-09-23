package data

/**
 * Whether a recorded position is somewhere to go back to.
 *
 * Not every position is. A title stopped twenty seconds in was not being
 * watched, it was being looked at; one stopped in the closing credits is
 * finished, and offering to resume it means offering the credits. Both are
 * worse than starting from the beginning, and both happen constantly.
 *
 * A straight port of `web/public/lib/resume-point.js`, which is
 * authoritative: these are judgements about what a number means, made once
 * and shared by both surfaces, not bookkeeping either one owns. Kept free of
 * `model.Progress` so it stays exactly as pure as its web counterpart —
 * callers pass the two numbers a judgement needs, nothing more.
 */
object ResumePoint {

    /** Before this, a title was opened rather than watched. */
    private const val TOO_EARLY_SECONDS = 30.0

    /** Or this share of a title too short for half a minute to be a glance. */
    private const val TOO_EARLY_FRACTION = 0.1

    /** What is left at the end of a title is the credits. */
    private const val CREDITS_FRACTION = 0.05

    /** Never more than this, or the last twelve minutes of a film would count. */
    private const val CREDITS_SECONDS = 60.0

    /** The tail of a title that counts as having finished it: the smaller of a share and a fixed minute. */
    private fun creditsOf(runtime: Double): Double = minOf(runtime * CREDITS_FRACTION, CREDITS_SECONDS)

    /**
     * Whether [at] is the end of a title [runtime] long.
     *
     * Unknowable without a runtime, and answered `false` there: a title that
     * may or may not be finished is better offered than silently dropped
     * from the shelf that exists to remind you of it.
     */
    fun isFinished(at: Double, runtime: Double): Boolean {
        if (!runtime.isFinite() || runtime <= 0) return false
        if (!at.isFinite() || at < 0) return false
        return runtime - at <= creditsOf(runtime)
    }

    /**
     * Where to start a title given what was recorded, or `null` for the top.
     *
     * `null` covers all three ways a position is not a resume point: there
     * is none, it is too near the start to be worth keeping, or the title
     * is done. [progress] is `null` itself when nothing was recorded at
     * all — the same case, reached one step earlier.
     */
    fun resumeAt(progress: ProgressPoint?): Double? {
        if (progress == null) return null
        val at = progress.at
        if (!at.isFinite()) return null

        // Scaled for the same reason the tail is: half a minute is a glance
        // at a film and most of a two-minute lesson. With no runtime the
        // flat half minute is all there is to go on.
        val runtime = progress.duration
        val glance = if (runtime != null && runtime.isFinite() && runtime > 0) {
            minOf(TOO_EARLY_SECONDS, runtime * TOO_EARLY_FRACTION)
        } else {
            TOO_EARLY_SECONDS
        }
        if (at < glance) return null

        if (isFinished(at, runtime ?: 0.0)) return null
        return at
    }

    /**
     * How far through, 0 to 1, or `null` when the runtime is unknown.
     *
     * `null` rather than a guess: a progress rule drawn from an unknown
     * length is a bar that means nothing, and a bar that means nothing is
     * worse than none.
     */
    fun watchedFraction(progress: ProgressPoint?): Double? {
        if (progress == null) return null
        val runtime = progress.duration
        val at = progress.at
        if (runtime == null || !runtime.isFinite() || runtime <= 0 || !at.isFinite()) return null
        return (at / runtime).coerceIn(0.0, 1.0)
    }

    /**
     * Which runtime may be believed, of the two that might be known.
     *
     * The catalog's is the file's real length and wins whenever it is
     * there. The observed one — however this device measured playback — is
     * only worth having when it saw the whole file, which [direct] answers:
     * a still-growing conversion's reported length is worse than not
     * knowing one at all, since everything downstream would then believe
     * the title is finished a few seconds early, for the whole title.
     * `0` means "nobody knows" and leaves a position to keep its place
     * rather than being wrongly judged done.
     */
    fun trustedRuntime(catalogued: Double?, observed: Double?, direct: Boolean): Double {
        if (catalogued != null && catalogued.isFinite() && catalogued > 0) return catalogued
        return if (direct && observed != null && observed.isFinite() && observed > 0) observed else 0.0
    }
}

/**
 * One profile's position in one title, as [ResumePoint] judges it — the
 * same two numbers `model.Progress` carries, kept separate so this file has
 * no dependency of its own to stay pinned to the web's fixtures.
 */
data class ProgressPoint(val at: Double, val duration: Double?)
