package catalog.profile

import model.Household
import model.HouseholdAction
import model.Households
import model.Profile
import model.householdAllowed
import model.householdLine
import java.text.Normalizer

/** The owner's Households part, as the web's `settings-households.js` draws it. */
sealed interface HouseholdsOwnerUiState {
    /** The households not read yet. */
    data object Loading : HouseholdsOwnerUiState

    /** The owner's note; [canOpen] when there is an owner, whose button asks their PIN. */
    data class Closed(
        val canOpen: Boolean = false,
    ) : HouseholdsOwnerUiState

    /**
     * Every live household as a row, and [createChoices]: the grown-ups who
     * could run a new one, beside a new grown-up. [notice] says why the last
     * change did not take, until the next is sent. [answers] counts the
     * changes answered, so a form held open until its answer sees one even
     * when the notice repeats. [sending] while a change is out with the
     * owner's PIN.
     */
    data class Open(
        val rows: List<HouseholdRow>,
        val createChoices: List<Profile>,
        val notice: String? = null,
        val answers: Int = 0,
        val sending: Boolean = false,
    ) : HouseholdsOwnerUiState
}

/** One household, its [line], and what the owner may do to it by the household rule. */
data class HouseholdRow(
    val household: Household,
    val line: String,
    /** The household this device shows. */
    val isHere: Boolean,
    val canRename: Boolean,
    val canUseHere: Boolean,
    /** Only an empty one, never Home. */
    val canDelete: Boolean,
    /** Its own grown-ups but its admin; none for the owner's own household, which the owner runs. */
    val adminChoices: List<Profile>,
    /** Grown-ups who may move in, with their kids. */
    val moveChoices: List<Profile>,
) {
    val canChangeAdmin: Boolean get() = adminChoices.isNotEmpty()
    val canMove: Boolean get() = moveChoices.isNotEmpty()
}

/** The part open for the owner, over everyone on the install; closed again once there is no owner. */
internal fun Households.ownersPart(
    notice: String?,
    answers: Int,
    sending: Boolean,
): HouseholdsOwnerUiState {
    val boss = owner ?: return HouseholdsOwnerUiState.Closed()
    val live = list.map { it.id }
    val grownUps = everyone.filterNot { it.kids }

    fun may(
        action: HouseholdAction,
        id: String,
        targetId: String = "",
    ) = everyone.householdAllowed(live, boss.id, action, id, targetId)

    val rows =
        list.map { household ->
            val id = household.id
            HouseholdRow(
                household = household,
                line = householdLine(everyone.find { it.id == household.adminId }?.name, members(id)),
                isHere = id == joined,
                canRename = may(HouseholdAction.RENAME, id),
                canUseHere = id != joined && may(HouseholdAction.JOIN, id),
                canDelete = may(HouseholdAction.DELETE, id),
                adminChoices = grownUps.filter { it.id != household.adminId && may(HouseholdAction.SET_ADMIN, id, it.id) },
                moveChoices = grownUps.filter { may(HouseholdAction.MOVE, id, it.id) },
            )
        }
    return HouseholdsOwnerUiState.Open(rows, grownUps.filter { may(HouseholdAction.CREATE, "", it.id) }, notice, answers, sending)
}

/** The owner is who is watching here: the part opens at once for them, on every visit, as on the web. */
internal fun Households.watchedByOwner(chosenId: String?) = chosenId != null && chosenId == owner?.id

/** A name as household names are compared: case, spacing and Unicode form aside. */
internal fun householdKey(name: String) = Normalizer.normalize(name, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ").lowercase()
