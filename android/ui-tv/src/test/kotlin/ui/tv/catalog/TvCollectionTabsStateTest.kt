package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeriesResumePick
import model.Credit
import model.Kind
import model.TitleCredits
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvCollection]'s own tabs — Episodes/About/Cast/Similar — this phase adds:
 * Cast and Similar are gated on having something to show, tab selection
 * survives a state update, and the [SeriesResumePick] pill sits in the
 * Episodes tab's own header, above the season wall or the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCollectionTabsStateTest : TvScreenStateTest() {
    private val show =
        Entry.Collection(
            key = "SHOW/A Show",
            kind = CollectionKind.SHOW,
            name = "A Show",
            posterPath = null,
            posterKey = null,
            count = 1,
            chapters = 1,
            divisions = listOf(Division("Season 1", 1, listOf(set("e1", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0)), emptyList())),
        )

    @Test
    fun withNoCastOrSimilarOnlyEpisodesAndAboutAreOffered() {
        show { TvCollection(show, info = null, watch = WatchSnapshot.Empty, posterPath = { null }, onOpenTitle = {}, onOpenSeason = {}) }

        listOf("Episodes", "About").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Cast").assertDoesNotExist()
        compose.onNodeWithText("Similar").assertDoesNotExist()
        // Episodes is the default tab, unchanged from before this phase.
        compose.onNodeWithText("1. Pilot").assertIsFocused()
    }

    @Test
    fun castGetsItsOwnTabAndPressingAPersonOpensThem() {
        var opened: Long? = null
        val credits = TitleCredits(cast = listOf(Credit(personId = 4L, name = "Ada Actor", role = "Herself", portraitPath = null)), crew = emptyList())
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                posterPath = { null },
                onOpenTitle = {},
                onOpenSeason = {},
                credits = credits,
                onOpenPerson = { opened = it },
            )
        }

        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Ada Actor").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(4L, opened)
    }

    @Test
    fun similarGetsItsOwnTabAndPressingAShowOpensIt() {
        var opened: String? = null
        val other =
            Entry.Collection(
                key = "SHOW/Another Show",
                kind = CollectionKind.SHOW,
                name = "Another Show",
                posterPath = null,
                posterKey = null,
                count = 1,
                chapters = 1,
                divisions = emptyList(),
            )
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                posterPath = { null },
                onOpenTitle = {},
                onOpenSeason = {},
                similar = listOf(other),
                onOpenCollection = { opened = it },
            )
        }

        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Another Show").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("SHOW/Another Show", opened)
    }

    @Test
    fun theResumePillSitsInTheEpisodesHeaderAndPlaysItsOwnEpisode() {
        var played: String? = null
        val pick = SeriesResumePick(set = set("e1", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0, episode = 1), at = 30.0, verb = ResumeVerb.RESUME)
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                posterPath = { null },
                onOpenTitle = {},
                onOpenSeason = {},
                resume = pick,
                onResume = { played = it },
            )
        }

        compose.onNodeWithText("▶ Resume", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("e1", played)
    }

    /**
     * A page rebuilt fresh on the way back from a person's page (this page
     * is torn down while that one is shown, not kept alive underneath it)
     * still lands on Cast, and on that person, because [restoreKey] — not a
     * `rememberSaveable` this rebuild has nothing saved for — is what says so.
     */
    @Test
    fun comingBackFromAPersonLandsOnCastTabWithThatPersonFocused() {
        val credits =
            TitleCredits(
                cast =
                    listOf(
                        Credit(personId = 4L, name = "Ada Actor", role = "Herself", portraitPath = null),
                        Credit(personId = 5L, name = "Bo Actor", role = "Himself", portraitPath = null),
                    ),
                crew = emptyList(),
            )
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                posterPath = { null },
                onOpenTitle = {},
                onOpenSeason = {},
                credits = credits,
                restoreKey = "5",
            )
        }

        compose.onNodeWithText("1. Pilot").assertDoesNotExist()
        compose.onNodeWithText("Bo Actor").assertIsFocused()
    }

    /** The same restore rule, for a similar show opened from the Similar tab. */
    @Test
    fun comingBackFromASimilarShowLandsOnSimilarTabWithThatShowFocused() {
        val other =
            Entry.Collection(
                key = "SHOW/Another Show",
                kind = CollectionKind.SHOW,
                name = "Another Show",
                posterPath = null,
                posterKey = null,
                count = 1,
                chapters = 1,
                divisions = emptyList(),
            )
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                posterPath = { null },
                onOpenTitle = {},
                onOpenSeason = {},
                similar = listOf(other),
                restoreKey = "SHOW/Another Show",
            )
        }

        compose.onNodeWithText("1. Pilot").assertDoesNotExist()
        compose.onNodeWithText("Another Show").assertIsFocused()
    }

    /** A state update after the page opens (credits arriving a moment later) must not reset which tab is showing. */
    @Test
    fun tabSelectionSurvivesCreditsArrivingAfterThePage() {
        val credits = TitleCredits(cast = listOf(Credit(personId = 4L, name = "Ada Actor", role = null, portraitPath = null)), crew = emptyList())
        val currentCredits = mutableStateOf(TitleCredits.Empty)
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                posterPath = { null },
                onOpenTitle = {},
                onOpenSeason = {},
                credits = currentCredits.value,
            )
        }
        compose.onNodeWithText("About").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Episodes").assertExists()

        compose.runOnUiThread { currentCredits.value = credits }
        compose.waitForIdle()

        // Still on About — Cast arriving did not pull the remote or the tab back to Episodes.
        compose.onNodeWithText("Cast").assertExists()
        compose.onNodeWithText("1. Pilot").assertDoesNotExist()
    }
}
