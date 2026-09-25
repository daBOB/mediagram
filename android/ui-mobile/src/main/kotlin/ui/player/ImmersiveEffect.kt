package ui.player

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Hides the system bars while the player is on screen. The web's fullscreen
 * is a button; a phone's player is always fullscreen, so there is nothing
 * for a viewer to press here — this simply does it, and undoes it the
 * moment the screen this is attached to leaves composition, rotation
 * included, the same as `KeepScreenOnWhile`.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` is what lets a swipe from an edge
 * bring the bars back without leaving the player, the way any other
 * fullscreen video app does; the swipe-to-reveal and the back gesture both
 * keep working exactly as before, since hiding a bar only hides it, never
 * turns off the gesture navigation it would otherwise still show. `PlayerMarks`
 * already pads for `systemBarsIgnoringVisibility` rather than the bars'
 * actual (hidden) state, which is what keeps it from jumping the moment
 * this hides or a swipe reveals them.
 *
 * Hiding once on entry is not enough: the settings sheet is its own dialog
 * window, and on API 30+ the focused window's own requested visibility
 * wins — while it is open the real bars come back, and returning from the
 * background or from any other window (a permission prompt, the sheet
 * itself closing) can leave them shown on API 24-29's legacy flag path too,
 * since nothing there re-applies them on its own. Re-hiding on `ON_RESUME`
 * and on this window regaining focus covers both: a window losing focus to
 * another (the sheet opening) is not this, and a window *regaining* it is
 * exactly what "the sheet closed" and "back from the background" both are.
 */
@Composable
internal fun ImmersiveEffect() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(view, activity, lifecycleOwner) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        fun hide() = controller.hide(WindowInsetsCompat.Type.systemBars())
        hide()

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hide()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) hide() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
