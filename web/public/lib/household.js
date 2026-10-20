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

/** Wrapped as the chosen profile is: with storage disabled, the answer lasts this page load. */
function stored() {
  try {
    return window.localStorage.getItem(KEY);
  } catch {
    return device.id;
  }
}

function store(id) {
  try {
    window.localStorage.setItem(KEY, id);
  } catch {
    // Kept in `device` instead, which still works until the page goes.
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

/** This device now shows household `id`; the next `here` finds it stored. */
export function joined(id) {
  store(id);
  device = { ...device, id };
}
