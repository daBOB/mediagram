package ui.tv.catalog

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import catalog.EditorialPicks
import catalog.Latest
import catalog.MagazineHome
import catalog.TogetherOffer
import kotlinx.coroutines.flow.MutableStateFlow
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.catalog.LocalTogetherOffers
import ui.common.catalog.TogetherOffers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home's Join on the television, as `together-join.js` offers it on the web:
 * a focusable plate under the cover, which takes the remote as Home appears.
 * A session heard or ended while Home is up never throws the remote away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTogetherJoinTest : TvScreenStateTest() {
    private var joined = 0
    private val shared = set("shared", Kind.MOVIE, "Shared Film", addedAt = 0)
    private val coverFilm = set("cover", Kind.MOVIE, "Cover Film", addedAt = 0)
    private val plate = hasText("Watching together: Shared Film · TV, Tablet")
    private val watchNow = hasContentDescription("Watch now: Cover Film")

    /** The cover's own stops: Watch now, My List and Details. */
    private val inCover = watchNow or hasText("My List", substring = true) or hasText("Details")

    private fun offer(set: MediaSet) = TogetherOffer(41, set, "TV, Tablet")

    private fun offers(flow: MutableStateFlow<TogetherOffer?>) = TogetherOffers(flow) { joined++ }

    private fun ok() = compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun home(
        together: MutableStateFlow<TogetherOffer?>,
        cover: Boolean = true,
    ) = show {
            CompositionLocalProvider(LocalTogetherOffers provides offers(together)) {
                TvHome(
                    magazine =
                        MagazineHome(
                            editorial =
                                EditorialPicks(
                                    cover = listOfNotNull(coverFilm.takeIf { cover }),
                                    features = emptyList(),
                                    quote = null,
                                    thisMonth = emptyList(),
                                ),
                            resumeCards = emptyList(),
                            recentlyAdded = emptyList(),
                            recentlyAddedTotal = 0,
                        ),
                    latest = Latest(emptyList(), emptyList(), emptyList(), 0, 0, 0),
                    watch = WatchSnapshot.Empty,
                    listState = rememberLazyListState(),
                    onPlay = {},
                    onOpenTitle = {},
                    onOpenCollection = {},
                    onToggleWatchlist = { _, _ -> },
                    onSeeAll = {},
                    upExit = remember { FocusRequester() },
                )
            }
        }

    @Test
    fun theSessionsPlateSitsUnderTheCoverTakesTheRemoteAndJoins() {
        home(MutableStateFlow(offer(shared)))

        compose.onNode(plate).assertIsFocused()
        val plateTop = compose.onNode(plate).getUnclippedBoundsInRoot().top
        assertTrue(plateTop >= compose.onNode(watchNow).getUnclippedBoundsInRoot().bottom, "the plate sits under the cover")
        ok()
        assertEquals(1, joined)

        // Up is the cover, at whichever of its stops sits nearest.
        press(Key.DirectionUp)
        compose.onNode(isFocused() and inCover).assertExists()
    }

    @Test
    fun withNoSessionTheCoverTakesTheRemoteAsBefore() {
        home(MutableStateFlow(null))
        compose.onNode(watchNow).assertIsFocused()
        compose.onNodeWithText("Watching together", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSessionHeardAfterHomeAppearedLeavesTheRemoteWhereItIs() {
        val together = MutableStateFlow<TogetherOffer?>(null)
        home(together)
        compose.onNode(watchNow).assertIsFocused()

        compose.runOnUiThread { together.value = offer(shared) }
        compose.waitForIdle()

        compose.onNode(plate).assertExists()
        compose.onNode(watchNow).assertIsFocused()
    }

    @Test
    fun aSessionEndingUnderTheRemoteHandsItToThePageNotTheBar() {
        val together = MutableStateFlow<TogetherOffer?>(offer(shared))
        home(together)
        compose.onNode(plate).assertIsFocused()

        compose.runOnUiThread { together.value = null }
        compose.waitForIdle()

        compose.onNode(plate).assertDoesNotExist()
        // Nothing under it here, so the remote goes up to the cover.
        compose.onNode(isFocused() and inCover).assertExists()
    }

    /** Nothing else on the page to take the remote: the ended session's plate stays under it, and OK there joins nothing. */
    @Test
    fun aPlateKeptForTheRemoteAfterItsSessionEndedJoinsNothing() {
        val together = MutableStateFlow<TogetherOffer?>(offer(shared))
        home(together, cover = false)
        compose.onNode(plate).assertIsFocused()

        compose.runOnUiThread { together.value = null }
        compose.waitForIdle()

        compose.onNode(plate).assertIsFocused()
        ok()
        assertEquals(0, joined)
    }
}
