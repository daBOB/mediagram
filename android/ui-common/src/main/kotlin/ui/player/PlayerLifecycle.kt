package ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import player.PlayerViewModel

/**
 * Keeps playback through Activity recreation and stops when navigation
 * removes the screen for good. The Activity-scoped ViewModel survives
 * rotation on its own; this only decides when to tell it to stop or save.
 *
 * A backstop for a kill that skips the dispose effect entirely — recents
 * swiped, the process trimmed — sits alongside it: an Activity can reach
 * `ON_STOP` (screen off, task-switched away) while the Composition it hosts
 * is still there, primed to resume, which the dispose effect alone would
 * not catch.
 */
@Composable
fun PlayerLifecycle(
    viewModel: PlayerViewModel,
    setId: String,
    fsk: String?,
) {
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(setId) { viewModel.open(setId, fsk) }
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                viewModel.stop()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.save()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Compose may supply a theme wrapper; unwrap it to find the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
