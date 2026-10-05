package catalog.profile

import model.Profile
import model.admin

/** What "Who's watching?" shows, and what it is doing while it decides. */
sealed interface ProfileUiState {
    /**
     * Deciding what to show: reading this device's own choice and, only
     * when it has none, giving a sync round up to five seconds to bring in
     * the names other devices already use.
     */
    data object Loading : ProfileUiState

    /**
     * Nobody chosen yet, or the bar action asked to change who is. [canStay]
     * offers "Stay as I am" — true only on a reopen, and only while the
     * profile being watched as is still here. [error] is a failed load or
     * choice, with a way to try again; [notice] a refusal to say above the
     * tiles until the viewer tries something else, as the web's picker says one.
     * [synced] says a sync round has landed on this device, so [profiles] holds
     * the household's names.
     */
    data class Picking(
        val profiles: List<Profile>,
        val canStay: Boolean,
        val error: String? = null,
        val notice: String? = null,
        val synced: Boolean = true,
    ) : ProfileUiState {
        private val noGrownUp: Boolean get() = profiles.none { !it.kids }

        /**
         * No grown-up here at all — a new household, or one that knows only
         * kids — after a sync round has said so: the first profile is made,
         * and runs the household.
         */
        val needsFirstProfile: Boolean get() = synced && noGrownUp

        /**
         * No grown-up here, and no sync round landed yet: the household's
         * names may still be on their way, and a first profile made blind
         * under one of them would hand its PIN to that member. Waits, with
         * Try again.
         */
        val awaitingHousehold: Boolean get() = !synced && noGrownUp

        /**
         * Grown-ups, none of them the admin: "Who runs this household?" until
         * one claims. The earliest claim wins when devices meet.
         */
        val needsAdmin: Boolean get() = !noGrownUp && profiles.admin() == null

        /** Who may answer that question, or open Manage profiles — which is offered once there is anyone. */
        val grownUps: List<Profile> get() = profiles.filterNot { it.kids }
    }

    data class Chosen(
        val profile: Profile,
    ) : ProfileUiState
}
