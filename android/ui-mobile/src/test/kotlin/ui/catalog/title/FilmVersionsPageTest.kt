package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CatalogUiState
import catalog.Department
import catalog.Shelf
import catalog.shelvesOf
import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.TitleDetailScreen
import ui.catalog.VersionAskSheet
import ui.common.catalog.VersionAsk
import ui.common.catalog.rememberVersionAsk
import kotlin.test.assertEquals

/**
 * A film held twice: its page lists both copies in place of a Play, each
 * with its own, and a card that plays straight away asks in a bottom sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class FilmVersionsPageTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun copy(setId: String, quality: String) =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = "Endgame", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2019, durationSecs = 10_871,
            posterPath = null, totalBytes = 4_294_967_296, quality = quality, hdr = "SDR", posterKey = "tmdb-movie-1",
        )

    private val shelves: List<Shelf> = shelvesOf(listOf(copy("HD", "1080p"), copy("UHD", "2160p")))
    private val film = shelves.single { it.department == Department.MOVIES }.entries.single().let { (it as catalog.Entry.Film).set }
    private val watch = WatchSnapshot(listOf(Progress("HD", 1_800.0, 10_871.0, 1)), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun theFilmPageListsEveryCopyInPlaceOfItsPlay() {
        val played = mutableListOf<String>()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    TitleDetailScreen(set = film, info = null, onPlay = {}, onOpenGenre = {}, watch = watch, onPlayVersion = { played += it.setId })
                }
            }
        }
        compose.onNodeWithText("Versions").assertExists()
        compose.onAllNodesWithText("▶ Play", useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(0, it.size, "no Play of its own") }
        compose.onNodeWithText("Standard · 4K · 3h 1m · 4.0 GB").assertExists()
        // The 1080p copy's place carries to the 4K copy: one cut, one timeline.
        compose.onAllNodesWithText("Resume at 30:00").fetchSemanticsNodes().let { assertEquals(2, it.size) }
        compose.onNodeWithContentDescription("Resume Standard · 1080p · 3h 1m · 4.0 GB").performClick()
        assertEquals(listOf("HD"), played)
    }

    @Test
    fun aCardThatPlaysStraightAwayAsksWhichCopyAndAPickPlaysIt() {
        val opened = mutableListOf<Pair<String, List<String>?>>()
        lateinit var ask: VersionAsk
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    ask = rememberVersionAsk(CatalogUiState.Ready(shelves, watch)) { id, run -> opened += id to run }
                    VersionAskSheet(ask, watch, emptySet())
                }
            }
        }
        compose.runOnUiThread { ask.play("HD", listOf("X", "UHD")) }
        compose.onNodeWithText("Standard · 1080p · 3h 1m · 4.0 GB").assertExists()
        compose.onNodeWithContentDescription("Play Standard · 4K · 3h 1m · 4.0 GB").assertDoesNotExist()
        compose.onNodeWithContentDescription("Resume Standard · 4K · 3h 1m · 4.0 GB").performClick()
        compose.waitForIdle()
        // The pick takes its film's place in the run, so a list carries on after it.
        assertEquals<List<Pair<String, List<String>?>>>(listOf("UHD" to listOf("X", "UHD")), opened)
    }
}
