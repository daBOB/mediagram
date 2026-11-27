package homerows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the TV home screen asks this app to open, read from its intent. */
class HomeLaunchTest {
    @Test
    fun resumeWithASetId() = assertEquals(HomeLaunch.Resume("m1"), homeLaunchOf(ACTION_RESUME, "m1"))

    @Test
    fun openWithASetId() = assertEquals(HomeLaunch.Open("m1"), homeLaunchOf(ACTION_OPEN, "m1"))

    @Test
    fun pickProfileNeedsNoSetId() = assertEquals(HomeLaunch.PickProfile, homeLaunchOf(ACTION_PICK_PROFILE, null))

    @Test
    fun resumeWithoutASetIdIsNothing() {
        assertNull(homeLaunchOf(ACTION_RESUME, null))
        assertNull(homeLaunchOf(ACTION_OPEN, " "))
    }

    @Test
    fun mainActionIsNothing() = assertNull(homeLaunchOf("android.intent.action.MAIN", "m1"))

    @Test
    fun theContractNamesStay() {
        assertEquals("com.mediagram.android.action.RESUME", ACTION_RESUME)
        assertEquals("com.mediagram.android.action.OPEN", ACTION_OPEN)
        assertEquals("com.mediagram.android.action.PICK_PROFILE", ACTION_PICK_PROFILE)
        assertEquals("set_id", EXTRA_SET_ID)
    }
}
