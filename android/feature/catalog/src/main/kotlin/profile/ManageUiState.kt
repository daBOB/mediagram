package catalog.profile

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
     * change did not take, until the next one does.
     */
    data class Managing(
        val actor: Profile,
        val grownUps: List<Profile>,
        val kids: List<Profile>,
        val notice: String? = null,
    ) : ManageUiState {
        val canAddGrownUp: Boolean get() = actor.admin
    }
}

/**
 * The panel for [actorId] — the web's `manageable`: the admin sees every
 * other grown-up, everyone the kids that are their own. With the actor gone
 * (removed on another device) there is nobody to manage as, so it asks who
 * is here again.
 */
internal fun manageable(
    profiles: List<Profile>,
    actorId: String,
    notice: String?,
): ManageUiState {
    val actor = profiles.find { it.id == actorId && !it.kids } ?: return ManageUiState.ChoosingActor(profiles.filterNot { it.kids }, notice)
    return ManageUiState.Managing(
        actor = actor,
        grownUps = if (actor.admin) profiles.filter { !it.kids && it.id != actorId } else emptyList(),
        kids = profiles.filter { it.kids && profiles.ownerOf(it) == actorId },
        notice = notice,
    )
}
