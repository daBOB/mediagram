package player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import data.CastAnswer
import data.CastAnswer.Answer
import data.HouseholdsRepository
import data.TOGETHER_UNREACHABLE
import data.TogetherLive
import data.WatchStateRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import model.together.CAST_ANSWER_MS
import model.together.CastWords
import model.together.Member
import model.together.Playback
import model.together.refusedLine
import java.util.UUID
import javax.inject.Inject

/** Where Play on TV stands, from the press to the TV's answer. The screen shows [Asking] and [Told] as words, [Busy] as a Replace question. */
sealed interface CastAsk {
    data object Asking : CastAsk

    /** The TV is playing [title]; [id] is this offer's message, which Replace and Cancel act on. */
    data class Busy(val id: Int, val title: String) : CastAsk

    /** Nothing was cast, for [text]; the screen dismisses it. */
    data class Told(val text: String) : CastAsk

    /** The TV took request [id]: the remote opens. */
    data class On(val id: Int) : CastAsk
}

/** How long the TV is waited for: the server's own deadline and a margin for the round trip, as the web player's. */
internal const val CAST_WAIT_MS = CAST_ANSWER_MS + 3_000L

/**
 * Pressing Play on TV, on a phone or tablet: offers a title to the TV and
 * follows its answer. One ask at a time, from the press until the screen
 * dismisses what it said, as the web player's `askTv`.
 */
@HiltViewModel
class CastSenderViewModel @Inject constructor(
    private val live: TogetherLive,
    private val repository: WatchStateRepository,
    private val households: HouseholdsRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val _ask = MutableStateFlow<CastAsk?>(null)
    val ask: StateFlow<CastAsk?> = _ask.asStateFlow()

    private val me = Member(UUID.randomUUID().toString(), labelOf(context), remote = true)
    private var offered: Playback? = null
    private var profile = ""
    private var household = ""

    private val inSession = MutableStateFlow(false)

    /** The player says whether this device is in a session now; it greys Play on TV with [CastWords.LEAVE_FIRST]. */
    fun inSession(value: Boolean) {
        inSession.value = value
    }

    /**
     * Why Play on TV can't be pressed, or `null` when it may be: no household
     * joined yet, whose TVs alone are asked, Telegram unavailable, or this
     * device's player in a session (see [inSession]).
     */
    val castable: StateFlow<String?> =
        combine(live.available, inSession, households.view) { available, session, view ->
            when {
                view.joined == null -> CastWords.JOIN_FIRST
                !available -> TOGETHER_UNREACHABLE
                session -> CastWords.LEAVE_FIRST
                else -> null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Offers [own] to a TV of this device's household, as the chosen profile; nothing while an ask is open. */
    fun playOnTv(own: Playback) {
        if (_ask.value != null) return
        household = households.view.value.joined ?: return
        profile = repository.chosenProfileId.value ?: return
        offered = own
        send { live.offer(me, own, profile, household) }
    }

    /** After [CastAsk.Busy]: asks the TV to switch. */
    fun replace() {
        val busy = _ask.value as? CastAsk.Busy ?: return
        val own = offered ?: return
        send { live.replace(busy.id, me, own, profile, household) }
    }

    /** After [CastAsk.Busy]: withdraws the offer. */
    fun cancel() {
        val busy = _ask.value as? CastAsk.Busy ?: return
        live.cancel(busy.id)
        _ask.value = null
    }

    /** Closes what the TV said, or, at the Replace question, cancels it, as Escape does on the web; the wait itself can't be closed. */
    fun dismiss() {
        when (_ask.value) {
            is CastAsk.Busy -> cancel()
            is CastAsk.Told, is CastAsk.On -> _ask.value = null
            CastAsk.Asking, null -> Unit
        }
    }

    /** The screen is gone for good: an offer waiting on the Replace question is withdrawn, not left for the server's deadline. */
    override fun onCleared() = cancel()

    /**
     * Runs [call] to its answer even if the screen is left meanwhile, so a
     * waiting offer is not left live for the server's minute with nobody
     * listening (`TogetherLive.offer`); this model only stops listening.
     */
    private fun send(call: suspend () -> CastAnswer) {
        _ask.value = CastAsk.Asking
        val reply = viewModelScope.async(NonCancellable) { runCatching { call() } }
        viewModelScope.launch {
            val got = withTimeoutOrNull(CAST_WAIT_MS) { reply.await() }
            _ask.value =
                when {
                    got == null -> CastAsk.Told(CastWords.NO_ANSWER)
                    else -> got.fold(::stateOf) { CastAsk.Told(TOGETHER_UNREACHABLE) }
                }
        }
    }

    private fun stateOf(reply: CastAnswer): CastAsk {
        val id = reply.live?.id
        return when (reply.answer) {
            Answer.NONE -> CastAsk.Told(CastWords.NO_ANSWER)
            Answer.REFUSED -> CastAsk.Told(refusedLine(reply.note))
            // A reply without its message can't be acted on: the web player's unreachable.
            Answer.ON -> id?.let(CastAsk::On) ?: CastAsk.Told(TOGETHER_UNREACHABLE)
            Answer.BUSY -> id?.let { CastAsk.Busy(it, reply.note) } ?: CastAsk.Told(TOGETHER_UNREACHABLE)
        }
    }
}
