package ui.profile

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ui.common.profile.ManageActions

/**
 * What became of the adds — the web's `act(said, …)`, which says an add's
 * refusal beside its form and keeps what was typed there until the add went
 * through. A form is its household part's id, "" for an unnamed part. Manage
 * counts its answers but not whose they are, so every change notes where it
 * was sent from as it goes: an add its form ([sent]),
 * anything else nowhere ([elsewhere], through [notingOthers]). The answer
 * after the latest note is that change's. An add whose PIN prompt was
 * dismissed sent nothing, and the next change's note replaces it.
 */
@Stable
internal class OwnerAdds {
    private var from by mutableStateOf<String?>(null)
    private var at by mutableIntStateOf(0)
    private val took = mutableStateMapOf<String, Int>()
    private var seen: Int? = null

    /** An add from [form] going out, [answers] answers in. */
    fun sent(form: String, answers: Int) {
        from = form
        at = answers
    }

    fun elsewhere() {
        from = null
    }

    /** The add form [answers]' latest answer came back to, if it answered an add. */
    fun answered(answers: Int): String? = from?.takeIf { answers == at + 1 }

    /** How many adds from [form] went through: it empties as this rises. */
    fun took(form: String): Int = took[form] ?: 0

    /** Manage's [answers] heard, with its [notice]: none means the change took. */
    fun heard(answers: Int, notice: String?) {
        val before = seen
        seen = answers
        if (before != null && answers > before && notice == null) answered(answers)?.let { took[it] = took(it) + 1 }
    }
}

/** These actions, every change but an add noted as sent from [elsewhere][OwnerAdds.elsewhere] before it goes. */
internal fun ManageActions.notingOthers(adds: OwnerAdds) =
    ManageActions(
        onActAs = onActAs,
        onAddKid = onAddKid,
        onSetKidsAge = { id, age ->
            adds.elsewhere()
            onSetKidsAge(id, age)
        },
        onSetDailyLimit = { id, minutes ->
            adds.elsewhere()
            onSetDailyLimit(id, minutes)
        },
        onSetHiddenSections = { id, sections ->
            adds.elsewhere()
            onSetHiddenSections(id, sections)
        },
        onRemove = { id ->
            adds.elsewhere()
            onRemove(id)
        },
        onAddGrownUp = onAddGrownUp,
        onChangePin = { id ->
            adds.elsewhere()
            onChangePin(id)
        },
        onClose = onClose,
    )
