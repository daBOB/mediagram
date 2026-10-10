package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import designsystem.MediagramTheme
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueButtonsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun eachWrapperStillTakesItsClick() {
        val clicks = mutableListOf<String>()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme {
                    androidx.compose.foundation.layout.Column {
                        CatalogueTextButton(onClick = { clicks += "text" }) { Text("text") }
                        CatalogueButton(onClick = { clicks += "filled" }) { Text("filled") }
                        CatalogueOutlinedButton(onClick = { clicks += "outlined" }) { Text("outlined") }
                        CatalogueIconButton(onClick = { clicks += "icon" }) { Text("icon") }
                    }
                }
            }
        }
        listOf("text", "filled", "outlined", "icon").forEach { compose.onNodeWithText(it).performClick() }
        compose.waitForIdle()
        assertEquals(listOf("text", "filled", "outlined", "icon"), clicks)
    }

    /**
     * Under MediagramTheme a bare Material button answers a press with
     * nothing: the theme turns ripple off, and Material's buttons never read
     * LocalIndication. A new one slipping in would be a silent control.
     */
    @Test
    fun noScreenUsesABareMaterialButton() {
        val bare = Regex("""^import androidx\.compose\.material3\.(TextButton|Button|OutlinedButton|IconButton)$""", RegexOption.MULTILINE)
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "CatalogueButtons.kt" }
                .filter { bare.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "use ui.chrome's Catalogue*Button instead in: $offenders")
    }
}
