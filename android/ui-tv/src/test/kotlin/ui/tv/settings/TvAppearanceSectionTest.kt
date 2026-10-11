package ui.tv.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import designsystem.Accent
import designsystem.Backdrop
import designsystem.TvStyle
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvAppearanceSectionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var pickedAccent: Accent? = null
    private var pickedBackdrop: Backdrop? = null
    private var pickedStyle: TvStyle? = null

    private fun show(
        accent: Accent,
        backdrop: Backdrop = Backdrop.Default,
        style: TvStyle = TvStyle.Default,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                TvAppearanceSection(
                    accent = accent,
                    backdrop = backdrop,
                    style = style,
                    focusInContent = true,
                    entryFocusRequester = remember { FocusRequester() },
                    onSelectAccent = { pickedAccent = it },
                    onSelectBackdrop = { pickedBackdrop = it },
                    onSelectStyle = { pickedStyle = it },
                )
            }
        }
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theSelectedAccentIsAnnouncedAsSelected() {
        show(accent = Accent.TEAL)
        compose.onNodeWithContentDescription("Teal").assertIsSelected()
        compose.onNodeWithContentDescription("Coral").assertIsNotSelected()
    }

    @Test
    fun pressingAnAccentReportsItChosen() {
        show(accent = Accent.CORAL)
        // Not performClick(): tv-material's ClickableSurface drives its click
        // through pointer-input gesture detection Robolectric doesn't run to
        // completion (see TvTmdbKeyScreenTest's own `press` helper for the
        // same reason). The semantics action is the click callback itself.
        compose.onNodeWithContentDescription("Blue").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Accent.BLUE, pickedAccent)
    }

    @Test
    fun theSelectedArtworkModeIsAnnouncedAsSelected() {
        show(accent = Accent.CORAL, backdrop = Backdrop.BLURRED)
        compose.onNodeWithContentDescription("Blurred").assertIsSelected()
        compose.onNodeWithContentDescription("Default").assertIsNotSelected()
    }

    @Test
    fun pressingAnArtworkCardReachesChooseBackdrop() {
        show(accent = Accent.CORAL)
        compose.onNodeWithContentDescription("Solid").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Backdrop.SOLID, pickedBackdrop)
    }

    @Test
    fun theSelectedThemeIsAnnouncedAsSelected() {
        show(accent = Accent.CORAL, style = TvStyle.APPLE)
        compose.onNodeWithContentDescription("Apple TV style").assertIsSelected()
        compose.onNodeWithContentDescription("mediagram").assertIsNotSelected()
    }

    @Test
    fun pressingAThemeReachesChooseStyle() {
        show(accent = Accent.CORAL)
        compose.onNodeWithContentDescription("Google TV style").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(TvStyle.GOOGLE, pickedStyle)
    }
}
