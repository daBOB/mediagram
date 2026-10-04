package catalog.profile

import model.Profile
import model.ProfileOutcome

/**
 * What the PIN prompt shows, on a phone and a television alike: [title]
 * (whose PIN, or a new one), whether it is a new PIN typed twice, and why
 * the last try failed. [busy] while the core is being asked.
 */
data class PinPrompt(
    val title: String,
    val newPin: Boolean,
    val confirming: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
) {
    /**
     * The web asks a new PIN in two fields at once; a remote has one pad, so
     * the second entry is a step of its own, labelled as the web's field is.
     */
    val heading: String get() = if (confirming) "The new PIN again" else title
}

/** A PIN is exactly four digits; a field or a pad hands one over only once it has them. */
const val PIN_LENGTH = 4

// The words below are the web player's own (`profile-picker.js`,
// `pin-prompt.js`, `profile-manage.js`), shared by the phone and the
// television so neither drifts from it. PICKER_NOTE says "Android" where the
// web says "a browser": what gets past a PIN differs by surface.
const val PICKER_NOTE =
    "Profiles keep your places and lists apart. A grown-up’s PIN keeps children out of it; " +
        "it is not a login, and someone who knows their way around Android can get past it."
const val FIRST_PROFILE = "Create the first profile — it runs this household"
const val WHO_RUNS_THIS = "Who runs this household?"
const val MANAGE_PROFILES = "Manage profiles"
const val WHO_ARE_YOU = "Who are you?"
const val GROWN_UPS = "Grown-ups"
const val KIDS_SECTION = "Kids"
const val YOUR_PIN = "Your PIN"
const val ADD_A_KID = "Add a kid"
const val ADD_A_GROWN_UP = "Add a grown-up"
const val CHANGE_YOUR_PIN = "Change your PIN"
const val RESET_PIN = "Reset PIN"
const val REMOVE = "Remove"
const val DONE = "Done"

/** Under Manage's heading once a grown-up has said who they are. */
fun managingAs(name: String): String = "As $name"

/** What the PIN prompt asks a grown-up for: its PIN, or — from before PINs — a first one. */
fun pinTitleFor(profile: Profile): String = if (profile.hasPin) "${profile.name}’s PIN" else "Choose a PIN for ${profile.name}"

/** A new profile's first PIN: the first profile's, or a grown-up the admin adds. */
fun newPinTitleFor(name: String): String = "A PIN for $name"

/** The admin choosing another grown-up's PIN. */
fun resetPinTitleFor(name: String): String = "A new PIN for $name"

internal const val YOUR_NEW_PIN = "Your new PIN"
internal const val PIN_SHAPE = "A PIN is four digits."
internal const val PINS_DIFFER = "The two PINs are not the same."
internal const val DID_NOT_GO_THROUGH = "That did not go through. Please try again."

/** Every change Manage sends carries the PIN it holds; a wrong one means it is no longer theirs. */
internal const val PIN_NO_LONGER_VALID = "Your PIN is no longer valid. Choose who you are again."

/** Why a request did not take, in the web's words; null once it did. */
fun ProfileOutcome.sentence(): String? =
    when (this) {
        ProfileOutcome.Done -> null
        ProfileOutcome.Invalid -> "That was not accepted. Check the name and the PIN."
        ProfileOutcome.NameTaken -> "A profile with that name already exists."
        ProfileOutcome.NotFound -> "That profile is not here any more."
        is ProfileOutcome.Wait -> "Too many wrong PINs. Try again in $seconds s."
        ProfileOutcome.NoPin -> "This profile has no PIN yet. Choose it again to set one."
        ProfileOutcome.WrongPin -> "Wrong PIN."
        // One wording for every refusal by the rule, as on the web: a manage
        // action outside the viewer's role, and a first profile when a
        // grown-up has arrived meanwhile.
        ProfileOutcome.NotAllowed -> "That is not allowed."
    }

/** The line under a kid's name on a tile or a row: its own limit, never a fixed one. */
val Profile.kidsTag: String? get() = if (kids) "Kids · FSK $kidsLimit" else null

/** Asked before a removal; a grown-up's kids go with it, so it says so. */
fun removeQuestion(profile: Profile): String =
    if (profile.kids) {
        "Remove ${profile.name} and everything they have watched?"
    } else {
        "Remove ${profile.name}, their kids, and everything they have watched?"
    }
