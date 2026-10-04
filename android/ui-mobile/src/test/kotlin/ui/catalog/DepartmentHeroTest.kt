package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DepartmentHero] itself — the words, the quote's placement and width, the
 * hero's own single tap target (the quote's credit, never the hero itself),
 * and Solid hiding its art — checked directly rather than through a whole
 * department page, the same way [ui.catalog.home.HomeCoverTest] checks the
 * cover directly.
 */
@RunWith(RobolectricTestRunner::class)
class DepartmentHeroTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val lead =
        MediaSet(
            setId = "lead", kind = Kind.MOVIE, title = "a lowercase title", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
            totalBytes = 0, backdropPath = "backdrop.jpg", tagline = "a quotable line",
        )

    private fun show(
        title: String = "series",
        backdrop: Backdrop = Backdrop.DEFAULT,
        lead: MediaSet? = this.lead,
        onOpenTitle: ((String) -> Unit)? = null,
        franchiseTitle: Boolean = false,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalBackdrop provides backdrop) {
                        DepartmentHero(
                            title = title, line = "48 shows", lead = lead,
                            onOpenTitle = onOpenTitle, franchiseTitle = franchiseTitle,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The real measured layout behind a node's text — `lineCount`/`hasVisualOverflow` tell wrapped or clipped text apart from a one-line match, which a plain `onNodeWithText` cannot: its semantics carry the full string regardless of how it actually laid out. */
    private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun wordsAreUppercaseRegardlessOfTheirOwnCase() {
        show()
        compose.onNodeWithText("SERIES").assertIsDisplayed()
        compose.onNodeWithText("48 shows").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun onAWideWindowTheQuoteSitsAboveTheTitleNotBelowIt() {
        show()
        // Unmerged: with a link inside it, the quote's own credit carries
        // its own semantics node distinct from the hero's title — a
        // merged-tree lookup would still find each word at its own real
        // position here, but every other bounds check in this file relies
        // on the unmerged tree too, so this stays consistent with them.
        val quoteTop = compose.onNodeWithText("“a quotable line”", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val titleTop = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertTrue(quoteTop < titleTop, "the quote (top-right) should sit above the bottom-aligned title on a wide window")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun onACompactWindowTheQuoteIsHidden() {
        // The web hides `.dept-quote` below 900px (`departments.css:91`).
        show()
        compose.onNodeWithText("“a quotable line”", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Below 900px the web sets the words onto the art strip's faded foot —
     * `padding-top: 48vw` over a `64vw` strip placed behind them — rather
     * than under it; a phone does the same, its title 48vw down.
     */
    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun onAPhoneTheWordsOverlapTheArtStripsFadedFoot() {
        show()
        val art = compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(256f, art.bottom.value, 1f)
        assertEquals(192f, title.top.value, 1f)
        assertTrue(title.top < art.bottom, "the title should start on the strip's own foot, not under it")
    }

    /** A portrait tablet (Medium) keeps the hero it had: its words under the strip. */
    @Test
    @Config(sdk = [35], qualifiers = "w700dp-h1200dp")
    fun onAPortraitTabletTheWordsStayUnderTheArtStrip() {
        show()
        val art = compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(title.top >= art.bottom, "a portrait tablet's title should sit under the strip, at ${title.top} against ${art.bottom}")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w880dp-h900dp")
    fun aWindowInTheOldGapBandDrawsTheWideLayoutNotCompact() {
        // 880dp is EXPANDED (`androidx.window.core.layout.WindowWidthSizeClass`,
        // ≥840dp) — the same signal `ui.chrome.LibraryHome` already bleeds
        // the bar on. A hero still reading its own, narrower 900dp
        // breakpoint went compact here while the bar above it had already
        // decided the window was wide, leaving a bar-height gap between a
        // compact copy block and art it no longer overlapped.
        show()
        compose.onNodeWithText("“a quotable line”", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [35], qualifiers = "w360dp-h640dp")
    fun theLongestDepartmentNameNeverBreaksMidWordOnAPhone() = assertTitleIsOneLineWithNoOverflow("documentaries")

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [35], qualifiers = "w777dp-h1200dp")
    fun theLongestDepartmentNameNeverBreaksMidWordOnAPortraitTablet() = assertTitleIsOneLineWithNoOverflow("documentaries")

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun theLongestDepartmentNameNeverBreaksMidWordOnALandscapeTablet() = assertTitleIsOneLineWithNoOverflow("documentaries")

    /**
     * Truncation ("DOCUMENT…") or a mid-word wrap ("DOCUMENT"/"ARIES" as two
     * lines) both leave `onNodeWithText(...).assertIsDisplayed()` passing —
     * a text node's semantics carry the full string regardless of how it
     * laid out, and Robolectric's default LEGACY graphics measure text at
     * about one pixel per character, so a real overflow never has a chance
     * to happen either way. `GraphicsMode.NATIVE` and the real
     * [TextLayoutResult] this reads are what make this guard able to fail.
     */
    private fun assertTitleIsOneLineWithNoOverflow(title: String) {
        show(title = title)
        val result = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).textLayoutResult()
        assertEquals(1, result.lineCount, "the title should shrink to fit one line, never wrap onto a second")
        assertTrue(!result.hasVisualOverflow, "the title should shrink to fit, never clip")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun theHerosOwnHeightMatchesTheWebsClampFloor() {
        // `fluid(420f, .58f, 600f, 777f)` = 450.66dp — a regression guard
        // for the trap `matchParentSize()` (in `DepartmentHeroLayouts.kt`)
        // exists for: a child measuring against the wrong, unresolved
        // height would throw this off silently, with nothing else here to
        // catch it.
        show()
        val bounds = compose.onNodeWithTag(DEPARTMENT_HERO_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val heightDp = (bounds.bottom - bounds.top).value
        assertTrue(heightDp in 449f..452f, "expected the hero's own height near 450.66dp, was ${heightDp}dp")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun solidHidesTheArtAndTheQuoteEvenOnAWideWindow() {
        show(backdrop = Backdrop.SOLID)
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("“a quotable line”", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun theHeroItselfIsNeverATapTargetEvenWithALeadAndAnOpenCallback() {
        show(onOpenTitle = {})
        // The web only ever links the quote's own credit
        // (`department-hero.js`) — never the picture or the words beside
        // it. The one tap target anywhere in the hero, if any, is that
        // credit line, checked below.
        val clickables = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertEquals(1, clickables.size, "only the quote's own credit should ever be a tap target")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun theQuotesCreditOpensTheLeadWhenGivenACallback() {
        var opened: String? = null
        show(onOpenTitle = { id -> opened = id })
        compose.onNodeWithText("— A LOWERCASE TITLE", useUnmergedTree = true).performClick()
        assertEquals("lead", opened, "the credit should open the lead's own setId")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun theHeroDrawsNothingClickableAtAllWithoutACallback() {
        show(onOpenTitle = null)
        val clickables = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertEquals(0, clickables.size, "a hero with nothing to open should draw no tap target at all — Documentaries' own case")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun theQuotesOwnCapMatchesTheWebsSeventeenRemNotANarrowerOne() {
        val longTagline = "A tagline long enough on its own that it must wrap across more than one line no matter how wide this quote is allowed to be"
        show(lead = lead.copy(tagline = longTagline))
        val bounds = compose.onNodeWithTag(DEPT_HERO_QUOTE_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val widthDp = (bounds.right - bounds.left).value
        // 272dp (`departments.css:41`) plus the quote's own inner padding on
        // each side — a modifier order that capped the width *before* that
        // padding measured a quarter narrower than this (203dp), wrapping
        // the same string across an extra line the web never needed.
        assertTrue(widthDp > 280f, "expected the quote near its own 304dp cap (272dp text + 32dp padding), was ${widthDp}dp")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun aFranchiseTitleWrapsAcrossLinesInsteadOfShrinkingOntoOne() {
        show(title = "The Lord of the Rings Collection", franchiseTitle = true)
        val result = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).textLayoutResult()
        assertTrue(result.lineCount > 1, "a franchise name long enough to need it should wrap, not shrink onto one line — was ${result.lineCount}")
        assertTrue(result.lineCount <= 3, "a franchise title should never exceed its own three-line ceiling — was ${result.lineCount}")
    }
}
