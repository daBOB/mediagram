package ui.player

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import model.MediaSet
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowDisplayManager

/** A playing title behind the real screen, and the tablet's Living Room TV to connect to it. */
abstract class PlayerPresentationBase : PlayerCardScreenBase() {
    internal val label = "Playing on Living Room TV"

    /** Connects the tablet's Living Room TV, flagged as the presentation display Miracast reports. */
    internal fun connectTv(): Int {
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

    internal fun idle() {
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idle() }
        compose.waitForIdle()
    }

    internal fun presentation(): Presentation? = ShadowDialog.getShownDialogs().filterIsInstance<Presentation>().singleOrNull { it.isShowing }

    internal fun View.surfaces(): Int =
        when (this) {
            is SurfaceView -> 1
            is ViewGroup -> (0 until childCount).sumOf { getChildAt(it).surfaces() }
            else -> 0
        }

    internal fun duneCatalog(): CatalogRepository =
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
