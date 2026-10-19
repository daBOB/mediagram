package player

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * The name of the wireless display the picture goes to, or null while there
 * is none. [PresentationDisplays.display] is current only while collected, so
 * this collects it for as long as the view model lives: a screen opening the
 * player later reads where the picture goes now, not where it last went.
 *
 * The display going away pauses: the picture comes back to this screen,
 * which nobody may be looking at. Only this screen pauses, as a dismissed
 * picture-in-picture window does; it is not a viewer's own pause.
 */
internal fun PlayerViewModel.presentingOn(displays: PresentationDisplays?): StateFlow<String?> {
    var shownOn: String? = null
    return (displays?.display ?: flowOf(null))
        .map { it?.name }
        .onEach { name ->
            if (shownOn != null && name == null) handle.pause()
            shownOn = name
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** The screen stopped: with its picture on a wireless display nobody is at the remote, so the title pauses there too. */
fun PlayerViewModel.pauseIfPresenting() {
    if (presenting.value != null) handle.pause()
}
