package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.MaterialTheme
import designsystem.Palette
import designsystem.TvTypeScale
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvTheme] is the only place a TV screen can reach [Palette] and
 * [TvTypeScale] through tv-material's own `MaterialTheme` — `:ui-tv`
 * never has M3 on its compile classpath, so nothing here can fall back to
 * `designsystem.MediagramTheme`. These tests pin the two facts every later
 * screen relies on without re-checking: the accent colour a focused card
 * borders itself in is the same red the phone calls "active", and a title
 * or a line of body copy is set at the couch-distance sizes [TvTypeScale]
 * chose, not tv-material's own defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvThemeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun primaryColourIsTheCatalogueImprint() {
        var primary: Color? = null
        show { primary = MaterialTheme.colorScheme.primary }
        assertEquals(Palette.Imprint, primary)
    }

    @Test
    fun titleTextTracksTvTypeScale() {
        var titleSize: TextUnit? = null
        show { titleSize = MaterialTheme.typography.titleLarge.fontSize }
        assertEquals(TvTypeScale.title.fontSize, titleSize)
    }

    @Test
    fun bodyTextTracksTvTypeScale() {
        var bodySize: TextUnit? = null
        show { bodySize = MaterialTheme.typography.bodyLarge.fontSize }
        assertEquals(TvTypeScale.body.fontSize, bodySize)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}
