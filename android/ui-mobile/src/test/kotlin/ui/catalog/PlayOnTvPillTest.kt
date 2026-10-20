package ui.catalog

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import data.TOGETHER_UNREACHABLE
import model.Kind
import model.MediaSet
import model.together.CastWords
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Play on TV on a title page, where the system's Cast screen opens: the pill
 * asks which way, as the player's tool does, and the page's own Play opens
 * the player when this device plays it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1200dp")
class PlayOnTvPillTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val app get() = RuntimeEnvironment.getApplication()
    private var cast: Pair<String, Double>? = null
    private val played = mutableListOf<String>()

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    /** The system's Cast screen, as the tablet's Settings has it. */
    private fun castScreen() {
        val settings = ApplicationInfo().apply { packageName = "com.android.settings" }
        val screen = ActivityInfo().apply { packageName = settings.packageName; name = "com.android.settings.CastSettings"; applicationInfo = settings }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Settings.ACTION_CAST_SETTINGS), ResolveInfo().apply { activityInfo = screen })
    }

    private fun startedAction(): String? = shadowOf(app).nextStartedActivity?.action

    private fun show(playOnTv: PlayOnTv) {
        val film = MediaSet(
            setId = "film-1", kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 1800,
            posterPath = null, totalBytes = 10,
        )
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme { TitleDetailScreen(film, null, { played += "film-1" }, onOpenGenre = {}, playOnTv = playOnTv) }
            }
        }
        compose.waitForIdle()
    }

    private fun tv(
        reason: String? = null,
        connected: Boolean = false,
    ) = PlayOnTv(reason, connected) { setId, pos -> cast = setId to pos }

    @Test
    fun thePillAsksWhichWay() {
        castScreen()
        show(tv(reason = TOGETHER_UNREACHABLE))

        // Not greyed: this device playing touches no channel.
        compose.onNode(hasContentDescription(CastWords.PLAY_ON_TV)).assertIsEnabled().performClick()

        compose
            .onNode(hasContentDescription("${CastWayWords.THE_TV}, $TOGETHER_UNREACHABLE, ${CastWayWords.THE_TV_DETAIL}"))
            .assertIsNotEnabled()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).assertIsEnabled()
        compose.onNodeWithText(CastWords.CANCEL).performClick()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).assertDoesNotExist()
        assertNull(cast)
        assertEquals(emptyList(), played)
    }

    @Test
    fun theTvPlaysItOffersFromThePillsPoint() {
        castScreen()
        show(tv())

        compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()
        compose.onNodeWithText(CastWayWords.THE_TV).performClick()

        assertEquals("film-1" to 0.0, cast)
        assertEquals(emptyList(), played)
        assertNull(startedAction())
    }

    @Test
    fun thisDevicePlaysItOpensThePlayerAndTheCastScreen() {
        castScreen()
        show(tv())

        compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).performClick()

        assertEquals(listOf("film-1"), played)
        assertEquals("android.settings.CAST_SETTINGS", startedAction())
        assertNull(cast)
    }

    @Test
    fun withADisplayConnectedThisDevicePlaysItOnlyOpensThePlayer() {
        castScreen()
        show(tv(connected = true))

        compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).performClick()

        assertEquals(listOf("film-1"), played)
        assertNull(startedAction())
    }
}
