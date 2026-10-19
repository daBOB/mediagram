package ui.player

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ui.common.player.findActivity

/**
 * The picture on a wireless display, full screen and black around it. A
 * `Presentation` is a dialog with no lifecycle, saved state or view models
 * of its own, so its view tree borrows [activity]'s; [composition] makes its
 * composition part of the player screen's, recomposed with it.
 */
internal class PlayerPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val composition: CompositionContext,
    private val content: @Composable () -> Unit,
) : Presentation(activity, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setParentCompositionContext(composition)
                setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { content() } }
            },
        )
    }
}

/**
 * Shows [content] on the presentation display called [name] for as long as
 * this is composed. A display gone before it could be shown is no failure:
 * the player hears it went, and takes this away.
 */
@Composable
internal fun PresentOnDisplay(
    name: String,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    val parent = rememberCompositionContext()
    val latest by rememberUpdatedState(content)
    DisposableEffect(activity, name) {
        val display =
            activity.getSystemService(DisplayManager::class.java)
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.name == name }
        val presentation = display?.let { PlayerPresentation(activity, it, parent) { latest() } }
        try {
            presentation?.show()
        } catch (gone: WindowManager.InvalidDisplayException) {
            // Removed between the player hearing of it and this; its removal is on its way too.
        }
        onDispose { presentation?.dismiss() }
    }
}
