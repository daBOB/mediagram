package ui.player

import android.app.Presentation
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import model.Kind
import model.MediaSet
import model.together.CastWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowDisplayManager
import ui.common.catalog.HERO_ARTWORK_TEST_TAG
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A wireless display connected while the player is open: the picture goes to
 * it, and the phone turns into the remote by itself — the box remote's look,
 * driving this player. Losing the display, or the screen stopping, pauses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp")
class PlayerPresentationTest : PlayerCardScreenBase() {
    private val label = "Playing on Living Room TV"

    @Test
    fun aPresentationDisplayShowsPlayingOnItsName() {
        open()
        val id = connectTv()

        val shown = assertNotNull(presentation(), "the picture is presented")
        assertEquals(id, shown.display.displayId)
        compose.onNode(hasContentDescription(label)).assertExists()
    }

    @Test
    fun whilePresentingThePhoneShowsTheRemoteNotTheVideo() {
        open(catalog = duneCatalog())
        assertEquals(1, controller.get().window.decorView.surfaces(), "the phone shows the picture before")

        connectTv()

        assertEquals(0, controller.get().window.decorView.surfaces(), "and none while the TV does")
        assertEquals(1, presentation()!!.window!!.decorView.surfaces())
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG).assertExists()
        compose.onNodeWithText("Dune").assertExists()
        compose.onNode(hasContentDescription(label)).assertExists()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        // The remote's card: what only a picture on this screen has is left out, and there is no Stop.
        for (gone in listOf("Restart", "Stats", "Framing", "Picture in picture")) {
            compose.onNodeWithContentDescription(gone).assertDoesNotExist()
        }
        compose.onNodeWithText("Watch together").assertDoesNotExist()
        compose.onNodeWithText(CastWords.STOP).assertDoesNotExist()
        compose.onNodeWithContentDescription("Speed").assertExists()
    }

    @Test
    fun whilePresentingTheCardStaysUp() {
        open(catalog = duneCatalog())
        connectTv()

        lingerPast()
        compose.onNodeWithTag(PlayerCardTag).assertExists()

        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG).performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
    }

    @Test
    fun noPipWhilePresenting() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        open()
        assertTrue(assertNotNull(controller.get().pipParams).isAutoEnterEnabled, "a playing title would shrink on Home")

        connectTv()

        assertFalse(assertNotNull(controller.get().pipParams).isAutoEnterEnabled)
        compose.onNodeWithContentDescription("Picture in picture").assertDoesNotExist()
    }

    @Test
    fun losingTheDisplayPauses() {
        open()
        val id = connectTv()
        val shown = presentation()!!
        verify(exactly = 0) { fixture.media.pause() }

        compose.runOnUiThread { ShadowDisplayManager.removeDisplay(id) }
        idle()

        verify { fixture.media.pause() }
        assertFalse(shown.isShowing)
        assertNull(presentation())
        // The player's own look is back: its picture, and its own card.
        assertEquals(1, controller.get().window.decorView.surfaces())
        compose.onNode(hasContentDescription(label)).assertDoesNotExist()
        compose.onNodeWithContentDescription("Restart").assertExists()
    }

    @Test
    fun stoppingWhilePresentingPauses() {
        open()
        connectTv()
        verify(exactly = 0) { fixture.media.pause() }

        compose.runOnUiThread { controller.pause().stop() }
        idle()

        verify { fixture.media.pause() }
    }

    /** Connects the tablet's Living Room TV, flagged as the presentation display Miracast reports. */
    private fun connectTv(): Int {
        var id = 0
        compose.runOnUiThread {
            id = ShadowDisplayManager.addDisplay("w1920dp-h1080dp", TYPE_WIFI)
            val display = RuntimeEnvironment.getApplication().getSystemService(DisplayManager::class.java).getDisplay(id)
            shadowOf(display).apply {
                setName("Living Room TV")
                setFlags(Display.FLAG_PRESENTATION)
            }
        }
        idle()
        return id
    }

    private fun idle() {
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idle() }
        compose.waitForIdle()
    }

    private fun presentation(): Presentation? = ShadowDialog.getShownDialogs().filterIsInstance<Presentation>().singleOrNull { it.isShowing }

    private fun View.surfaces(): Int =
        when (this) {
            is SurfaceView -> 1
            is ViewGroup -> (0 until childCount).sumOf { getChildAt(it).surfaces() }
            else -> 0
        }

    private fun duneCatalog(): CatalogRepository =
        mockk<CatalogRepository>(relaxed = true).also {
            coEvery { it.sets() } returns emptyList()
            coEvery { it.mediaSet("set-one") } returns
                MediaSet(
                    "set-one", Kind.MOVIE, "Dune", null, null, null, null, null, null, 2021, 155, null, 10,
                    backdropPath = "backdrop.jpg", rawTitle = "Dune",
                )
        }

    private companion object {
        // Display.TYPE_WIFI is hidden API; this is its value.
        const val TYPE_WIFI = 3
    }
}
