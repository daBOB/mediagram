package data

/**
 * What one attempt to replace the installed catalogue did.
 *
 * Three cases rather than a boolean, because finding nothing new is not a
 * failure: a viewer who asks for the library again and is told it is
 * already current got what they asked for. Only [Refused] is a warning,
 * and it carries the sentence the core wrote, which says what to do next.
 */
sealed interface RefreshOutcome {
    data object Updated : RefreshOutcome
    data object AlreadyCurrent : RefreshOutcome
    data class Refused(val reason: String) : RefreshOutcome
}

/**
 * The last [RefreshOutcome], and nothing else.
 *
 * The same shape as `PlaybackCounters` and here for the same reason: two
 * screens ask about one process-lifetime fact — the catalog, which does the
 * refreshing, and the System screen, which reports on it — and neither owns
 * it. A log that accumulated history would be answering a question nobody
 * asks; what a viewer wants to know is what happened the last time they
 * pressed the thing.
 *
 * Written on whichever thread the refresh finished on and read on main, so
 * the field is volatile — a reference is published atomically, but without
 * this the reader is not guaranteed to see it.
 */
class RefreshLog {

    @Volatile
    private var latest: RefreshOutcome? = null

    fun record(outcome: RefreshOutcome) {
        latest = outcome
    }

    /** `null` before anything has been asked, which is not the same as nothing having changed. */
    fun last(): RefreshOutcome? = latest
}
