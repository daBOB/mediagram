/**
 * One household's row and the create-a-household form, drawn from a context
 * the caller builds: the owner's view of every household (`profile-manage-owner.js`).
 */

import { el } from "../dom.js";
import * as api from "../household-api.js";
import { thisHousehold } from "../household.js";
import { HOUSEHOLD_WORDS, householdLine } from "../household-words.js";
import { pill } from "../profile-manage-households.js";
import { createForm, personForm, renameForm } from "./household-forms.js";

/**
 * What the rows draw from and act through.
 * @typedef {object} HouseholdCtx
 * @property {Array<{id: string, name: string, hasPin?: boolean, kids?: boolean, household?: string}>} everyone every profile on the install
 * @property {() => Array<{id: string, name: string, adminId: string|null}>} households every live household, read when needed
 * @property {(action: string, householdId: string, targetId?: string) => boolean} may what the household rule lets the owner do
 * @property {(said: HTMLElement, call: Function, opts?: object) => Promise<void>} act runs `call` as the owner, saying the outcome in `said`
 * @property {() => void} switchProfile asks who is watching again, once this device moves household
 */

/** A name as the server compares household names: case, spacing and Unicode form aside. */
const key = (name) => name.normalize("NFC").trim().replace(/\s+/g, " ").toLowerCase();

/** Where a refusal is said, beside what was refused, read out as it changes. */
export function message() {
  const said = el("p", "pin-message");
  said.setAttribute("role", "alert");
  return said;
}

/**
 * One household: its name and line, what the owner may do to it, and where a form opens.
 * @param {{id: string, name: string, adminId: string|null}} household
 * @param {HouseholdCtx} ctx
 */
export function householdHeader(household, { everyone, may, act, switchProfile }) {
  const grownUps = everyone.filter((one) => !one.kids);
  const line = el("div", "manage-row household-row");
  const admin = everyone.find((one) => one.id === household.adminId);
  const count = everyone.filter((one) => one.household === household.id).length;
  const actions = el("div", "household-actions");
  const slot = el("div", "household-form");
  const said = message();
  /** A button that opens `build`'s form under the row, and closes it again. */
  const opens = (label, build) => pill(label, () => {
    const again = slot.dataset.open === label;
    slot.replaceChildren(...(again ? [] : [build()]));
    slot.dataset.open = again ? "" : label;
    said.textContent = "";
  });
  const id = household.id;

  if (may("rename-household", id)) {
    actions.append(opens(HOUSEHOLD_WORDS.rename, () => renameForm(household.name, (name) =>
      act(said, (actor, pin) => api.rename(actor, pin, id, name), { named: () => true }))));
  }
  const admins = grownUps.filter((one) => one.id !== household.adminId && may("set-household-admin", id, one.id));
  if (admins.length > 0) {
    actions.append(opens(HOUSEHOLD_WORDS.changeAdmin, () => personForm(HOUSEHOLD_WORDS.changeAdmin, admins, (one) =>
      act(said, (actor, pin, next) => api.setAdmin(actor, pin, id, one.id, next), { newPinFor: one.hasPin ? null : one.name }))));
  }
  const movers = grownUps.filter((one) => may("move-to-household", id, one.id));
  if (movers.length > 0) {
    // Into a household nobody runs, the one moved runs it, so needs a PIN.
    actions.append(opens(HOUSEHOLD_WORDS.moveHere, () => personForm(HOUSEHOLD_WORDS.moveHere, movers, (one) =>
      act(said, (actor, pin, next) => api.move(actor, pin, id, one.id, next),
        { newPinFor: household.adminId === null && !one.hasPin ? one.name : null }))));
  }
  if (id !== thisHousehold()?.id && may("join-household", id)) {
    actions.append(pill(HOUSEHOLD_WORDS.useHere, () => void act(said, async (_actor, pin) => {
      const outcome = await api.join(id, pin);
      // This device leaves the profile chosen here behind, so who is watching is asked again.
      if (outcome.ok) switchProfile();
      return outcome;
    })));
  }
  if (may("delete-household", id)) {
    actions.append(pill(HOUSEHOLD_WORDS.delete, () => void act(said, (actor, pin) => api.remove(actor, pin, id))));
  }
  line.append(el("span", null, household.name), el("span", "setting-value", householdLine(admin?.name ?? null, count)), actions, slot, said);
  return line;
}

/**
 * The create-a-household form, with where its refusal is said.
 * @param {HouseholdCtx} ctx
 */
export function createHouseholdBlock({ everyone, households, may, act }) {
  const create = el("div", "household-create");
  const said = message();
  const grownUps = everyone.filter((one) => !one.kids);
  create.append(createForm(grownUps.filter((one) => may("create-household", "", one.id)), (name, admin) => {
    const fresh = admin.id === undefined;
    return act(said, (actor, pin, newPin) => api.create(actor, pin, name, fresh ? { adminName: admin.name, newPin } : { adminId: admin.id, newPin }), {
      newPinFor: fresh || !admin.hasPin ? admin.name : null,
      // Both names can be taken: a household's is said first, as the server checks it first.
      named: () => !fresh || households().some((one) => key(one.name) === key(name)),
    });
  }), said);
  return create;
}
