package player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.ArmedCast
import data.CatalogRepository
import data.LiveSession
import data.TogetherLive
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.DailyLimitState
import model.dailyLimitState
import model.forKidsProfile
import model.together.CastState
import model.together.CastWords
import model.together.TvAnswer
import model.together.TvState
import model.together.ageReason
import model.together.answerOf
import model.together.timeReason
import java.util.UUID
import javax.inject.Inject

/** A cast the TV accepted: open [set], in place of what plays when [replace]. */
data class CastOpen(
    val set: String,
    val replace: Boolean,
)

/**
 * The TV's side of a cast: answers each request once, and tells the UI to
 * open an accepted one. Collecting [opening] is what listens.
 */
@HiltViewModel
class CastReceiverViewModel @Inject constructor(
    private val live: TogetherLive,
    private val catalog: CatalogRepository,
    private val repository: WatchStateRepository,
    private val handle: PlayerHandle,
) : ViewModel() {
    private val screen = UUID.randomUUID().toString()
    private val answered = mutableSetOf<Pair<Int, CastState>>()
    private val accepted = MutableStateFlow<CastOpen?>(null)

    val opening: StateFlow<CastOpen?> =
        flow {
            coroutineScope {
                launch { live.requests.collect { it?.let { request -> answer(request) } } }
                accepted.collect { emit(it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The UI has navigated. */
    fun opened() {
        accepted.value = null
    }

    private suspend fun answer(request: LiveSession) {
        val s = request.session
        val cast = s.cast ?: return
        val key = request.id to cast.state
        if (key in answered) return
        try {
            // A set that no longer resolves is still open: busy never reads as idle.
            val busy = handle.openSet.value?.let { titleLine(catalog.mediaSet(it)).ifEmpty { it } }
            val answer = answerOf(s, TvState(screen, busy, refusalOf(s.set, cast.profile)), System.currentTimeMillis())
            if (answer is TvAnswer.Ignore) return
            answered += key
            when (answer) {
                is TvAnswer.Refuse -> live.refuse(request.id, screen, answer.note)
                is TvAnswer.Busy -> live.busy(request.id, screen, answer.note)
                TvAnswer.Accept -> {
                    live.castOnOpen(ArmedCast(request.id, s.set, cast.profile, cast.from, s.playing))
                    accepted.value = CastOpen(s.set, replace = busy != null)
                }
                TvAnswer.Ignore -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed read or send must not take the app down; the next emission tries again.
            answered -= key
        }
    }

    /** Why this TV won't play [set] as [profileId], in the order the web gives it; null when it will. */
    private suspend fun refusalOf(
        set: String,
        profileId: String,
    ): String? {
        // The player plays as the caster only when it finds them in this same list.
        val profile = repository.profiles.value.find { it.id == profileId } ?: return CastWords.PROFILE_UNKNOWN
        val media = catalog.mediaSet(set) ?: return CastWords.LIBRARY_MISSING
        if (!profile.kids) return null
        val marks = repository.snapshotOf(profileId)?.kidsMarks.orEmpty()
        if (forKidsProfile(listOf(media), marks, profile.kidsLimit).isEmpty()) return ageReason(profile.name)
        if (dailyLimitState(profile.dailyLimit, repository.watchedToday(profileId)).state == DailyLimitState.OUT) return timeReason(profile.name)
        return null
    }
}
