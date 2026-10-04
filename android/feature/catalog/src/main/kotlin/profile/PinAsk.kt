package catalog.profile

import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.ProfileOutcome
import model.ProfileRequest

private val FOUR_DIGITS = Regex("[0-9]{$PIN_LENGTH}")

/**
 * The PIN prompt's half of a view model, shared by the picker and Manage so
 * both ask the same way — the web's `askPin`. It checks only the shape, so a
 * mistyped PIN is said at once; whether it is the right one is the core's to
 * say. A new PIN is typed twice: nobody can reset the admin's own, so one
 * slip there would lock the household's only admin out for good.
 *
 * A refused PIN keeps the prompt open with the reason. A refused new PIN —
 * typed twice and the right shape, so no other would fare better — ends the
 * prompt and hands the reason to `refused`: what was turned down is
 * something else, a name or a current PIN sent beside it.
 *
 * Only the latest [ask] may answer: a request still out when the prompt was
 * cancelled or replaced finishes without touching the screen.
 */
internal class PinAsk(
    private val scope: CoroutineScope,
) {
    private val shown = MutableStateFlow<PinPrompt?>(null)
    val prompt: StateFlow<PinPrompt?> = shown.asStateFlow()

    private var first: String? = null
    private var asking = 0L
    private var send: suspend (String) -> ProfileOutcome = { ProfileOutcome.Invalid }
    private var done: (String) -> Unit = {}
    private var refused: (String) -> Unit = {}

    fun ask(
        prompt: PinPrompt,
        send: suspend (pin: String) -> ProfileOutcome,
        done: (pin: String) -> Unit,
        refused: (sentence: String) -> Unit = {},
    ) {
        asking++
        first = null
        this.send = send
        this.done = done
        this.refused = refused
        shown.value = prompt
    }

    /** A PIN typed: the wrong shape is said, a new PIN's first entry held, a second that differs refused, anything else sent. */
    fun enter(pin: String) {
        val current = shown.value ?: return
        if (current.busy) return
        when {
            !FOUR_DIGITS.matches(pin) -> shown.value = current.copy(error = PIN_SHAPE)
            current.newPin && !current.confirming -> {
                first = pin
                shown.value = current.copy(confirming = true, error = null)
            }
            current.newPin && pin != first -> {
                first = null
                shown.value = current.copy(confirming = false, error = PINS_DIFFER)
            }
            else -> sendNow(current, pin)
        }
    }

    fun cancel() {
        asking++
        first = null
        shown.value = null
    }

    private fun sendNow(
        current: PinPrompt,
        pin: String,
    ) {
        val started = asking
        val sending = send
        val finish = done
        val tell = refused
        shown.value = current.copy(busy = true, error = null)
        scope.launch {
            val outcome = attempt { sending(pin) }
            if (started != asking) return@launch
            val reason = outcome?.sentence() ?: DID_NOT_GO_THROUGH
            when {
                outcome == ProfileOutcome.Done -> {
                    cancel()
                    finish(pin)
                }
                current.newPin -> {
                    cancel()
                    tell(reason)
                }
                else -> shown.value = current.copy(busy = false, error = reason)
            }
        }
    }
}

/** [block]'s answer, or null when the core could not be asked at all — which says nothing about the PIN. */
internal suspend fun attempt(block: suspend () -> ProfileOutcome): ProfileOutcome? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        null
    }

/**
 * [request] sent through the repository; a change that took is pushed to the
 * other devices now, not at the next five-minute round — an unlock changes
 * nothing stored, so it has nothing to push.
 */
internal suspend fun WatchStateRepository.manageAndShare(
    request: ProfileRequest,
    sync: WatchSync,
): ProfileOutcome = manage(request).also { if (it == ProfileOutcome.Done && request !is ProfileRequest.Unlock) sync.soon() }
