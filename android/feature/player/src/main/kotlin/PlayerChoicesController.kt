package player

import data.CatalogRepository
import data.PlayerPreferences
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.MediaSet

/**
 * What this viewer has chosen for the open title, and how a choice made
 * for one show is kept from leaking into the next — split out of
 * [PlayerViewModel] to keep that file under the project's line guideline.
 * Speed is the only field [PlayerChoices] carries this phase; audio,
 * subtitles and framing join it, and this controller, later.
 */
class PlayerChoicesController(
    private val launchScope: CoroutineScope,
    private val session: PlayerSession,
    private val repository: WatchStateRepository,
    private val catalogRepository: CatalogRepository,
    private val preferences: PlayerPreferences,
    private val handle: PlayerHandle,
) {
    private val _openSet = MutableStateFlow<MediaSet?>(null)
    val openSet: StateFlow<MediaSet?> = _openSet.asStateFlow()

    private val _choices = MutableStateFlow(PlayerChoices.Default)
    val choices: StateFlow<PlayerChoices> = _choices.asStateFlow()

    /** Where [setSpeed] remembers a choice; null before the open title's set resolves. */
    private var openScope: String? = null

    /**
     * Set by [setSpeed] for the title now open, so a still in-flight
     * [resolve] does not clobber a choice made while it was working.
     * Cleared by [reset] — a genuinely different title opening, never a
     * rotation reopening the same one; see [PlayerViewModel.open].
     */
    private var userChoseSpeed = false

    /** Drops whatever the last title had, for a genuinely new one. */
    fun reset() {
        openScope = null
        _openSet.value = null
        _choices.value = PlayerChoices.Default
        userChoseSpeed = false
    }

    /**
     * Resolves the set behind [setId], its preference scope, and the
     * speed remembered under it, applying it to [handle]. Falls back to a
     * raw `set:<id>` scope when the set itself cannot be resolved — a cold
     * start straight into the player with the catalog not yet loaded.
     *
     * Every read here is checked against [PlayerSession.openSetId] before
     * it is acted on: a later [PlayerViewModel.open] racing ahead of this
     * must win, never be overwritten by a resolution landing after it —
     * and [userChoseSpeed] guards the same race for a choice made by
     * hand: whichever speed the viewer picked while this was still
     * working wins, and is remembered under the scope this resolves
     * rather than lost or overwritten by it.
     */
    suspend fun resolve(setId: String) {
        val set = safely(null) { catalogRepository.mediaSet(setId) }
        if (session.openSetId != setId) return
        _openSet.value = set
        val scope = scopeOf(set) ?: "set:$setId"
        openScope = scope

        if (userChoseSpeed) {
            rememberSpeed(scope, _choices.value.speed)
            return
        }

        val profileId = repository.chosenProfileId.value
        val loaded = if (profileId == null) emptyMap() else safely(emptyMap()) { preferences.load(profileId, scope) }
        val speed = speedOrDefault(loaded["speed"])
        if (session.openSetId != setId || userChoseSpeed) return
        _choices.value = PlayerChoices(speed = speed)
        handle.setPlaybackSpeed(speed)
    }

    /** Applies a chosen speed and remembers it for this show; a no-op write with no profile chosen. */
    fun setSpeed(rate: Float) {
        userChoseSpeed = true
        _choices.value = _choices.value.copy(speed = rate)
        handle.setPlaybackSpeed(rate)
        // Remembered once [resolve] has a scope, if it hasn't yet.
        val scope = openScope ?: return
        rememberSpeed(scope, rate)
    }

    /** Fire-and-forget: a core round trip failing to remember a speed is not a reason to crash the player. */
    private fun rememberSpeed(scope: String, rate: Float) {
        val profileId = repository.chosenProfileId.value ?: return
        launchScope.launch {
            safely(Unit) { preferences.remember(profileId, scope, "speed", speedPreferenceValue(rate)) }
        }
    }

    /** Runs [block], answering [default] for anything but cancellation — which is rethrown, so a cancelled coroutine stays cancelled. */
    private suspend fun <T> safely(default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        default
    }
}
