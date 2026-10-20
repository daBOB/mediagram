package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import model.HouseholdWords
import ui.tv.setup.TvTextQuestion

/** Create a household's row in the list, and A new grown-up's among the admins: no household or profile has an empty id. */
private const val NEW = ""

/**
 * Where the owner's Households panel stands: the list, a household's
 * actions, or one of their steps — each a whole screen, as the web's forms
 * under a row become on a remote. [id] is the household it is about.
 */
internal sealed interface TvHouseholdStep {
    val id: String? get() = null

    /** The list, the remote on household [row] — [NEW] for Create a household — or the first. */
    data class List(val row: String? = null) : TvHouseholdStep

    /** What the rule allows on household [id], the remote on [action], the step it opened, or the first. */
    data class Actions(override val id: String, val action: String? = null) : TvHouseholdStep

    /** [typed], once sent: what the field held, kept while a PIN is asked over it. */
    data class Rename(override val id: String, val typed: String? = null) : TvHouseholdStep

    data class ChangeAdmin(override val id: String) : TvHouseholdStep

    data class Move(override val id: String) : TvHouseholdStep

    data class CreateName(val name: String = "") : TvHouseholdStep

    data class CreateAdmin(val name: String) : TvHouseholdStep

    data class CreateGrownUp(val name: String, val typed: String = "") : TvHouseholdStep

    /** Back: out the way the remote came in, a row at a time; null leaves the panel. */
    fun back(): TvHouseholdStep? =
        when (this) {
            is List -> null
            is Actions -> List(id)
            is Rename -> Actions(id, HouseholdWords.RENAME)
            is ChangeAdmin -> Actions(id, HouseholdWords.CHANGE_ADMIN)
            is Move -> Actions(id, HouseholdWords.MOVE_HERE)
            is CreateName -> List(NEW)
            is CreateAdmin -> CreateName(name)
            is CreateGrownUp -> CreateAdmin(name)
        }

    /** Where a change sent from here leaves the remote once it took: on its household's row, or on Create a household. */
    fun done(): TvHouseholdStep = List(id ?: NEW)
}

/**
 * [step]'s screen. [go] moves to another step; [send] sends a change from
 * a step — this one, holding what was typed — which stays, saying [notice]
 * if it was refused, until it takes. A blank name is not sent, as the web's
 * forms send none.
 */
@Composable
internal fun TvHouseholdStepScreen(
    step: TvHouseholdStep,
    part: HouseholdsOwnerUiState.Open,
    notice: String?,
    owner: HouseholdsOwnerViewModel,
    go: (TvHouseholdStep) -> Unit,
    send: (from: TvHouseholdStep, change: () -> Unit) -> Unit,
) {
    val row = part.rows.find { it.household.id == step.id }
    val name = row?.household?.name.orEmpty()
    when (step) {
        is TvHouseholdStep.List ->
            TvChoicesScreen(HouseholdWords.HOUSEHOLDS, null, part.rows.map { it.household.id to "${it.household.name} · ${it.line}" } + (NEW to HouseholdWords.CREATE_A_HOUSEHOLD), step.row) {
                go(if (it == NEW) TvHouseholdStep.CreateName() else TvHouseholdStep.Actions(it))
            }
        is TvHouseholdStep.Actions -> {
            val offered =
                listOfNotNull(
                    HouseholdWords.RENAME.takeIf { row?.canRename == true },
                    HouseholdWords.CHANGE_ADMIN.takeIf { row?.canChangeAdmin == true },
                    HouseholdWords.MOVE_HERE.takeIf { row?.canMove == true },
                    HouseholdWords.USE_HERE.takeIf { row?.canUseHere == true },
                    HouseholdWords.DELETE.takeIf { row?.canDelete == true },
                )
            TvChoicesScreen(name, row?.line, offered.map { it to it }, step.action, notice) { action ->
                when (action) {
                    HouseholdWords.RENAME -> go(TvHouseholdStep.Rename(step.id))
                    HouseholdWords.CHANGE_ADMIN -> go(TvHouseholdStep.ChangeAdmin(step.id))
                    HouseholdWords.MOVE_HERE -> go(TvHouseholdStep.Move(step.id))
                    HouseholdWords.USE_HERE -> send(step) { owner.useHere(step.id) }
                    else -> send(step) { owner.delete(step.id) }
                }
            }
        }
        is TvHouseholdStep.Rename ->
            TvNameStep("${HouseholdWords.RENAME} · $name", HouseholdWords.HOUSEHOLD_NAME, step.typed ?: name, notice) { typed ->
                send(step.copy(typed = typed)) { owner.rename(step.id, typed) }
            }
        is TvHouseholdStep.ChangeAdmin ->
            TvChoicesScreen("${HouseholdWords.CHANGE_ADMIN} · $name", null, row?.adminChoices.orEmpty().map { it.id to it.name }, null, notice) {
                send(step) { owner.changeAdmin(step.id, it) }
            }
        is TvHouseholdStep.Move ->
            TvChoicesScreen("${HouseholdWords.MOVE_HERE} · $name", null, row?.moveChoices.orEmpty().map { it.id to it.name }, null, notice) {
                send(step) { owner.moveHere(step.id, it) }
            }
        is TvHouseholdStep.CreateName ->
            TvNameStep(HouseholdWords.CREATE_A_HOUSEHOLD, HouseholdWords.HOUSEHOLD_NAME, step.name, null) { go(TvHouseholdStep.CreateAdmin(it)) }
        is TvHouseholdStep.CreateAdmin -> {
            val admins = listOf(NEW to HouseholdWords.A_NEW_GROWN_UP) + part.createChoices.map { it.id to it.name }
            TvChoicesScreen("${HouseholdWords.CREATE_A_HOUSEHOLD} · ${step.name}", HouseholdWords.ADMIN, admins, null, notice) { id ->
                if (id == NEW) go(TvHouseholdStep.CreateGrownUp(step.name)) else send(step) { owner.create(step.name, id, null) }
            }
        }
        is TvHouseholdStep.CreateGrownUp ->
            TvNameStep("${HouseholdWords.CREATE_A_HOUSEHOLD} · ${step.name}", HouseholdWords.NEW_GROWN_UP_NAME, step.typed, notice) { typed ->
                send(step.copy(typed = typed)) { owner.create(step.name, null, typed) }
            }
    }
}

/** A name asked on the TV's text field, starting from [initial]; [onName] hears it trimmed, and never a blank one. */
@Composable
private fun TvNameStep(
    heading: String,
    label: String,
    initial: String,
    notice: String?,
    onName: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    TvTextQuestion(heading = heading, explanation = null, label = label, value = value, onValue = { value = it }, error = notice, onSubmit = {
        value.trim().takeIf { it.isNotEmpty() }?.let(onName)
    })
}
