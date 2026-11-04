package games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.CoreProvider
import data.WatchStateRepository
import data.coreSentence
import data.di.AppScope
import data.settings.TmdbSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.Profile
import javax.inject.Inject

sealed interface GameNightUi {
    data object Starting : GameNightUi

    data class Ready(
        val tvUrl: String,
    ) : GameNightUi

    data class Failed(
        val message: String,
    ) : GameNightUi
}

/** Starts, steps back in and stops the game night host; the screen shows [state]. */
@HiltViewModel
class GameNightViewModel
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val watchState: WatchStateRepository,
        private val catalog: CatalogRepository,
        private val tmdb: TmdbSettings,
        @AppScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val _state = MutableStateFlow<GameNightUi>(GameNightUi.Starting)
        val state: StateFlow<GameNightUi> = _state.asStateFlow()

        fun start() {
            _state.value = GameNightUi.Starting
            viewModelScope.launch {
                _state.value =
                    try {
                        // The choice lands only after a reload; asking before it does must not read as a grown-up.
                        if (watchState.chosenProfile.value == null) watchState.reload()
                        // Still no profile: held to the 12 limit with the toggle locked, never unlocked.
                        val profile = watchState.chosenProfile.value ?: Profile(id = "", name = "", kids = true, kidsAge = GAME_NIGHT_KIDS_LIMIT)
                        val config = gameNightConfig(catalog.sets(), profile, watchState.snapshot.value.kidsMarks)
                        val urls = coreProvider.awaitCore().startGameNight(config.copy(tmdbKey = tmdb.read()))
                        GameNightUi.Ready(urls.tvUrl)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        GameNightUi.Failed(e.coreSentence() ?: e.message ?: "The game night could not start.")
                    }
            }
        }

        /** True when a game was running and the room is back in the lobby; false when there was nothing to go back from. */
        suspend fun back(): Boolean = coreProvider.awaitCore().gameNightBack()

        /** On the app's scope: leaving the screen clears this view model, which must not cancel the stop. */
        fun stop() {
            appScope.launch { coreProvider.awaitCore().stopGameNight() }
        }
    }
