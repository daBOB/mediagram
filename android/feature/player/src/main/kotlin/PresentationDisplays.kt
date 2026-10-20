package player

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wireless display (Miracast, AnyView) the system offers for
 * presentations, or null when none is connected. The platform reports one
 * as soon as the user pairs a TV, so the player does not scan for it.
 *
 * The listener is registered only while [display] is collected, so the
 * answer is current only while someone collects it.
 */
@Singleton
class PresentationDisplays @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager = context.getSystemService(DisplayManager::class.java)

    private fun first(): Display? =
        manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()

    /**
     * Current only while collected: the current display is re-read on each
     * new subscription, but between subscribers `.value` keeps whatever was
     * last seen and may name a display that has since gone. Collect it; never
     * read `.value` cold.
     */
    val display: StateFlow<Display?> = callbackFlow {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { trySend(first()) }
            override fun onDisplayRemoved(displayId: Int) { trySend(first()) }
            override fun onDisplayChanged(displayId: Int) { trySend(first()) }
        }
        manager.registerDisplayListener(listener, null)
        trySend(first())
        awaitClose { manager.unregisterDisplayListener(listener) }
    }.stateIn(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        SharingStarted.WhileSubscribed(),
        first(),
    )
}
