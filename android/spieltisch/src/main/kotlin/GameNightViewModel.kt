package com.mediagram.spieltisch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.spieltisch_core.SpieltischConfig

/** What the game night screen shows. */
sealed interface GameNightUi {
    /** The host is coming up, or a night has just ended. */
    data object Starting : GameNightUi

    /** The host runs; [tvUrl] is its TV page on loopback. */
    data class Ready(
        val tvUrl: String,
    ) : GameNightUi

    /** The host could not start; [message] says why. */
    data class Failed(
        val message: String,
    ) : GameNightUi
}

/** Starts, steps back in and stops the game night host over [night]; the screen shows [state]. */
class GameNightViewModel(
    private val night: NightSession,
) : ViewModel() {
    private val _state = MutableStateFlow<GameNightUi>(GameNightUi.Starting)
    val state: StateFlow<GameNightUi> = _state.asStateFlow()

    private var startJob: Job? = null

    fun start() {
        _state.value = GameNightUi.Starting
        val earlier = startJob
        val stopping = night.stopping
        startJob = viewModelScope.launch {
            // One night at a time: a stop still under way, from this screen or one before it, would otherwise end this night.
            earlier?.cancelAndJoin()
            stopping?.join()
            _state.value =
                try {
                    // Locked, with no film titles: on its own the app cannot tell whose night it is.
                    val urls = night.core.startNight(SpieltischConfig(kidsLocked = true, dataDir = night.dataDir))
                    night.announcer.announce(urls.joinUrl)
                    GameNightUi.Ready(urls.tvUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    GameNightUi.Failed(e.message ?: "The game night could not start.")
                }
        }
    }

    /** True when a game was running and the room is back in the lobby; false when there was nothing to go back from. */
    suspend fun back(): Boolean = night.core.nightBack()

    /**
     * On the session's scope: leaving the screen clears this view model, which must not cancel the stop.
     * It waits out an in-flight start first, so a host that was still coming up is stopped, not orphaned.
     * The state goes back to [GameNightUi.Starting], so coming back never shows the ended night's page.
     */
    fun stop() {
        // Phones stop looking at a night that is ending, before the host goes away under them.
        night.announcer.withdraw()
        _state.value = GameNightUi.Starting
        // Going to the background and leaving the screen both stop; the second has nothing left to end.
        if (startJob == null && night.stopping != null) return
        val starting = startJob
        startJob = null
        night.stopping =
            night.scope.launch {
                starting?.cancelAndJoin()
                // A start that finished after the withdraw above has announced a night that is about to end.
                night.announcer.withdraw()
                night.core.stopNight()
            }
    }
}
