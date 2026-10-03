package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import catalog.Feature
import catalog.FeatureKind
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Every feature title draws uppercase except the second card, which keeps
 * its own case for rhythm across the three — `.feature:nth-child(2)`
 * (`home.css:180`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class HomeFeaturesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun set(
        id: String,
        tagline: String? = null,
    ) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "lowercase $id", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
        posterPath = null, totalBytes = 0, backdropPath = "b-$id.jpg", tagline = tagline,
    )

    @Test
    fun onlyTheSecondCardKeepsItsOwnCase() {
        val features =
            listOf(
                Feature(FeatureKind.EDITOR, set("first")),
                Feature(FeatureKind.TRENDING, set("second")),
                Feature(FeatureKind.STAFF, set("third")),
            )
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { HomeFeatures(features = features, width = 1164.dp, onOpenTitle = {}) } }
        }
        compose.waitForIdle()

        compose.onNodeWithText("LOWERCASE FIRST").assertIsDisplayed()
        compose.onNodeWithText("lowercase second").assertIsDisplayed()
        compose.onNodeWithText("LOWERCASE SECOND").assertDoesNotExist()
        compose.onNodeWithText("LOWERCASE THIRD").assertIsDisplayed()
    }

    @Test
    fun aThreeLineDeckStillDisplaysInFullOnTheWideCardAndInThePair() {
        // A `heightIn(min = ...)` card grows for a deck this long rather
        // than cropping it to the min — the regression a fixed `height()`
        // shipped once already (see `HomeFeatures.kt`'s own note).
        val longDeck = "A tagline long enough to wrap onto three whole lines, the way a real one from the library sometimes runs."
        val features =
            listOf(
                Feature(FeatureKind.EDITOR, set("first", tagline = longDeck)),
                Feature(FeatureKind.TRENDING, set("second", tagline = longDeck)),
            )
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { HomeFeatures(features = features, width = 1164.dp, onOpenTitle = {}) } }
        }
        compose.waitForIdle()

        compose.onAllNodesWithText(longDeck)[0].assertIsDisplayed()
        compose.onAllNodesWithText(longDeck)[1].assertIsDisplayed()
    }
}
