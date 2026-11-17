/**
 * The owner's view of Manage profiles: every household — this device's first,
 * then the others in the live order — with its header (its name, who runs it,
 * the owner's actions on it), then its people, each with the line that says
 * who they are and what the owner may change, then adding a grown-up or a kid
 * right there; and last, creating a household. The owner manages every
 * household from this one place.
 *
 * Drawing only: `mountManage` holds the owner's one PIN and hands in `act`,
 * which sends every change made here and says a refusal beside its form.
 */

import { el } from "./dom.js";
import * as api from "./profile-api.js";
import * as state from "./watch-state.js";
import { householdId, placed } from "./household.js";
import { householdAllowed } from "./household-rules.js";
import { HOUSEHOLD_WORDS } from "./household-words.js";
import { DAILY_LIMIT_WORDS } from "./daily-limit.js";
import { dailyChoice, minutesOf } from "./daily-limit-choice.js";
import { addForm, LIMITS, limitChoice } from "./profile-add-form.js";
import { row, roleLine } from "./profile-manage-households.js";
import { createHouseholdBlock, householdHeader, message } from "./catalog/household-rows.js";
import { personChoice } from "./catalog/household-forms.js";

/** Adding a grown-up into household `id`: their name, then — after the owner's PIN — theirs, twice. */
function addGrownUp(id, act) {
  const said = message();
  const form = addForm("Add a grown-up", [], (name) =>
    act(said, (actor, pin, newPin) => api.create(actor, pin, { name, kids: false, newPin, household: id }), { newPinFor: name }));
  return [form, said];
}

/**
 * Adding a kid for one of a household's `grownUps`, where the kid then lives:
 * its admin's, else the first's, unless another is chosen under Belongs to,
 * which is offered only when there is more than one.
 */
function addKid(grownUps, act) {
  const said = message();
  const age = limitChoice(LIMITS[0], "Age limit for the new kid");
  const daily = dailyChoice(null, `${DAILY_LIMIT_WORDS.label} for the new kid`);
  const parent = personChoice(HOUSEHOLD_WORDS.belongsTo, grownUps);
  parent.value = (grownUps.find((one) => one.admin) ?? grownUps[0]).id;
  const belongs = el("label", "household-admin", HOUSEHOLD_WORDS.belongsTo);
  belongs.append(parent);
  const form = addForm("Add a kid", [age, daily, ...(grownUps.length > 1 ? [belongs] : [])], (name) =>
    act(said, (actor, pin) => api.create(actor, pin,
      { name, kids: true, kidsAge: Number(age.value), dailyLimit: minutesOf(daily), parentId: parent.value })));
  return [form, said];
}

/**
 * @param {{id: string}} owner
 * @param {{ draws: { you: Function, grownUp: Function, kid: Function },
 *   act: (said: HTMLElement, call: Function, opts?: object) => Promise<void>, switchProfile: () => void }} tools
 *   the rows `mountManage` draws, each with the controls it offers, and how a change is sent
 * @returns {HTMLElement[]}
 */
export function ownerView(owner, { draws, act, switchProfile }) {
  const everyone = state.everyone();
  const households = state.households();
  const live = households.map((one) => one.id);
  const may = (action, id, targetId = "") => householdAllowed(everyone, live, owner.id, action, id, targetId);
  const ctx = { everyone, households: state.households, may, act, switchProfile };
  const { grownUps, kids } = api.manageable(everyone, owner.id);
  const reaches = (list, one) => list.some((other) => other.id === one.id);
  /** One of `people`'s rows: what the owner may change of it, beside the line that says who it is. */
  const personRow = (one, people) => {
    const line = roleLine(one, people);
    if (one.id === owner.id) return draws.you(one, line);
    if (reaches(grownUps, one)) return draws.grownUp(one, line);
    return reaches(kids, one) ? draws.kid(one, line) : row(one.name, line);
  };
  // The server places everyone in a live household, a gone one reading as home, so every profile's is listed.
  const ids = [...new Set([householdId() ?? placed(owner), ...live])];
  const parts = ids.map((id) => {
    const people = everyone.filter((one) => placed(one) === id);
    const grown = people.filter((one) => !one.kids);
    const household = households.find((one) => one.id === id);
    const part = el("section", "manage-section manage-household");
    // A household the list doesn't name yet (a sync mid-way) keeps its people, unheaded, rather than breaking the page.
    if (household !== undefined) {
      const header = householdHeader(household, ctx);
      part.setAttribute("aria-labelledby", header.children[0].id);
      part.append(header);
    }
    part.append(...[...grown, ...people.filter((one) => one.kids)].map((one) => personRow(one, people)),
      // A kid always belongs to a grown-up, so a household with none offers no kid.
      ...addGrownUp(id, act), ...(grown.length > 0 ? addKid(grown, act) : []));
    return part;
  });
  return [...parts, createHouseholdBlock(ctx)];
}
