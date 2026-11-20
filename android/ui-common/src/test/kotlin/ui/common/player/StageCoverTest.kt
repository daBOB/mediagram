package ui.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageCoverTest {
    @Test
    fun anAudioOnlyTitleShowsItsPoster() = assertEquals("/p.jpg", stageCoverOf(audioOnly = true, posterPath = "/p.jpg"))

    @Test
    fun aTitleWithAPictureShowsNoCover() = assertNull(stageCoverOf(audioOnly = false, posterPath = "/p.jpg"))

    @Test
    fun anAudioOnlyTitleWithoutAPosterStaysBlack() = assertNull(stageCoverOf(audioOnly = true, posterPath = null))
}
