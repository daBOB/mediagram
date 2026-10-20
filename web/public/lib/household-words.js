/**
 * The words of households, written once for the page and the server; the
 * Android app's `HouseholdWords.kt` is held to the same `household-rules.json`,
 * so both surfaces say the same thing.
 */

export const HOUSEHOLD_WORDS = {
  households: "Households",
  owner: "Owner",
  ownerNote: "Households are the owner’s to set up.",
  ownersPin: "The owner’s PIN",
  thisDevice: "This device",
  switchHousehold: "Switch household",
  joinAHousehold: "Join a household",
  joinNote: "This device shows one household’s profiles. Choose yours, then its admin’s PIN.",
  noneToJoin: "No household can be joined from here.",
  createAHousehold: "Create a household",
  householdName: "Household name",
  admin: "Admin",
  aNewGrownUp: "A new grown-up",
  newGrownUpName: "The new grown-up’s name",
  rename: "Rename",
  changeAdmin: "Change admin",
  moveHere: "Move a grown-up here",
  useHere: "Use on this device",
  delete: "Delete",
  householdNameTaken: "A household with that name already exists.",
  profileNameTaken: "That name is already used on this install.",
};

/** A household's line in the owner's list: who runs it, and how many profiles are in it. */
export const householdLine = (adminName, count) =>
  `${adminName == null ? "No admin" : `Admin ${adminName}`} · ${count} profile${count === 1 ? "" : "s"}`;

/**
 * What joining asks for, given the household's admin (`{name, owner}`, or
 * none): that admin's PIN, or the owner's, which opens every household. Where
 * the owner runs it, the owner's PIN is the only one, said once.
 */
export const joinPinTitle = (admin) =>
  admin == null || admin.owner === true ? HOUSEHOLD_WORDS.ownersPin : `${admin.name}’s PIN, or the owner’s`;
