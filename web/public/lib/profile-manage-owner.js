/**
 * The owner's view of Manage profiles: every household — this device's first,
 * then the others in the live order — with its line (its name, who runs it,
 * and a ⋯ with the owner's actions on it), then its people, each one row with
 * the line that says who they are, opening on a tap to what the owner may
 * change, then Add someone; and last, Create a household. The owner manages
 * every household from this one place.
 *
 * Drawing only: `mountManage` holds the owner's one PIN and hands in `act`,
 * which sends every change made here and says a refusal beside its form.
 */

import { el } from "./dom.js";
import * as api from "./profile-api.js";
import * as state from "./watch-state.js";
import { householdId, placed } from "./household.js";
import { householdAllowed } from "./household-rules.js";
import { roleLine } from "./profile-manage-households.js";
import { addSomeone } from "./profile-add-someone.js";
import { createHouseholdBlock, householdHeader } from "./catalog/household-rows.js";

/**
 * @param {{id: string}} owner
 * @param {{ draws: { you: Function, grownUp: Function, kid: Function }, person: Function,
 *   act: (said: HTMLElement, call: Function, opts?: object) => Promise<void>,
 *   keep: (name: string, node: HTMLElement) => HTMLElement, switchProfile: () => void }} tools
 *   what opens under each person's row and how a row is drawn, how a change is sent, and how a button
 *   is named to have focus again after a redraw
 * @returns {HTMLElement[]}
 */
export function ownerView(owner, { draws, person, act, keep, switchProfile }) {
  const everyone = state.everyone();
  const households = state.households();
  const live = households.map((one) => one.id);
  const may = (action, id, targetId = "") => householdAllowed(everyone, live, owner.id, action, id, targetId);
  const ctx = { everyone, households: state.households, may, act, switchProfile, keep };
  const { grownUps, kids } = api.manageable(everyone, owner.id);
  const reaches = (list, one) => list.some((other) => other.id === one.id);
  /** One of `people`'s rows, which opens what the owner may change of it, if anything. */
  const personRow = (one, people) => {
    const line = roleLine(one, people);
    if (one.id === owner.id) return person(one, line, () => draws.you(one));
    if (reaches(grownUps, one)) return person(one, line, () => draws.grownUp(one));
    return person(one, line, reaches(kids, one) ? () => draws.kid(one) : null);
  };
  // The server places everyone in a live household, a gone one reading as home, so every profile's is listed.
  const ids = [...new Set([householdId() ?? placed(owner), ...live])];
  const headed = ids.filter((id) => households.some((one) => one.id === id));
  const parts = ids.map((id) => {
    const people = everyone.filter((one) => placed(one) === id);
    const grown = people.filter((one) => !one.kids);
    const household = households.find((one) => one.id === id);
    const part = el("section", "manage-section manage-household");
    // A household the list doesn't name yet (a sync mid-way) keeps its people, unheaded, rather than breaking the page.
    if (household !== undefined) {
      const header = householdHeader(household, { ...ctx, next: headed[headed.indexOf(id) + 1] ?? null });
      part.setAttribute("aria-labelledby", header.children[0].id);
      part.append(header);
    }
    part.append(...[...grown, ...people.filter((one) => one.kids)].map((one) => personRow(one, people)),
      // A kid always belongs to a grown-up, so a household with none offers no kid.
      addSomeone({ grownUp: true, kid: grown.length > 0, parents: grown, household: id, householdName: household?.name, act, keep }));
    return part;
  });
  return [...parts, createHouseholdBlock(ctx)];
}
