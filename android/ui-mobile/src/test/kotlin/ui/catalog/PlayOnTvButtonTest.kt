package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import data.TOGETHER_UNREACHABLE
import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.together.CastWords
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.CastAsk
import player.PlayerChoices
import ui.player.CardToolsRow
import ui.player.PlayerCardActions
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Play on TV as the web's title pages and control card offer it: a pill
 * right after Play or Resume that casts what that pill would start, a tool
 * right after Watch together in the player, each greyed with the reason; and
 * the words the TV's answer comes back as.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1200dp")
class PlayOnTvButtonTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var cast: Pair<String, Double>? = null
    private val pressed = mutableListOf<String>()

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun title(
        id: String,
        kind: Kind = Kind.MOVIE,
        season: Int? = null,
        episode: Int? = null,
    ) = MediaSet(
        setId = id, kind = kind, title = "Dune", show = if (kind == Kind.EPISODE) "Show" else null, chapter = null, path = null,
        season = season, episodeFirst = episode, episodeLast = null, year = 2021, durationSecs = 1800,
        posterPath = null, totalBytes = 10,
    )

    private fun watchedTo(
        id: String,
        at: Double,
    ) = WatchSnapshot.Empty.copy(progress = listOf(Progress(id, at, 1800.0, 1L)))

    private fun playOnTv(reason: String? = null) = PlayOnTv(reason) { setId, pos -> cast = setId to pos }

    private fun assertBeside(
        before: String,
        after: String,
    ) {
        val left = compose.onNodeWithText(before, substring = true).getBoundsInRoot()
        val right = compose.onNodeWithText(after).getBoundsInRoot()
        assertTrue(right.left >= left.right && right.top < left.bottom, "$after ($right) sits right after $before ($left)")
    }

    @Test
    fun playOnTvSitsBesidePlay() {
        show { TitleDetailScreen(title("film-1"), null, {}, onOpenGenre = {}, watch = watchedTo("film-1", 600.0), playOnTv = playOnTv()) }

        assertBeside("Resume from 10:00", CastWords.PLAY_ON_TV)
        compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()
        assertEquals("film-1" to 600.0, cast)
    }

    @Test
    fun aShowCastsTheEpisodeItsResumePillWouldStart() {
        val show =
            Entry.Collection(
                key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
                count = 1, chapters = 1,
                divisions = listOf(Division("Season 1", 1, listOf(title("s1e1", Kind.EPISODE, 1, 1)), emptyList())),
            )
        show { CollectionScreen(show, null, watchedTo("s1e1", 1000.0), emptySet(), {}, {}, playOnTv = playOnTv()) }

        assertBeside("S1 E1", CastWords.PLAY_ON_TV)
        compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()
        assertEquals("s1e1" to 1000.0, cast)
    }

    @Test
    fun playOnTvIsGreyedWithTheReason() {
        show { TitleDetailScreen(title("film-1"), null, {}, onOpenGenre = {}, playOnTv = playOnTv(TOGETHER_UNREACHABLE)) }

        compose
            .onNode(hasContentDescription("${CastWords.PLAY_ON_TV}, $TOGETHER_UNREACHABLE"))
            .assertIsNotEnabled()
            .performClick()
        assertNull(cast)
    }

    @Test
    fun inThePlayerPlayOnTvFollowsWatchTogetherGreyedWhileInASession() {
        show {
            CardToolsRow(
                choices = PlayerChoices(),
                speed = 1f,
                actions =
                    PlayerCardActions(
                        onRestart = {}, onPrevious = {}, onNext = {}, onToggleSubtitles = {}, onOpenMenu = {},
                        onToggleStats = {}, onEpisodes = {}, onEnterPip = null, onPlayOnTv = { pressed += "tv" },
                    ),
                castReason = CastWords.LEAVE_FIRST,
            )
        }

        assertBeside("Watch together", CastWords.PLAY_ON_TV)
        compose
            .onNode(hasContentDescription("${CastWords.PLAY_ON_TV}, ${CastWords.LEAVE_FIRST}"))
            .assertIsNotEnabled()
            .performClick()
        assertEquals(emptyList(), pressed)
    }

    @Test
    fun theBusyQuestionOffersReplaceAndCancel() {
        show { CastAskDialog(CastAsk.Busy(3, "Dune"), onReplace = { pressed += "replace" }, onCancel = { pressed += "cancel" }, onDismiss = {}) }

        compose.onNodeWithText("The TV is playing Dune. Replace it?").assertExists()
        compose.onNodeWithText(CastWords.REPLACE).performClick()
        compose.onNodeWithText(CastWords.CANCEL).performClick()
        assertEquals(listOf("replace", "cancel"), pressed)
    }

    @Test
    fun askingHasNoButtonsAndWhatTheTvSaidHasOk() {
        var ask: CastAsk by mutableStateOf(CastAsk.Asking)
        show { CastAskDialog(ask, onReplace = {}, onCancel = {}, onDismiss = { pressed += "ok" }) }

        compose.onNodeWithText(CastWords.ASKING).assertExists()
        compose.onNodeWithText("OK").assertDoesNotExist()

        compose.runOnUiThread { ask = CastAsk.Told(CastWords.NO_ANSWER) }
        compose.onNodeWithText(CastWords.NO_ANSWER).assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf("ok"), pressed)
    }
}
