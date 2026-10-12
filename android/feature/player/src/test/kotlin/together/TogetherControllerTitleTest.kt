package player.together

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The session moving on to another title: followed by asking for it as Next would, and never sent back as this screen's move. */
class TogetherControllerTitleTest {
    @Test
    fun anotherScreenMovingOnIsFollowedAndOpeningThatTitleSendsNothing() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.push(950, rig.message(950).copy(set = EP2, pos = 0.0, at = rig.now(), by = TV.screen))
            assertEquals(listOf(EP2), rig.opened)
            assertEquals("Next episode by TV", rig.controller.ui.value.notice)
            val sent = rig.core.togetherEdits.size

            val start = rig.controller.open(EP2)
            runCurrent()

            assertEquals(sent, rig.core.togetherEdits.size)
            assertEquals(0.0, start?.pos)
        }

    @Test
    fun aTitleTheSessionMovedToIsAskedForAgainOnEachCheckUntilItOpens() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.push(950, rig.message(950).copy(set = EP2, pos = 0.0, at = rig.now(), by = TV.screen))

            advanceTimeBy(DRIFT_CHECK_MS)
            runCurrent()

            assertEquals(listOf(EP2, EP2), rig.opened)
        }

    @Test
    fun aTitleOpenedByHandAfterLeavingIsTheViewersOwnMove() =
        runTest {
            val rig = TogetherRig(this).joined()
            // The session moved on, and this screen left before that title opened; then it ended.
            rig.push(950, rig.message(950).copy(set = EP2, pos = 0.0, at = rig.now(), by = TV.screen))
            rig.controller.leave()
            runCurrent()
            val ended = rig.message(950).copy(ended = true, by = TV.screen)
            rig.core.togetherMessages.remove(950)
            rig.push(950, ended)
            // A session of its own, started on the title still open here.
            rig.controller.onButton()
            runCurrent()

            rig.controller.open(EP2)
            runCurrent()

            assertEquals(EP2, rig.message(900).set)
        }

    @Test
    fun theSessionMovingToATitleThisProfileMayNotSeeIsWhereThisScreenLeaves() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.push(950, rig.message(950).copy(set = HIDDEN, pos = 0.0, at = rig.now(), by = TV.screen))

            assertTrue(rig.opened.isEmpty())
            assertEquals(LEFT_NOT_HERE, rig.controller.ui.value.notice)
            assertEquals(listOf(TV), rig.message(950).members)
        }
}
