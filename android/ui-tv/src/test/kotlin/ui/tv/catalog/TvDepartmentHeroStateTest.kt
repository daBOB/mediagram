package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Overscan
import model.Kind
import model.MediaSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.chrome.TvContentGutter
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * [TvDepartmentHero] over the television's own fixed 960dp width, the width
 * every real screen this hero draws on has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvDepartmentHeroStateTest : TvScreenStateTest() {
    private fun lead(backdropPath: String? = "/bd", tagline: String? = "A tagline") =
        MediaSet(
            setId = "lead", kind = Kind.MOVIE, title = "Lead Film", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, backdropPath = backdropPath, tagline = tagline,
        )

    @Test
    fun drawsTheUppercaseTitleAndLine() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead()) }

        compose.onNodeWithText("MOVIES").assertIsDisplayed()
        compose.onNodeWithText("four films").assertIsDisplayed()
    }

    @Test
    fun solidHidesTheArtAndItsQuoteEvenWithATaggedLead() {
        show {
            CompositionLocalProvider(LocalBackdrop provides Backdrop.SOLID) {
                TvDepartmentHero(title = "Movies", line = "four films", lead = lead())
            }
        }

        compose.onNodeWithText("MOVIES").assertIsDisplayed()
        compose.onAllNodesWithText("“A tagline”").assertCountEquals(0)
    }

    @Test
    fun noTaglineDrawsNoQuoteEvenWithArt() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead(tagline = null)) }
        compose.onAllNodesWithText("“A tagline”").assertCountEquals(0)
    }

    @Test
    fun noArtDrawsNoQuoteEvenWithATagline() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead(backdropPath = null)) }
        compose.onAllNodesWithText("“A tagline”").assertCountEquals(0)
    }

    @Test
    fun artAndATaglineTogetherDrawTheQuote() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead()) }
        compose.onNodeWithText("“A tagline”").assertIsDisplayed()
    }

    /**
     * A long title plus a long, wrapping tagline once actually overlapped
     * on Collections at the real 960dp width — [HeroCopyMaxWidth]'s own
     * doc. Pinned as arithmetic rather than a rendered measurement: under
     * Robolectric's own font fallback, "COLLECTIONS" measures narrower than
     * on a real device (the same gap `TvCoverSlideStateTest`'s own doc
     * names), so a bounds-based assertion here would pass even at the old,
     * overlapping 640dp width — this instead proves the *maximum* either
     * column can ever reach, which a real device's own wider metrics still
     * has to answer to.
     */
    @Test
    fun theCopyAndQuoteColumnsNeverOverlapAtTheFixedTvWidth() {
        val screenWidth = 960f
        val copyRight = TvContentGutter.value + HeroCopyMaxWidth.value
        val quoteLeft = screenWidth - Overscan.horizontal.value - HeroQuoteMaxWidth.value
        assertTrue(
            copyRight <= quoteLeft,
            "the copy column's own maximum right edge ${copyRight}dp reaches past the quote's own maximum left edge ${quoteLeft}dp",
        )
    }

    /** Never a focus stop, quote included — see [TvDepartmentHero]'s own doc on why. */
    @Test
    fun noNodeInsideTheHeroIsClickableOrFocusable() {
        show { TvDepartmentHero(title = "Movies", line = "four films", lead = lead()) }

        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TvDepartmentHeroTestTag))).assertCountEquals(0)
    }

    /**
     * [TvHeroWordsAboveQuote]'s own decision, driven with fixed-size fakes
     * rather than real words/a real quote: the actual overlap this reports
     * — a real title plus a real tagline — depends on font metrics
     * Robolectric cannot be held to (this file's own doc on
     * [theCopyAndQuoteColumnsNeverOverlapAtTheFixedTvWidth] says so
     * already); driving the layout with a fake of a known height instead
     * proves the *rule* holds for any words tall enough to ask for it,
     * not only whichever string Collections happens to hold today.
     */
    @Test
    fun quoteSitsAtItsOwnFloorWhenTheWordsLeaveRoom() {
        showHeroLayout(heroHeight = 300.dp, wordsHeight = 100.dp, quoteHeight = 60.dp, floor = 50.dp, gap = 20.dp)

        assertTop("quote", 50.dp)
        assertTop("words", 200.dp) // Still flush against the bottom: 300 - 100.
    }

    @Test
    fun quoteRetreatsAboveTheWordsRatherThanOverlapThem() {
        showHeroLayout(heroHeight = 300.dp, wordsHeight = 200.dp, quoteHeight = 60.dp, floor = 50.dp, gap = 10.dp)

        // Its own floor (50) would land inside the words (which now start
        // at 100); pushed up to sit flush above them instead: 100 - 10 - 60.
        assertTop("quote", 30.dp)
    }

    @Test
    fun quoteIsLeftOutWhenEvenTheVeryTopHasNoRoomForIt() {
        showHeroLayout(heroHeight = 300.dp, wordsHeight = 280.dp, quoteHeight = 60.dp, floor = 50.dp, gap = 10.dp)

        compose.onNodeWithTag("quote").assertDoesNotExist()
    }

    private fun showHeroLayout(
        heroHeight: Dp,
        wordsHeight: Dp,
        quoteHeight: Dp,
        floor: Dp,
        gap: Dp,
    ) {
        show {
            Box(Modifier.size(400.dp, heroHeight)) {
                TvHeroWordsAboveQuote(
                    quoteTopFloor = floor,
                    gap = gap,
                    words = { Box(Modifier.testTag("words").size(200.dp, wordsHeight)) },
                    quote = { Box(Modifier.testTag("quote").size(100.dp, quoteHeight)) },
                )
            }
        }
    }

    private fun assertTop(
        tag: String,
        expected: Dp,
    ) {
        val expectedPx = with(compose.density) { expected.toPx() }
        val top = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        assertTrue(abs(top - expectedPx) <= 1f, "$tag top at ${top}px, expected ${expectedPx}px")
    }
}
