package ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.TogetherOffer
import kotlinx.coroutines.flow.MutableStateFlow
import model.Kind
import org.junit.Test
import player.titleLine
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.catalog.BrowsePageTest
import ui.catalog.TitleDetailScreen
import ui.common.FrameKind
import ui.common.LibraryPositions
import ui.common.catalog.LocalTogetherOffers
import ui.common.catalog.TogetherOffers
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The bar a cast shows at the foot of the catalog, as the web's `cast-bar.js`
 * draws it: what the TV plays and a play/pause, opening the remote on a tap
 * and stepping aside while the remote is open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class CastBarTest : BrowsePageTest() {
    private class Slot<T>(override var value: T) : MutableState<T> {
        override fun component1(): T = value

        override fun component2(): (T) -> Unit = { value = it }
    }

    private val set = film("a").copy(rawTitle = "Film a")
    private val cast = TogetherOffer(41, set, "TV", cast = true)
    private var opened = 0
    private var pressed = 0

    private fun bar(offer: TogetherOffer?) = show { CastBar(offer, onOpen = { opened++ }, onPlayPause = { pressed++ }) }

    @Test
    fun aCastShowsTheBar() {
        val shown = castBarOffer(cast, top = null)
        bar(shown)
        compose.onNodeWithText("Playing on TV · Film a").assertExists()
    }

    @Test
    fun anEpisodeIsNamedAsTheRemoteNamesIt() {
        val episode = set.copy(kind = Kind.EPISODE, show = "A Show", season = 1, episodeFirst = 2, episodeLast = 2, rawTitle = "Pilot")
        bar(cast.copy(set = episode))
        compose.onNodeWithText("Playing on TV · ${titleLine(episode)}").assertExists()
        compose.onNodeWithText("A Show", substring = true).assertExists()
    }

    @Test
    fun anOfferShowsNoBar() {
        assertNull(castBarOffer(cast.copy(cast = false), top = null))
        bar(castBarOffer(cast.copy(cast = false), top = null))
        compose.onNodeWithText("Playing on TV", substring = true).assertDoesNotExist()
    }

    @Test
    fun aHiddenTitleShowsNoBar() {
        // A title the profile's catalog hides never becomes an offer, so there is nothing to bar.
        assertNull(castBarOffer(null, top = null))
        bar(null)
        compose.onNodeWithText("Playing on TV", substring = true).assertDoesNotExist()
    }

    @Test
    fun tappingTheBarOpensTheRemote() {
        val at = LibraryPositions(Slot(""))
        show { CastBar(cast, onOpen = { at.openRemote(cast.id) }, onPlayPause = {}) }
        compose.onNodeWithText("Playing on TV · Film a").performClick()
        compose.runOnIdle {
            assertEquals(FrameKind.REMOTE, at.top)
            assertEquals(41, at.remoteId)
        }
    }

    @Test
    fun theBarStepsAsideWhileTheRemoteIsOpenAndReturnsWhenItCloses() {
        val at = LibraryPositions(Slot(""))
        assertEquals(cast, castBarOffer(cast, at.top))
        at.openRemote(41)
        assertNull(castBarOffer(cast, at.top))
        at.pop()
        assertEquals(cast, castBarOffer(cast, at.top))
    }

    @Test
    fun theBarStepsAsideOverThePlayer() {
        val at = LibraryPositions(Slot(""))
        at.openPlayer("a")
        assertNull(castBarOffer(cast, at.top))
    }

    @Test
    fun aTitlePageStillOffersJoinForACast() {
        val offers = TogetherOffers(MutableStateFlow<TogetherOffer?>(cast)) {}
        show {
            CompositionLocalProvider(LocalTogetherOffers provides offers) {
                TitleDetailScreen(set = set, info = null, onPlay = {}, onOpenGenre = {})
            }
        }
        compose.onNodeWithText("▶ Join").assertExists()
    }

    @Test
    fun playPauseMovesTheSessionAndNeverOpensTheRemote() {
        bar(cast)
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(1 to 0, pressed to opened)
    }

    @Test
    fun aPausedCastOffersPlay() {
        bar(cast.copy(playing = false))
        compose.onNodeWithContentDescription("Play").assertExists()
    }
}
