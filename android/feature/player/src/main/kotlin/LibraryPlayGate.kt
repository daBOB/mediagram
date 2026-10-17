package player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.WatchStateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.DailyLimitWords
import javax.inject.Inject

/** How long the library's line stays up: the web's page notice. */
internal const val LIBRARY_NOTICE_MS = 10_000L

/**
 * The daily limit at the library's one door to the player (`LibraryPositions.openPlayer`):
 * a refused title does not open, the viewer stays where they were, and [notice]
 * holds the words for the screen to show.
 */
@HiltViewModel
class LibraryPlayGate @Inject constructor(
    repository: WatchStateRepository,
) : ViewModel(), PlayGate {
    private val limit = DailyLimitGate(repository)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private var hide: Job? = null

    override fun applies(): Boolean = limit.applies()

    override suspend fun mayOpen(): Boolean {
        if (limit.mayOpen()) return true
        _notice.value = DailyLimitWords.OUT
        hide?.cancel()
        hide = viewModelScope.launch {
            delay(LIBRARY_NOTICE_MS)
            _notice.value = null
        }
        return false
    }
}
