package model

// Households asked of the core, beside the profile requests in ProfileRequests.kt:
// plain classes for the same reason, since they carry PINs.

/**
 * This device shows household [id] from now, opened by its admin's PIN or the
 * owner's. Local to this device: sent with `manage`, never `manageAndShare`.
 */
class JoinHousehold(
    val id: String,
    val pin: String,
) : ProfileRequest

/**
 * The owner makes household [name], run by grown-up [adminId], who moves in
 * with their kids, or by a new one called [adminName] — one of the two.
 * [newPin] is the new one's first PIN, and the existing one's where they
 * have none.
 */
class CreateHousehold(
    val actorId: String,
    val pin: String,
    val name: String,
    val adminId: String?,
    val adminName: String?,
    val newPin: String?,
) : ProfileRequest

class RenameHousehold(
    val actorId: String,
    val pin: String,
    val id: String,
    val name: String,
) : ProfileRequest

/** [newPin] is the new admin's first PIN, where they have none. */
class SetHouseholdAdmin(
    val actorId: String,
    val pin: String,
    val id: String,
    val adminId: String,
    val newPin: String?,
) : ProfileRequest

/** A grown-up and their kids. Into a household nobody runs they run it, and [newPin] is their first PIN where they have none. */
class MoveToHousehold(
    val actorId: String,
    val pin: String,
    val id: String,
    val profileId: String,
    val newPin: String? = null,
) : ProfileRequest

/** An empty household other than Home, for good. */
class DeleteHousehold(
    val actorId: String,
    val pin: String,
    val id: String,
) : ProfileRequest
