package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The menu's drop is a layer translation, which shows in its item's bounds
 * inside the popup's root on a paused clock. Its fade is alpha, which nothing
 * here can capture (see PageGroundColorTest); the device walk checks it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueMenuTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var expanded by mutableStateOf(false)
    private var picked = 0

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(still: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    Box {
                        Text("⋯")
                        CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }, still = still) {
                            CatalogueMenuItem(
                                text = { Text("Pin") },
                                onClick = {
                                    expanded = false
                                    picked++
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Opens the menu and answers where its item stands on the entrance's first frame. */
    private fun open(): Float {
        compose.runOnUiThread { expanded = true }
        compose.mainClock.advanceTimeByFrame() // recomposes: the popup comes in
        compose.waitForIdle() // its window attaches, composes and lays out
        compose.mainClock.advanceTimeByFrame() // the entrance's first frame
        return top()
    }

    private fun top() = compose.onNodeWithText("Pin").getUnclippedBoundsInRoot().top.value

    @Test
    fun inTheCatalogueTheMenuDropsIntoPlaceAndClosesAtOnce() {
        show(still = false)
        val opening = open()
        compose.mainClock.advanceTimeBy(400)
        val rest = top()
        assertTrue(rest - opening > 1f, "starts above its rest: $opening, then $rest")

        compose.onNodeWithText("Pin").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(1, picked)
        compose.onNodeWithText("Pin").assertDoesNotExist()
    }

    @Test
    fun inThePlayerTheMenuOpensWhereItRests() {
        show(still = true)
        val opening = open()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(top(), opening, 0.1f)
    }

    @Test
    fun theListIsStillAPopupAndItsItemsStillTakeAClick() {
        show(still = false)
        open()
        compose.onNode(isPopup()).assertExists()
        compose.onNodeWithText("Pin").assert(hasClickAction())
    }

    /** Material's menu scales and fades both ways; one slipping back in would move where the web does not. */
    @Test
    fun noScreenUsesMaterialsAnimatedMenu() {
        val menu = Regex("""^import androidx\.compose\.material3\.DropdownMenu$""", RegexOption.MULTILINE)
        val item = Regex("""^import androidx\.compose\.material3\.DropdownMenuItem$""", RegexOption.MULTILINE)
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    val text = file.readText()
                    menu.containsMatchIn(text) || (file.name != "CatalogueMenu.kt" && item.containsMatchIn(text))
                }.map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "use ui.chrome's CatalogueMenu and CatalogueMenuItem instead in: $offenders")
    }
}
