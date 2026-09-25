package com.mediagram.android

import androidx.lifecycle.Lifecycle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers [isPipDismissal] — telling a viewer closing the picture-in-picture window apart from expanding it back to full screen. */
class PipDismissalTest {

    @Test
    fun stoppedAndOutOfPictureInPictureIsADismissal() {
        assertTrue(isPipDismissal(isInPictureInPictureMode = false, lifecycleState = Lifecycle.State.CREATED))
    }

    @Test
    fun expandingBackToFullScreenIsNotADismissal() {
        // The activity is already STARTED/RESUMED again by the time this
        // callback runs, as part of the very same transition.
        assertFalse(isPipDismissal(isInPictureInPictureMode = false, lifecycleState = Lifecycle.State.STARTED))
        assertFalse(isPipDismissal(isInPictureInPictureMode = false, lifecycleState = Lifecycle.State.RESUMED))
    }

    @Test
    fun stillInPictureInPictureIsNeverADismissal() {
        // The screen locking while pinned: the mode stays true and this
        // callback does not even fire, but if it somehow did, this must
        // not read as a dismissal either.
        assertFalse(isPipDismissal(isInPictureInPictureMode = true, lifecycleState = Lifecycle.State.CREATED))
    }
}
