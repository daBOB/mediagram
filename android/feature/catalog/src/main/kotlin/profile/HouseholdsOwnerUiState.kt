package catalog.profile

import model.Household
import model.HouseholdAction
import model.Households
import model.Profile
import model.householdAllowed
import model.householdLine
import java.text.Normalizer

/** The household actions of the owner's view, as the web's owner view draws them. */
sealed interface HouseholdsOwnerUiState {
    /** The households not read yet. */
    data object Loading : HouseholdsOwnerUiState

    /** Neither the owner watching nor the owner's view showing. */
    data object Closed : HouseholdsOwnerUiState

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
    val boss = owner ?: return HouseholdsOwnerUiState.Closed
    val creators = everyone.filter { !it.kids && may(boss, HouseholdAction.CREATE, "", it.id) }
    return HouseholdsOwnerUiState.Open(rowsFor(boss), creators, notice, answers, sending)
}

/** Every live household as a row: what [boss], the owner, may do to it by the household rule. */
internal fun Households.rowsFor(boss: Profile): List<HouseholdRow> {
    val grownUps = everyone.filterNot { it.kids }
    return list.map { household ->
        val id = household.id
        HouseholdRow(
            household = household,
            line = householdLine(everyone.find { it.id == household.adminId }?.name, members(id)),
            isHere = id == joined,
            canRename = may(boss, HouseholdAction.RENAME, id),
            canUseHere = id != joined && may(boss, HouseholdAction.JOIN, id),
            canDelete = may(boss, HouseholdAction.DELETE, id),
            adminChoices = grownUps.filter { it.id != household.adminId && may(boss, HouseholdAction.SET_ADMIN, id, it.id) },
            moveChoices = grownUps.filter { may(boss, HouseholdAction.MOVE, id, it.id) },
        )
    }
}

private fun Households.may(
    boss: Profile,
    action: HouseholdAction,
    id: String,
    targetId: String = "",
) = everyone.householdAllowed(list.map { it.id }, boss.id, action, id, targetId)

/** The owner is who is watching here: the part opens at once for them, on every visit, as on the web. */
internal fun Households.watchedByOwner(chosenId: String?) = chosenId != null && chosenId == owner?.id

/** A name as household names are compared: case, spacing and Unicode form aside. */
internal fun householdKey(name: String) = Normalizer.normalize(name, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ").lowercase()
