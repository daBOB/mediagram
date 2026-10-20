/**
 * This device's household: whose profiles it shows.
 *
 * In `localStorage`, beside the chosen profile and for the same reason: it is
 * the device's, not the library's. One install is shared by households, and
 * the television downstairs shows one of them while the laptop upstairs shows
 * another. Which one is `deviceHousehold`'s rule; this keeps its answer.
 */

import { deviceHousehold, HOME } from "./household-rules.js";

const KEY = "mediagram.household";

/**
 * The last answer, and the live households and `heard` it was worked out over.
 * @type {{ id: string|null, households: import("../../src/state/households.ts").Household[], heard: boolean }}
 */
let device = { id: null, households: [], heard: true };

/**
 * An answer the page's storage would not take — full, or refused — and that
 * storage: the page keeps the answer for as long as it reads the same one.
 */
let kept = { id: null, storage: undefined };

/** The page's storage, or `null` where even reaching it is refused. */
function storage() {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** Wrapped as the chosen profile is; the page's own copy first, as a full storage still holds the older one. */
function stored() {
  const local = storage();
  const mine = kept.storage === local ? kept.id : null;
  try {
    return mine ?? local.getItem(KEY);
  } catch {
    return mine;
  }
}

function store(id) {
  const local = storage();
  try {
    local.setItem(KEY, id);
    kept = { id: null, storage: undefined };
  } catch {
    kept = { id, storage: local };
  }
}

/** A profile's household; one that never said is home's, as `householdView` reads it. */
const placed = (one) => one.household ?? HOME;

/**
 * Works out this device's household over `everyone` and the live
 * `households`, keeps it, and answers the profiles in it — none while there
 * is no answer, which is the picker's cue to ask or to wait.
 * @param {string|null} chosenId the profile this device chose before, if any
 */
export function here(everyone, households, chosenId, heard) {
  // Home is never deleted, so it is always live, as `householdView` has it.
  const live = [...new Set([HOME, ...households.map((one) => one.id)])];
  const chosen = everyone.find((one) => one.id === chosenId);
  const id = deviceHousehold(stored(), chosen ? placed(chosen) : null, live, heard);
  if (id !== null && id !== stored()) store(id);
  device = { id, households, heard };
  return id === null ? [] : everyone.filter((one) => placed(one) === id);
}

/** The household this device shows, as `GET /api/profiles` lists it, or `null`. */
export const thisHousehold = () => device.households.find((one) => one.id === device.id) ?? null;

/** Whether the picker asks "Join a household": no answer, and the household has been heard. */
export const needsJoin = () => device.id === null && device.heard;

/**
 * This device now shows household `id`, from the next `here` on: until the
 * list has been read again there is nobody of that household to show.
 */
export const joined = store;
