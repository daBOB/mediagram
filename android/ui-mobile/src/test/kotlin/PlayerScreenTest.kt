package ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Only the decision function is testable without a real Activity and a
 * live rotation, which no infrastructure in this module provides yet
 * (its other test is a plain JVM unit test with no Compose test rule).
 * This proves the decision itself, not that `PlayerScreen` reads
 * `Activity.isChangingConfigurations` and wires it through correctly.
 */
class PlayerScreenTest {

    @Test
    fun aRotationDoesNotStopPlayback() {
        assertFalse(shouldStopOnDispose(isChangingConfigurations = true))
    }

    @Test
    fun leavingTheScreenForRealStopsPlayback() {
        assertTrue(shouldStopOnDispose(isChangingConfigurations = false))
    }
}
