package ui.tv.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.Kind
import model.MediaSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun drawsTheKickerUppercaseTitleAndLine() {
        show { TvDepartmentHero(kicker = "Only in your library", title = "Movies", line = "four films", lead = lead()) }

        compose.onNodeWithText("ONLY IN YOUR LIBRARY").assertIsDisplayed()
        compose.onNodeWithText("MOVIES").assertIsDisplayed()
        compose.onNodeWithText("four films").assertIsDisplayed()
    }

    @Test
    fun solidHidesTheArtAndItsQuoteEvenWithATaggedLead() {
        show {
            CompositionLocalProvider(LocalBackdrop provides Backdrop.SOLID) {
                TvDepartmentHero(kicker = "Only in your library", title = "Movies", line = "four films", lead = lead())
            }
        }

        compose.onNodeWithText("MOVIES").assertIsDisplayed()
        compose.onAllNodesWithText("“A tagline”").assertCountEquals(0)
    }

    @Test
    fun noTaglineDrawsNoQuoteEvenWithArt() {
        show { TvDepartmentHero(kicker = "Only in your library", title = "Movies", line = "four films", lead = lead(tagline = null)) }
        compose.onAllNodesWithText("“A tagline”").assertCountEquals(0)
    }

    @Test
    fun noArtDrawsNoQuoteEvenWithATagline() {
        show { TvDepartmentHero(kicker = "Only in your library", title = "Movies", line = "four films", lead = lead(backdropPath = null)) }
        compose.onAllNodesWithText("“A tagline”").assertCountEquals(0)
    }

    @Test
    fun artAndATaglineTogetherDrawTheQuote() {
        show { TvDepartmentHero(kicker = "Only in your library", title = "Movies", line = "four films", lead = lead()) }
        compose.onNodeWithText("“A tagline”").assertIsDisplayed()
    }

    /** Never a focus stop, quote included — see [TvDepartmentHero]'s own doc on why. */
    @Test
    fun noNodeInsideTheHeroIsClickableOrFocusable() {
        show { TvDepartmentHero(kicker = "Only in your library", title = "Movies", line = "four films", lead = lead()) }

        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TvDepartmentHeroTestTag))).assertCountEquals(0)
    }
}
