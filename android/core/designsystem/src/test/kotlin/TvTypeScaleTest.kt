package designsystem

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The ten-foot legibility floor: a body sentence read from the couch never
 * drops under 18sp, and a shelf name read across the room is set at roughly
 * double that. Plain `compose-ui` `TextStyle`, not an M3 `Typography` —
 * `:ui-tv` never has material3 on its compile classpath.
 */
class TvTypeScaleTest {
    @Test
    fun bodyMeetsTheTenFootFloor() {
        assertTrue(TvTypeScale.body.fontSize.value >= 18f)
    }

    @Test
    fun titleIsSetForCouchDistance() {
        assertTrue(TvTypeScale.title.fontSize.value in 30f..38f)
    }
}
