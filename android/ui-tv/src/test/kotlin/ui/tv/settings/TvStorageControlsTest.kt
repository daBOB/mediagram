package ui.tv.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.CacheVolume
import playback.INTERNAL_VOLUME_ID
import java.io.File
import kotlin.test.assertEquals

private const val GB = 1L shl 30

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvStorageControlsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val chosen = mutableListOf<Long>()
    private val chosenVolumes = mutableListOf<String>()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent(content = content)
        }
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun stepper(budget: Long) =
        show { TvBudgetStepper(budget = budget, choices = listOf(64 * GB, 128 * GB, 256 * GB), onChoose = { chosen += it }) }

    private fun press(key: Key) {
        compose.onNodeWithTag(BudgetStepperTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(BudgetStepperTag).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun rightStepsToTheNextBudgetAndLeftToThePrevious() {
        stepper(128 * GB)
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        assertEquals(listOf(256 * GB, 64 * GB), chosen)
    }

    @Test
    fun theEndsOfTheLadderChooseNothing() {
        stepper(256 * GB)
        press(Key.DirectionRight)
        assertEquals(emptyList(), chosen)
    }

    @Test
    fun focusAloneChoosesNothing() {
        stepper(128 * GB)
        compose.onNodeWithTag(BudgetStepperTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        assertEquals(emptyList(), chosen)
    }

    @Test
    fun aBudgetOffTheLadderStepsToTheNearestSizesEitherSide() {
        stepper(100 * GB)
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        assertEquals(listOf(64 * GB, 128 * GB), chosen)
    }

    private fun cards(chosenId: String?) =
        show {
            TvVolumeCards(
                volumes =
                    listOf(
                        CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File("."), 21 * GB, removable = false),
                        CacheVolume("usb", "USB stick", File("."), 345 * GB, removable = true),
                    ),
                chosenId = chosenId,
                onChoose = { chosenVolumes += it },
            )
        }

    @Test
    fun pressingAPlaceCardChoosesThatVolume() {
        cards(chosenId = INTERNAL_VOLUME_ID)
        compose.onNodeWithContentDescription("USB stick, 345 GB free").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("usb"), chosenVolumes)
    }

    @Test
    fun theChosenPlaceIsMarkedAndAMissingOneFallsBackToInternal() {
        cards(chosenId = "usb")
        compose.onNodeWithContentDescription("USB stick, 345 GB free").assertIsSelected()
        compose.onNodeWithContentDescription("Internal storage, 21 GB free").assertIsNotSelected()
        close()
        cards(chosenId = "gone")
        compose.onNodeWithContentDescription("Internal storage, 21 GB free").assertIsSelected()
    }
}
