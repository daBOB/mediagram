package ui.chrome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.NEW_ACHIEVEMENT
import stats.NO_ACHIEVEMENTS
import stats.StatsRead
import ui.LibraryFlowFixture
import ui.LibraryFlowTestActivity
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.EarnedAchievement
import uniffi.mediagram_core.StatsSummary
import java.time.ZonedDateTime

/**
 * The new-achievement dot over the real library flow: on the tablet rail's
 * Stats row, on the compact header's Stats button, out once shown, and the
 * Stats page marking what it shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class StatsDotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    private fun earned(vararg ids: String) = Achievements(earned = ids.map { EarnedAchievement(id = it, earnedAt = 1L) }, next = emptyList())

    private fun open(achievements: Achievements) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture(achievements = achievements)
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
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
    fun theRailsStatsRowWearsADotForAnAchievementNotYetShown() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun nothingUnseenWearsNoDot() {
        open(NO_ACHIEVEMENTS)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onceShownTheDotGoesOut() {
        open(earned("films-1"))
        compose.runOnUiThread { fixture.achievementsSeen.markSeen("viewer", setOf("films-1")) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun openingStatsMarksWhatItShowsAsSeen() {
        open(earned("films-1"))
        compose.onNodeWithText("Stats").performClick()
        compose.waitForIdle()
        verify { fixture.stats.markAchievementsSeen() }
    }

    @Test
    fun theStatsPageMarksWhatItShowsOnceItsReadLandsNotOnlyWhenOpened() {
        open(earned("films-1"))
        val read = MutableStateFlow<StatsRead>(StatsRead.Loading)
        val markedAt = mutableListOf<StatsRead>()
        every { fixture.stats.state } returns read
        every { fixture.stats.markAchievementsSeen() } answers { markedAt += read.value }
        compose.onNodeWithText("Stats").performClick()
        compose.waitForIdle()

        val done = StatsRead.Done(StatsSummary(weekSeconds = 0.0, monthSeconds = 0.0, allSeconds = 0.0, last30 = emptyList(), history = emptyList()), ZonedDateTime.now())
        compose.runOnUiThread { read.value = done }
        compose.waitForIdle()

        assertEquals("marked again once the read landed — before it, there is nothing to mark", done, markedAt.lastOrNull())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun theCompactHeadersStatsButtonWearsItToo() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertExists()
    }
}
