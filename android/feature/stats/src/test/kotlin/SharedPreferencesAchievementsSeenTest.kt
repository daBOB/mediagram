package stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** What this device has shown, kept across a restart, profile by profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesAchievementsSeenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun whatWasShownSurvivesARestartAndIsKeptPerProfile() {
        val first = SharedPreferencesAchievementsSeen(context)
        first.markSeen("ada", setOf("films-1", "genres-5"))
        first.markSeen("ben", setOf("films-1"))

        assertEquals(
            mapOf("ada" to setOf("films-1", "genres-5"), "ben" to setOf("films-1")),
            SharedPreferencesAchievementsSeen(context).seen.value,
        )
    }

    @Test
    fun markingAgainReplacesWhatThatProfileHadShown() {
        val seen = SharedPreferencesAchievementsSeen(context)
        seen.markSeen("ada", setOf("films-1"))
        seen.markSeen("ada", setOf("films-1", "streak-7"))

        assertEquals(setOf("films-1", "streak-7"), SharedPreferencesAchievementsSeen(context).seen.value["ada"])
    }
}
