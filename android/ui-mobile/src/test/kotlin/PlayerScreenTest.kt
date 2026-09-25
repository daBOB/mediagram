package ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Only the decision function is testable without a real Activity and a
 * live rotation, which no infrastructure in this module provides yet
 * (its other test is a plain JVM unit test with no Compose test rule).
 * This proves the decision itself, not that `PlayerScreen` reads
 * `Activity.isChangingConfigurations`/`isInPictureInPictureMode` and
 * wires them through correctly.
 */
class PlayerScreenTest {

    @Test
    fun aRotationDoesNotStopPlayback() {
        assertFalse(shouldStopOnDispose(isChangingConfigurations = true, isInPictureInPicture = false))
    }

    @Test
    fun pictureInPictureDoesNotStopPlayback() {
        assertFalse(shouldStopOnDispose(isChangingConfigurations = false, isInPictureInPicture = true))
    }

    @Test
    fun leavingTheScreenForRealStopsPlayback() {
        assertTrue(shouldStopOnDispose(isChangingConfigurations = false, isInPictureInPicture = false))
    }
}
