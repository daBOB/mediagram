package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import designsystem.Overscan
import model.Credit
import model.Kind
import model.Progress
import model.TitleCredits
import playback.FilmPreloadState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.common.catalog.TitlePreloadUi
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TvTitlePage]'s spread: the title, its facts and the provider's words over
 * the art, and Play — or "Resume from", with where — holding the remote
 * from the moment the page appears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTitlePageStateTest : TvScreenStateTest() {
    private val film =
        set("f", Kind.MOVIE, "A Film", addedAt = 0, year = 2004, durationSecs = 6780)
            .copy(fsk = "12", genres = listOf("Drama", "Comedy", "Crime", "War"))

    @Test
    fun aTitleNotYetStartedOffersPlayFocusedUnderItsFacts() {
        var played = false
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = { played = true }) }

        compose.onNodeWithText("A Film").assertExists()
        // `film-page.js`'s own facts line: year, runtime, rating, then the first three genres.
        compose.onNodeWithText("2004 · 1h 53m · FSK 12 · Drama, Comedy, Crime").assertExists()
        compose.onNodeWithText("▶ Play").assertIsFocused()
        compose.onNodeWithText("▶ Play").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(played)
    }

    /** The overview and the tagline are the spread's own, as the web adds them once the provider answers. */
    @Test
    fun theProvidersWordsSitInTheSpreadAndTheTaglineIsItsQuote() {
        val info = TitleInfo(overview = "What happens.", tagline = "A line of marketing", genres = null, rating = null, network = null, status = null)
        show { TvTitlePage(set = film.copy(backdropPath = "/nowhere/backdrop.jpg"), info = info, progress = null, onPlay = {}) }

        compose.onNodeWithText("What happens.").assertExists()
        compose.onNodeWithTag(TvTitleSpreadQuoteTag).assertExists()
        compose.onNodeWithText("“A line of marketing”").assertExists()
    }

    /** Solid draws no art, and so no quote over it — the web's `.spread.no-art`. */
    @Test
    fun withNoArtThereIsNoQuote() {
        val info = TitleInfo(overview = null, tagline = "A line of marketing", genres = null, rating = null, network = null, status = null)
        show { TvTitlePage(set = film, info = info, progress = null, onPlay = {}) }

        compose.onNodeWithTag(TvTitleSpreadQuoteTag).assertDoesNotExist()
    }

    /** A long synopsis is clamped in the spread, as the web's is, so nothing is out of the remote's reach: Down goes to the tabs. */
    @Test
    fun downFromPlayEntersTheTabThatIsShowing() {
        val overview = "A long synopsis. ".repeat(80).trim()
        val info = TitleInfo(overview = overview, tagline = null, genres = null, rating = null, network = null, status = null)
        show { TvTitlePage(set = film, info = info, progress = null, onPlay = {}) }
        compose.onNodeWithText("Details").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("▶ Play").performSemanticsAction(SemanticsActions.RequestFocus)

        compose.onNodeWithText("▶ Play").performKeyInput { pressKey(Key.DirectionDown) }

        // Details, the tab showing — not Overview, the tab nearest Play.
        compose.onNodeWithText("Details").assertIsFocused()
    }

    /** Up out of a panel lands on that panel's own tab, wherever the stop it leaves from sits under the row. */
    @Test
    fun upFromAPanelEntersItsOwnTabNotTheNearest() {
        val other = set("g", Kind.MOVIE, "Another Film", addedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, similar = listOf(other)) }
        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Another Film").assertIsFocused()

        compose.onNodeWithText("Another Film").performKeyInput { pressKey(Key.DirectionUp) }

        compose.onNodeWithText("Similar").assertIsFocused()
    }

    /**
     * A panel of facts has no stop to scroll by, so reaching the tabs lifts
     * the tab row to the safe line, giving the panel under it the rest of the
     * screen. Robolectric sets text about twice as tall as the box does, so
     * this pins where the row lands rather than that all nine Details rows fit.
     */
    @Test
    fun reachingTheTabsLiftsThemToTheSafeLine() {
        val full =
            film.copy(
                backdropPath = "/nowhere/backdrop.jpg", totalBytes = 15_246_565_376, container = "mkv", vcodec = "hevc",
                acodec = "eac3", quality = "1080p", hdr = "HDR10", partCount = 5, alang = listOf("en"), slang = listOf("de"),
            )
        val info = TitleInfo(overview = "A long synopsis. ".repeat(20), tagline = null, genres = null, rating = null, network = null, status = null)
        show { TvTitlePage(set = full, info = info, progress = null, onPlay = {}) }
        compose.onNodeWithText("Details").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("▶ Play").performSemanticsAction(SemanticsActions.RequestFocus)

        compose.onNodeWithText("▶ Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        val screen = compose.onRoot().getBoundsInRoot()
        val tabs = compose.onNodeWithText("Details").getUnclippedBoundsInRoot()
        assertEquals(screen.top + Overscan.vertical, tabs.top, "the tab row sits on the safe line")
        val first = compose.onNodeWithText("QUALITY").getUnclippedBoundsInRoot()
        assertTrue(first.top > tabs.bottom && first.bottom < screen.bottom - Overscan.vertical, "the sheet starts on screen, under the tabs")
    }

    /** The player's own rule: a glance at the opening, or a position in the credits, starts from the top. */
    @Test
    fun aPositionThePlayerWouldNotResumeFromSaysPlay() {
        for (at in listOf(5.0, 6760.0)) {
            show { TvTitlePage(set = film, info = null, progress = Progress("f", at = at, duration = 6780.0, updatedAt = 1), onPlay = {}) }
            compose.onNodeWithText("▶ Play").assertIsFocused()
            compose.onNodeWithText("Resume", substring = true).assertDoesNotExist()
            close()
        }
    }

    /** `film-page.js`'s own pill: "Resume from" and the clock, in place of a separate line saying where. */
    @Test
    fun aTitleUnderwayOffersResumeFromWhereItStopped() {
        val stopped = Progress("f", at = 750.0, duration = 6780.0, updatedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = stopped, onPlay = {}) }

        compose.onNodeWithText("▶ Resume from 12:30").assertIsFocused()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
    }

    /** The pills wrap inside the copy's own width, as `.spread-actions` sits in `.spread-copy`, never running on under the tagline's quote. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aFullRowOfPillsStaysClearOfTheQuote() {
        val info = TitleInfo(overview = "What happens.", tagline = "A line of marketing", genres = null, rating = null, network = null, status = null)
        show {
            TvTitlePage(
                set = film.copy(backdropPath = "/nowhere/backdrop.jpg"), info = info, progress = null, onPlay = {},
                preload = TitlePreloadUi(FilmPreloadState.Done, serverLine = null, onToggle = {}, onRemove = {}, onOpenStorage = {}),
                onToggleEditorsChoice = {},
            )
        }

        val quote = compose.onNodeWithTag(TvTitleSpreadQuoteTag).getUnclippedBoundsInRoot()
        for (pill in listOf("▶ Play", "Preloaded ✓", "Remove preload", "+ My List", "⋯")) {
            val bounds = compose.onNodeWithText(pill).getUnclippedBoundsInRoot()
            assertTrue(bounds.right <= quote.left, "$pill runs under the quote: $bounds against $quote")
        }
    }

    /**
     * Back from the franchise a "Part of" link opened lands on that link, not
     * on Play — and the franchise's id is never read as a person's, though a
     * cast member here shares it.
     */
    @Test
    fun comingBackFromTheFranchiseLandsOnItsPartOfLink() {
        val saga = listOf(film.copy(collectionId = 5, collectionName = "The Saga"), set("g", Kind.MOVIE, "Sequel", addedAt = 1).copy(collectionId = 5, collectionName = "The Saga"))
        var opened: Long? = null
        val credits = TitleCredits(cast = listOf(Credit(personId = 5L, name = "Ada Actor", role = "Herself", portraitPath = null)), crew = emptyList())
        show {
            TvTitlePage(
                set = saga[0], info = null, progress = null, onPlay = {}, allFilms = saga, credits = credits,
                onOpenFranchise = { opened = it }, restoreKey = franchiseRestoreKey(5),
            )
        }

        compose.onNodeWithText("The Saga").assertIsFocused()
        compose.onNodeWithText("The Saga").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(5L, opened)
    }
}
