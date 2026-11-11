package ui.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.films
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The rail's Games row over the real [TvLibrary]: it hands over to Spieltisch when that is
 * installed, and otherwise says so and stays on the shelves. The phone's rail has no such row
 * (`LibraryFlowTest.phoneRailHasNoGames`): Spieltisch is a television app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGamesRailTest {
    private val spieltisch = "com.mediagram.spieltisch"

    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun gamesOnTheRailOpensSpieltischThroughItsLeanbackLauncher() {
        launcher(Intent.CATEGORY_LEANBACK_LAUNCHER)
        showLibrary()

        pressGames()

        assertEquals(spieltisch, nextStartedActivity()?.component?.packageName)
        compose.onAllNodes(hasText(NOT_INSTALLED)).assertCountEquals(0)
    }

    @Test
    fun gamesFallsBackToTheNormalLaunchIntent() {
        launcher(Intent.CATEGORY_LAUNCHER)
        showLibrary()

        pressGames()

        assertEquals(spieltisch, nextStartedActivity()?.component?.packageName)
    }

    @Test
    fun gamesSaysSpieltischIsNotInstalledAndDoesNothingElse() {
        showLibrary()

        pressGames()

        compose.onNode(hasText(NOT_INSTALLED)).assertExists()
        assertNull(nextStartedActivity())
        compose.onNode(hasText("Film 1")).assertExists()
    }

    @Test
    fun gamesOverATitlePageLeavesThePageWhereItIs() {
        showLibrary()
        compose.onNode(hasText("Film 1") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("▶ Play").assertExists()

        pressGames()

        compose.onNode(hasText(NOT_INSTALLED)).assertExists()
        compose.onNodeWithText("▶ Play").assertExists()
        assertNull(nextStartedActivity())
    }

    private fun showLibrary() {
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2))
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Spieltisch installed with one activity answering a launch through [category]. */
    private fun launcher(category: String) {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = spieltisch; name = "$spieltisch.MainActivity" } }
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(spieltisch),
            info,
        )
    }

    private fun nextStartedActivity(): Intent? {
        compose.waitForIdle()
        return shadowOf(controller.get()).nextStartedActivity
    }

    private fun pressGames() {
        compose.onNodeWithContentDescription("Games").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private companion object {
        const val NOT_INSTALLED = "Spieltisch ist nicht installiert"
    }
}
