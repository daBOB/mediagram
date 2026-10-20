package model

/** The words of households, the web's `HOUSEHOLD_WORDS`, held to `household-rules.json`. */
object HouseholdWords {
    const val HOUSEHOLDS = "Households"
    const val OWNER = "Owner"
    const val OWNER_NOTE = "Households are the owner’s to set up."
    const val OWNERS_PIN = "The owner’s PIN"
    const val THIS_DEVICE = "This device"
    const val SWITCH_HOUSEHOLD = "Switch household"
    const val JOIN_A_HOUSEHOLD = "Join a household"
    const val JOIN_NOTE = "This device shows one household’s profiles. Choose yours, then its admin’s PIN."
    const val CREATE_A_HOUSEHOLD = "Create a household"
    const val HOUSEHOLD_NAME = "Household name"
    const val ADMIN = "Admin"
    const val A_NEW_GROWN_UP = "A new grown-up"
    const val NEW_GROWN_UP_NAME = "The new grown-up’s name"
    const val RENAME = "Rename"
    const val CHANGE_ADMIN = "Change admin"
    const val MOVE_HERE = "Move a grown-up here"
    const val USE_HERE = "Use on this device"
    const val DELETE = "Delete"
    const val HOUSEHOLD_NAME_TAKEN = "A household with that name already exists."
    const val PROFILE_NAME_TAKEN = "That name is already used on this install."
}

/** [HouseholdWords] keyed as the web's object, so the fixture compares them whole. */
val HOUSEHOLD_WORDS: Map<String, String> =
    with(HouseholdWords) {
        mapOf(
            "households" to HOUSEHOLDS,
            "owner" to OWNER,
            "ownerNote" to OWNER_NOTE,
            "ownersPin" to OWNERS_PIN,
            "thisDevice" to THIS_DEVICE,
            "switchHousehold" to SWITCH_HOUSEHOLD,
            "joinAHousehold" to JOIN_A_HOUSEHOLD,
            "joinNote" to JOIN_NOTE,
            "createAHousehold" to CREATE_A_HOUSEHOLD,
            "householdName" to HOUSEHOLD_NAME,
            "admin" to ADMIN,
            "aNewGrownUp" to A_NEW_GROWN_UP,
            "newGrownUpName" to NEW_GROWN_UP_NAME,
            "rename" to RENAME,
            "changeAdmin" to CHANGE_ADMIN,
            "moveHere" to MOVE_HERE,
            "useHere" to USE_HERE,
            "delete" to DELETE,
            "householdNameTaken" to HOUSEHOLD_NAME_TAKEN,
            "profileNameTaken" to PROFILE_NAME_TAKEN,
        )
    }

/** A household's line in the owner's list: who runs it, and how many profiles are in it. */
fun householdLine(
    adminName: String?,
    count: Int,
): String {
    val admin = if (adminName == null) "No admin" else "Admin $adminName"
    return "$admin · $count profile${if (count == 1) "" else "s"}"
}

/**
 * What joining asks for, given the household's [admin], or none: that admin's
 * PIN, or the owner's, which opens every household. Where the owner runs it,
 * the owner's PIN is the only one, said once.
 */
fun joinPinTitle(admin: Profile?): String =
    if (admin == null || admin.owner) HouseholdWords.OWNERS_PIN else "${admin.name}’s PIN, or the owner’s"
