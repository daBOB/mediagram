package model

/**
 * One thing asked of the core about who watches — checked there against
 * who is asking (`actorId`) and their PIN. Plain classes, not data classes:
 * they carry PINs, and a generated `toString` would print one into any log
 * or failure message it ever reached.
 */
sealed interface ProfileRequest {
    /** The first grown-up on a device that has none; it runs the household. */
    class CreateFirstAdmin(
        val name: String,
        val newPin: String,
    ) : ProfileRequest

    /** Opens a grown-up's profile from the picker; a kid's needs no PIN. Changes nothing stored. */
    class Unlock(
        val id: String,
        val pin: String,
    ) : ProfileRequest

    /** Makes [id] the household's admin; with no PIN yet, [pin] becomes its PIN. */
    class ClaimAdmin(
        val id: String,
        val pin: String,
    ) : ProfileRequest

    class CreateGrownUp(
        val actorId: String,
        val pin: String,
        val name: String,
        val newPin: String,
    ) : ProfileRequest

    /** [age] is the new kid's limit, 6 or 12. */
    class CreateKid(
        val actorId: String,
        val pin: String,
        val name: String,
        val age: Int,
    ) : ProfileRequest

    /** A grown-up's kids go with it. */
    class Remove(
        val actorId: String,
        val pin: String,
        val id: String,
    ) : ProfileRequest

    /** [pin] is ignored when a grown-up with no PIN sets its own first one. */
    class SetPin(
        val actorId: String,
        val pin: String,
        val id: String,
        val newPin: String,
    ) : ProfileRequest

    class SetKidsAge(
        val actorId: String,
        val pin: String,
        val id: String,
        val age: Int,
    ) : ProfileRequest
}

/** How the core answered a [ProfileRequest] — the contract's reasons. */
sealed interface ProfileOutcome {
    data object Done : ProfileOutcome

    /** A malformed name, new PIN or age — or a store that could not be written. */
    data object Invalid : ProfileOutcome

    /** The new profile's name is one a profile here already answers to; sync would read the two as one viewer. */
    data object NameTaken : ProfileOutcome

    data object NotFound : ProfileOutcome

    /** Five wrong PINs in a row for one profile: its PIN is not compared again for [seconds]. */
    data class Wait(
        val seconds: Int,
    ) : ProfileOutcome

    /** A grown-up with no PIN yet — it sets one first. */
    data object NoPin : ProfileOutcome

    /** Also the answer to a malformed current PIN: it is compared, and counts. */
    data object WrongPin : ProfileOutcome

    data object NotAllowed : ProfileOutcome

    /**
     * A first profile on a device that has not yet taken in a sync round: it
     * has not heard who the household already is, and a profile made blind
     * under a member's name would hand its PIN to that member everywhere.
     */
    data object NotSynced : ProfileOutcome
}
