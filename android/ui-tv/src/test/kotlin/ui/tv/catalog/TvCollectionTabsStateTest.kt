package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
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
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A show's own page ([TvCollection] → [TvSeriesPage]): its tabs — Episodes/
 * About/Cast/Similar, as `series-page.js` tabs them — its pills, and the
 * restore that brings the remote back to whatever it left from.
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

    /** Similar is always a tab, as `series-page.js` has it, saying so when nothing is like the show; Cast only once credits name somebody. */
    @Test
    fun withNoCastSimilarIsStillOfferedAndEpisodesShows() {
        show { TvCollection(show, info = null, watch = WatchSnapshot.Empty, onPlay = {}) }

        listOf("Episodes", "About", "Similar").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Cast").assertDoesNotExist()
        compose.onNodeWithText("1. Pilot").assertExists()
        // No resume pick, so the first pill there is takes the remote.
        compose.onNodeWithText("+ My List").assertIsFocused()

        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(NothingSimilar).assertExists()
    }

    /** `series-page.js#fillAbout`: when it aired, how much is held against what exists, who made it, its genres, picture and languages. */
    @Test
    fun aboutIsTheWebsFactSheet() {
        var genre: String? = null
        val episode = set("e1", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0, durationSecs = 3000).copy(quality = "1080p", alang = listOf("en"), genres = listOf("Drama"))
        val drama = show.copy(divisions = listOf(Division("Season 1", 1, listOf(episode), emptyList())))
        val info =
            TitleInfo(
                overview = null, tagline = null, genres = "Drama", rating = 8.1, network = "HBO", status = "Ended",
                firstAir = "2011-04-17", lastAir = "2019-05-19", totalSeasons = 8u, totalEpisodes = 73u,
            )
        show { TvCollection(drama, info = info, watch = WatchSnapshot.Empty, onPlay = {}, onOpenGenre = { genre = it }) }

        // The facts line under the title spells its seasons and uses the provider's years.
        compose.onNodeWithText("2011–2019 · one season · Drama").assertExists()
        compose.onNodeWithText("About").performSemanticsAction(SemanticsActions.OnClick)
        listOf(
            "AIRED" to "2011–2019",
            "HELD" to "1 of 73 episodes · 1 of 8 seasons · 50m",
            "FROM" to "★ 8.1 · HBO · Ended",
            "PICTURE" to "1080p",
            "AUDIO" to "English",
        ).forEach { (label, value) ->
            compose.onNodeWithText(label).assertExists()
            compose.onNodeWithText(value).assertExists()
        }
        compose.onNodeWithText("Drama").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Drama", genre)
    }

    /** Back from a genre's page lands on its link, which lives in About now. */
    @Test
    fun comingBackFromAGenreLandsOnItsLinkInAbout() {
        val tagged = show.copy(divisions = listOf(Division("Season 1", 1, listOf(set("e1", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0).copy(genres = listOf("Drama"))), emptyList())))
        show { TvCollection(tagged, info = null, watch = WatchSnapshot.Empty, onPlay = {}, restoreKey = "Drama") }

        compose.onNodeWithText("Drama").assertIsFocused()
    }

    /** Up from a panel enters its own tab — Cast here — not Episodes, the tab nearest the first plate. */
    @Test
    fun upFromCastEntersTheCastTab() {
        val credits = TitleCredits(cast = listOf(Credit(personId = 4L, name = "Ada Actor", role = "Herself", portraitPath = null)), crew = emptyList())
        show { TvCollection(show, info = null, watch = WatchSnapshot.Empty, onPlay = {}, credits = credits) }
        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Ada Actor").assertIsFocused()

        compose.onNodeWithText("Ada Actor").performKeyInput { pressKey(Key.DirectionUp) }

        compose.onNodeWithText("Cast").assertIsFocused()
    }

    /** The web lists and pins a show by its first episode; the pills do the same through the caller. */
    @Test
    fun myListAndTheMoreMenuActOnTheShow() {
        var listed = false
        var pinned = false
        show {
            TvCollection(
                show, info = null, watch = WatchSnapshot.Empty.copy(watchlist = listOf("e1")), onPlay = {},
                onToggleWatchlist = { listed = true }, onToggleEditorsChoice = { pinned = true },
            )
        }

        compose.onNodeWithText("✓ My List").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(listed)
        compose.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Make editor's choice").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(pinned)
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
                onPlay = {},
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
                onPlay = {},
                similar = listOf(other),
                onOpenCollection = { opened = it },
            )
        }

        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Another Show").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("SHOW/Another Show", opened)
    }

    @Test
    fun theResumePillTakesTheRemoteAndPlaysItsOwnEpisode() {
        var played: String? = null
        val pick = SeriesResumePick(set = set("e1", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0, episode = 1), at = 30.0, verb = ResumeVerb.RESUME)
        show {
            TvCollection(
                show,
                info = null,
                watch = WatchSnapshot.Empty,
                onPlay = {},
                resume = pick,
                onResume = { played = it },
            )
        }

        // `series-page.js`'s own pill words: the verb, then the episode.
        compose.onNodeWithText("▶ Resume Pilot").assertIsFocused()
        compose.onNodeWithText("▶ Resume Pilot").performSemanticsAction(SemanticsActions.OnClick)
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
                onPlay = {},
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
                onPlay = {},
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
                onPlay = {},
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
