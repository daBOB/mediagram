package data

import android.util.Log
import kotlinx.coroutines.CancellationException

private const val TAG = "fallback"

/**
 * Runs [block] and answers its value, or [default] when it throws.
 *
 * A [CancellationException] is rethrown, so a cancelled coroutine stays
 * cancelled instead of carrying on with the default. With [what] given, a
 * failure is logged as "<what> failed" with its throwable; without it the
 * fallback is silent.
 */
suspend fun <T> orDefault(default: T, what: String? = null, block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        if (what != null) Log.w(TAG, "$what failed", e)
        default
    }
