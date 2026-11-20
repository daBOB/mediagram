package ui.common.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import designsystem.Backdrop
import designsystem.LocalBackdrop
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.catalog.HERO_ARTWORK_TEST_TAG

/** A book's cover is the picture: the viewer's blurred-backdrop setting must not zoom, crop and blur it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlainCoverTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theCoverIsDrawnPlainEvenWithABlurredBackdrop() {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { CompositionLocalProvider(LocalBackdrop provides Backdrop.BLURRED) { PlainCover("/no/such/cover.jpg") } }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(PLAIN_COVER_TAG).assertExists()
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG).assertDoesNotExist()
    }
}
