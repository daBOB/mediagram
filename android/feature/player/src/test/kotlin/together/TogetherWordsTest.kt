package player.together

import model.together.Change
import model.together.Noticed
import kotlin.test.Test
import kotlin.test.assertEquals

/** The player's watch-together words are the web player's own (`together-words.js`). */
class TogetherWordsTest {
    private val session = TV_SESSION.copy(pos = 2_480.0, rate = 1.5)

    @Test
    fun eachChangeIsSaidAsTheWebPlayerSaysIt() {
        val said = Change.entries.associateWith { say(Noticed(it, "TV"), session) }

        assertEquals(
            mapOf(
                Change.PLAYED to "Resumed by TV",
                Change.PAUSED to "Paused by TV",
                Change.SOUGHT to "Skipped to 41:20 by TV",
                Change.RATE to "1.5× by TV",
                Change.TITLE to "Next episode by TV",
                Change.JOINED to "TV joined",
                Change.LEFT to "TV left",
            ),
            said,
        )
    }

    @Test
    fun aScreenWithNoLabelIsAnotherScreen() {
        assertEquals("Paused by Another screen", say(Noticed(Change.PAUSED, null), session))
    }

    @Test
    fun theButtonSaysWhatItDoes() {
        assertEquals(
            listOf("Watch together", "Join", "Leave watch together"),
            TogetherButton.entries.map { it.words },
        )
    }

    @Test
    fun aGreyedButtonSaysWhyToAScreenReader() {
        assertEquals("Join", TogetherUi(button = TogetherButton.JOIN, enabled = true).description)
        assertEquals(
            "Watch together, Telegram isn't reachable",
            TogetherUi(button = TogetherButton.START, reason = data.TOGETHER_UNREACHABLE).description,
        )
    }
}
