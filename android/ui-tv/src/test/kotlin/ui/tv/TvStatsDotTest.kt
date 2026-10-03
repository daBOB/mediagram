package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.NEW_ACHIEVEMENT
import stats.NO_ACHIEVEMENTS
import ui.tv.catalog.films
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.EarnedAchievement

/** The new-achievement dot over the real [TvLibrary]: on the collapsed rail's Stats row, out once shown, marked by the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvStatsDotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    private fun earned(vararg ids: String) = Achievements(earned = ids.map { EarnedAchievement(id = it, earnedAt = 1L) }, next = emptyList())

    private fun open(achievements: Achievements) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2), achievements = achievements)
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
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
    fun theCollapsedRailsStatsRowWearsADotForAnAchievementNotYetShown() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stats").assert(hasContentDescription(NEW_ACHIEVEMENT))
    }

    @Test
    fun nothingUnseenWearsNoDot() {
        open(NO_ACHIEVEMENTS)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onceShownTheDotGoesOut() {
        open(earned("films-1"))
        compose.runOnUiThread { fixture.achievementsSeen.markSeen("ada", setOf("films-1")) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun openingStatsMarksWhatItShowsAsSeen() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription("Stats").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        verify { fixture.stats.markAchievementsSeen() }
    }
}
