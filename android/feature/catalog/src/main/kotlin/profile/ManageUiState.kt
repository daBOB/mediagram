package catalog.profile

import model.Household
import model.Households
import model.KIDS_LIMITS
import model.Profile
import model.ownerOf

/** A new kid starts at the stricter limit; its parent raises it — the web's add-kid form's default. */
val NEW_KID_LIMIT: Int = KIDS_LIMITS.min()

/** What Manage profiles shows: nothing, "Who are you?", or what that grown-up's role allows. */
sealed interface ManageUiState {
    data object Closed : ManageUiState

    /** Only a grown-up manages anything. [notice] says why the panel was left, or a first PIN refused. */
    data class ChoosingActor(
        val grownUps: List<Profile>,
        val notice: String? = null,
    ) : ManageUiState

    /**
     * [actor]'s PIN was right. For the admin, every other grown-up and a way
     * to add one; for everyone, its own [kids]. [notice] says why the last
     * change did not take, until the next one is sent. [answers] counts the
     * changes answered while managing: a control held until its answer waits
     * for it to move, which even a refusal it has heard before does. The
     * owner's rows reach into every household; [households], the live ones,
     * name them ([parts]).
     */
    data class Managing(
        val actor: Profile,
        val grownUps: List<Profile>,
        val kids: List<Profile>,
        val notice: String? = null,
        val answers: Int = 0,
        val households: List<Household> = emptyList(),
    ) : ManageUiState {
        val canAddGrownUp: Boolean get() = actor.admin

        /** The rows that carry the Sections control: every kid listed, every other grown-up — never the actor's own row. */
        val sectionRows: List<Profile> get() = grownUps + kids
    }
}

/**
 * The panel for [actorId] over [profiles], this device's household — the
 * web's `manageable`: the admin sees every other grown-up, everyone the kids
 * that are their own, and the owner everyone in every other household of
 * [install] too. With the actor gone (removed on another device) there is
 * nobody to manage as, so it asks who is here again.
 */
internal fun manageable(
    profiles: List<Profile>,
    actorId: String,
    notice: String?,
    answers: Int = 0,
    install: Households = Households(),
): ManageUiState {
    val actor = profiles.find { it.id == actorId && !it.kids } ?: return ManageUiState.ChoosingActor(profiles.filterNot { it.kids }, notice)
    // This device's household from [profiles], read with every change; the others only as the households were last read.
    val elsewhere = if (actor.owner) install.everyone.filter { it.household != actor.household } else emptyList()
    return ManageUiState.Managing(
        actor = actor,
        grownUps = ((if (actor.admin) profiles else emptyList()) + elsewhere).filter { !it.kids && it.id != actorId },
        kids = profiles.filter { it.kids && profiles.ownerOf(it) == actorId } + elsewhere.filter { it.kids },
        notice = notice,
        answers = answers,
        households = install.list,
    )
}
