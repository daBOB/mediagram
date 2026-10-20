package player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Whether a TV screen is connected as a second display, for screens outside
 * the player that need to know before it opens. [PresentationDisplays.display]
 * is current only while collected, so this is too.
 */
@HiltViewModel
class PresentationViewModel @Inject constructor(
    displays: PresentationDisplays,
) : ViewModel() {
    val connected: StateFlow<Boolean> =
        displays.display.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
