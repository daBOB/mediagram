package player

import kotlinx.coroutines.CancellationException

/** Runs [block], answering [default] for anything but cancellation — which is rethrown, so a cancelled coroutine stays cancelled. */
internal suspend fun <T> safely(default: T, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    default
}
