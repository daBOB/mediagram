package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * `viewModelScope` needs a Main dispatcher to launch onto; bound to this
 * test's own scheduler (not a fresh one of its own) so `advanceTimeBy` and
 * `advanceUntilIdle` in the test body also govern the ticker's `delay`
 * calls, the same way they already govern everything else `runTest` runs.
 * Unconfined, so writes still land immediately wherever a test doesn't care
 * about the ten-second cadence and never explicitly advances time.
 *
 * Callers pair this with `@After fun tearDown() = Dispatchers.resetMain()`.
 */
fun TestScope.installMainDispatcher() {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
}
