/**
 * Households: which one each profile is in, who runs each, what the owner and
 * a household's admin may change, and which household this device shows.
 *
 * Pure, and the one place the rule is written: browser JS the server imports
 * too, as it does `report-text.js`, while the Android core and app hold their
 * ports to the same `household-rules.json`, as `daily-limit.js` is held to
 * its fixture. It is a household rule, not real separation: it decides what
 * is shown and offered, and refuses no request the server would otherwise take.
 */

/** The first household. The same id on every device, so each can migrate alone; never deleted. */
export const HOME = "home";

/**
 * Each profile's household and whether it runs it, and each live household's
 * admin. A household that is gone or unknown reads as home, so nobody is ever
 * in none.
 */
export function householdView(profiles, households) {
  const live = new Set([HOME, ...households.filter((one) => !one.removed).map((one) => one.id)]);
  const byId = new Map(profiles.map((one) => [one.id, one]));
  const stored = (one) => (live.has(one.household ?? HOME) ? (one.household ?? HOME) : HOME);
  // A kid is wherever its parent is, whatever its own field says: moving a
  // grown-up moves their kids, and a kid's stale field cannot split them.
  const householdOf = (one) => {
    const parent = one.kids ? byId.get(one.parentId) : undefined;
    return stored(parent !== undefined && !parent.kids ? parent : one);
  };
  const owner = profiles.find((one) => one.owner === true && !one.kids);

  // The owner always runs the household they are in, so no household admin
  // can reset the owner's PIN. Elsewhere the record names the admin, who
  // counts only while a grown-up of that household.
  const adminOf = (id) => {
    if (owner !== undefined && householdOf(owner) === id) return owner.id;
    const named = byId.get(households.find((one) => one.id === id)?.adminId);
    return named !== undefined && !named.kids && householdOf(named) === id ? named.id : null;
  };

  const admins = Object.fromEntries([...live].map((id) => [id, adminOf(id)]));
  const view = Object.fromEntries(
    profiles.map((one) => {
      const household = householdOf(one);
      return [one.id, { household, admin: !one.kids && admins[household] === one.id }];
    }),
  );
  return { profiles: view, admins };
}

/**
 * Whether `actorId` may do `action` to household `householdId` (and profile
 * `targetId`, where the action has one), over profiles `householdView` has
 * already placed. Never throws.
 */
export function householdAllowed(profiles, live, actorId, action, householdId, targetId) {
  const actor = profiles.find((one) => one.id === actorId);
  if (actor === undefined || actor.kids) return false;
  const owner = actor.owner === true;
  const target = profiles.find((one) => one.id === targetId && !one.kids);

  // The admin given with a new household is a new grown-up, or one who runs nothing yet.
  if (action === "create-household") return owner && (targetId === "" || (target !== undefined && !target.admin));
  if (!live.includes(householdId)) return false;

  switch (action) {
    case "rename-household":
      return owner;
    case "set-household-admin":
      // The owner runs their own household, so there is no admin there to change.
      return owner && actor.household !== householdId && target?.household === householdId;
    case "move-to-household":
      // A household's admin leaves only as its last grown-up, which empties it
      // for deletion; while others remain, the owner changes the admin first.
      return (
        owner &&
        target !== undefined &&
        target.household !== householdId &&
        (!target.admin ||
          !profiles.some((one) => !one.kids && one.id !== target.id && one.household === target.household))
      );
    case "delete-household":
      return owner && householdId !== HOME && !profiles.some((one) => one.household === householdId);
    case "join-household":
      return owner || (actor.admin && actor.household === householdId);
    case "claim-household":
      // Only a race or an older build leaves a household with grown-ups and no admin.
      return (
        actor.household === householdId &&
        !profiles.some((one) => !one.kids && one.admin && one.household === householdId)
      );
    default:
      return false;
  }
}

/**
 * Which household this device shows: the one it joined, else its chosen
 * profile's, so a device from before households stays where its profile is;
 * else, once a sync round has been heard, the only one there is. `null` means
 * ask "Join a household" — or, before anything is heard, wait.
 */
export function deviceHousehold(stored, chosen, live, heard) {
  if (live.includes(stored)) return stored;
  if (live.includes(chosen)) return chosen;
  if (!heard) return null;
  return live.length === 1 ? live[0] : null;
}
