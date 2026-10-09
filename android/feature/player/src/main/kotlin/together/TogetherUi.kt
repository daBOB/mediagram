package player.together

import data.LiveSession
import data.TOGETHER_UNREACHABLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.together.Session

/** What the player's Watch together button does: start a session, join the one live, or leave the one this screen is in. */
enum class TogetherButton { START, JOIN, LEAVE }

/**
 * What watch together shows in the player: the button, why it is greyed out
 * ([reason], `null` while it can be pressed), the "Together · TV, Tablet"
 * [label] while in a session, and the two-second [notice].
 */
data class TogetherUi(
    val button: TogetherButton = TogetherButton.START,
    val enabled: Boolean = false,
    val reason: String? = null,
    val label: String? = null,
    val notice: String? = null,
)

/** How long a notice stays up. */
internal const val NOTICE_MS = 2_000L

/**
 * Draws [TogetherUi], the twin of the web player's `together-view.js`:
 * [TogetherController] decides, this draws.
 */
internal class TogetherView(
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(TogetherUi())
    val ui: StateFlow<TogetherUi> = _ui.asStateFlow()
    private var hide: Job? = null

    fun show(text: String) {
        _ui.update { it.copy(notice = text) }
        hide?.cancel()
        hide =
            scope.launch {
                delay(NOTICE_MS)
                _ui.update { it.copy(notice = null) }
            }
    }

    /**
     * [current] is the session this screen follows, `null` out of one. Out
     * of one, [elsewhere] is the live session it could join, and [here]
     * whether that one's title is on this profile.
     */
    fun draw(
        current: Session?,
        elsewhere: LiveSession?,
        here: Boolean,
        available: Boolean,
    ) {
        val enabled = current != null || (available && here)
        _ui.update {
            it.copy(
                button =
                    when {
                        current != null -> TogetherButton.LEAVE
                        elsewhere != null -> TogetherButton.JOIN
                        else -> TogetherButton.START
                    },
                enabled = enabled,
                reason =
                    when {
                        enabled -> null
                        available -> NOT_HERE
                        else -> TOGETHER_UNREACHABLE
                    },
                label = current?.let { s -> "Together · ${s.members.joinToString(", ") { m -> m.label }}" },
            )
        }
    }
}
