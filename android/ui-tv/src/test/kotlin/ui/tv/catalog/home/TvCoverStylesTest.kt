package ui.tv.catalog.home

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import designsystem.TvStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvBarClearance
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.set

/** The cover story per style: mediagram's capitals, Apple's bold title as written, Google's inset card — the same words and buttons in each. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCoverStylesTest : TvScreenStateTest() {
    private val film = set("f1", Kind.MOVIE, "Die Hard", addedAt = 1)
    private val second = set("f2", Kind.MOVIE, "Heat", addedAt = 2)

    @Test
    fun mediagramSetsTheTitleInCapitals() {
        show { TvCoverSlide(set = film, minHeight = 400.dp) }
        compose.onNodeWithText("DIE HARD").assertExists()
    }

    @Test
    fun appleSetsTheTitleAsWrittenInBold() {
        show(TvStyle.APPLE) { TvCoverSlide(set = film, minHeight = 400.dp) }
        compose.onNodeWithText("DIE HARD").assertDoesNotExist()
        assertEquals(FontWeight.Bold, styleOf("Die Hard").fontWeight)
    }

    @Test
    fun googleDrawsTheStoryAndItsButtonsInsideAnInsetCard() {
        show(TvStyle.GOOGLE) { cover(listOf(film)) }
        val card = compose.onNodeWithTag(InsetCardTag).getUnclippedBoundsInRoot()
        val watch = compose.onNodeWithContentDescription("Watch now: Die Hard").getUnclippedBoundsInRoot()
        assertTrue(card.left > 0.dp, "inset from the screen's edge: $card")
        assertTrue(watch.left >= card.left + InsetCardPadding && watch.bottom <= card.bottom, "the buttons sit inside the card: $watch in $card")
        assertEquals(FontWeight.Bold, styleOf("Die Hard").fontWeight)
    }

    @Test
    fun googlesCoverStillTurnsToTheNextFilm() {
        compose.mainClock.autoAdvance = false
        show(TvStyle.GOOGLE) { cover(listOf(film, second)) }
        compose.onNodeWithText("Heat").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(9_000 + 1_400 + 100)
        compose.onNodeWithText("Heat").assertExists()
    }

    @Test
    fun googlesCardHoldsALongStoryAtTheCoversMinimum() {
        val long =
            set("f3", Kind.MOVIE, "The Lord of the Rings: The Fellowship of the Ring", addedAt = 3, year = 1995, durationSecs = 7200)
                .copy(tagline = "A meek hobbit of the Shire and eight companions set out on a journey to destroy the powerful One Ring and save Middle-earth from the Dark Lord Sauron.")
        show(TvStyle.GOOGLE) { cover(listOf(long)) }
        val card = compose.onNodeWithTag(InsetCardTag).getUnclippedBoundsInRoot()
        val kicker = compose.onNodeWithText("FEATURED TODAY").getUnclippedBoundsInRoot()
        val watch = compose.onNodeWithContentDescription("Watch now: ${long.title}").getUnclippedBoundsInRoot()
        val bounds = "card $card, kicker $kicker, watch $watch"
        assertTrue(kicker.top >= card.top + InsetCardPadding - 0.5.dp, "words start inside the card: $bounds")
        assertTrue(watch.bottom <= card.bottom, "buttons end inside the card: $bounds")
        assertTrue(card.top >= TvBarClearance, "card clears the bar: $bounds")
    }

    @androidx.compose.runtime.Composable
    private fun cover(films: List<model.MediaSet>) =
        TvHomeCover(
            films = films,
            watchlist = emptySet(),
            initialFilmId = null,
            onPlay = {},
            onOpenTitle = {},
            onToggleWatchlist = { _, _ -> },
            arrivalFocus = remember { FocusRequester() },
            upExit = remember { FocusRequester() },
        )
}
