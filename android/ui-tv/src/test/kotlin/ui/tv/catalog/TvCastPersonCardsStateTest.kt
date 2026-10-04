package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import model.Credit
import model.Kind
import model.TitleCredits
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A title's Cast tab on television draws its people as the web's
 * `personCard` and the phone's Cast tab do — a round portrait, the name
 * under it, the character under that — rather than as 2:3 poster plates,
 * and the remote walks them like any row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCastPersonCardsStateTest : TvScreenStateTest() {
    private val film = set("f", Kind.MOVIE, "A Film", addedAt = 0)
    private val ada = Credit(personId = 7L, name = "Ada Actor", role = "Herself", portraitPath = null)
    private val bo = Credit(personId = 8L, name = "Bo Actor", role = "Himself", portraitPath = null)
    private val credits = TitleCredits(cast = listOf(ada, bo), crew = emptyList())

    /** Side by side; the face as tall as the card is wide, as a circle is; the name under it and the character under the name. */
    @Test
    fun aCastMemberIsARoundPortraitWithTheirNameAndCharacterUnderIt() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits) }
        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)

        val first = card("Ada Actor").getUnclippedBoundsInRoot()
        val second = card("Bo Actor").getUnclippedBoundsInRoot()
        assertTrue(second.left >= first.right, "two people should stand side by side")
        assertEquals(first.top, second.top)
        assertTrue(first.width < 200.dp, "a card should be a portrait's width, got ${first.width}")

        // The initials stand in the middle of the face: half a card's width
        // down for a circle, where a 2:3 plate would put them a third lower.
        val initials = unmerged("AA")
        val faceMiddle = (initials.top + initials.bottom) / 2
        assertTrue(abs((faceMiddle - (first.top + first.width / 2)).value) < 4f, "the face should be as tall as it is wide, its middle at $faceMiddle")

        val name = unmerged("Ada Actor")
        val role = unmerged("Herself")
        assertTrue(name.top >= first.top + first.width * 0.95f, "the name should sit under the round portrait")
        assertTrue(role.top >= name.bottom, "the character should sit under the name")
    }

    @Test
    fun pressingACardOpensThatPerson() {
        var opened: Long? = null
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, onOpenPerson = { opened = it }) }
        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)

        card("Bo Actor").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(8L, opened)
    }

    /** Right walks the cards; with no crew line, Up lands on the Cast tab, not the tab nearest; Down comes back into the row. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theRemoteWalksTheCardsAndUpReachesTheCastTab() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, restoreKey = "7") }
        card("Ada Actor").assertIsFocused()

        press(Key.DirectionRight)
        card("Bo Actor").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("Cast").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNode(isFocused() and (hasText("Ada Actor") or hasText("Bo Actor"))).assertExists()
    }

    private fun card(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun unmerged(text: String) = compose.onNode(hasText(text), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
