package ui.player

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import data.CatalogRepository
import data.LiveSession
import data.TogetherLive
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import model.MediaSet
import model.together.Member
import model.together.PlaybackChange
import model.together.Session
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeTogetherLive
import kotlin.test.assertTrue

/**
 * On a tablet the label sits beside the title, as the web's does from 768px,
 * but with the episode list standing down the right it takes the line under
 * the title, as the web's does beside its sidebar: the bar runs the window's
 * width, and beside the title it lands on the list's own header.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp")
class TogetherLabelBesideSidebarTest : PlayerCardScreenBase() {
    private val pilot =
        MediaSet(
            setId = "set-one", kind = Kind.EPISODE, title = "Pilot", show = "A Show", chapter = null, path = null, season = 1,
            episodeFirst = 1, episodeLast = 1, year = null, durationSecs = 1_800, posterPath = null, totalBytes = 0, rawTitle = "Pilot",
        )

    @Test
    fun besideTheEpisodeListTheLabelTakesTheLineUnderTheTitle() {
        val catalog = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalog.sets() } returns listOf(pilot)
        coEvery { catalog.mediaSet("set-one") } returns pilot
        open(run = listOf("set-one", "set-two"), catalog = catalog, together = JoiningTogetherLive().apply { joinOnOpen(41) })
        compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.together.value.label != null }

        compose.onNodeWithContentDescription("Episodes").performClick()

        val sidebar = compose.onNodeWithTag(EpisodeSidebarTag).getBoundsInRoot()
        val label = compose.onNode(hasContentDescription("Together · TV", substring = true)).getBoundsInRoot()
        val title = compose.onNodeWithText("A Show", substring = true).getBoundsInRoot()
        assertTrue(label.top >= title.bottom, "the label ($label) takes the line under the title ($title)")
        assertTrue(label.right <= sidebar.left, "the label ($label) stays clear of the episode list ($sidebar)")
    }
}

/** A Telegram that lets this screen join session 41 on `set-one`, the TV already in it, and keeps every move. */
private class JoiningTogetherLive(
    private val held: FakeTogetherLive = FakeTogetherLive(),
) : TogetherLive by held {
    override suspend fun join(
        id: Int,
        me: Member,
    ): LiveSession {
        val session = Session("s", "set-one", true, 0.0, 1.0, System.currentTimeMillis(), "tv", listOf(Member("tv", "TV"), me), false)
        return LiveSession(id, session).also { held.live.value = it }
    }

    override suspend fun act(
        id: Int,
        screen: String,
        change: PlaybackChange,
    ): Session? = held.live.value?.session

    override fun leave(
        id: Int,
        screen: String,
    ) = Unit

    override fun end(
        id: Int,
        screen: String,
    ) = Unit
}
