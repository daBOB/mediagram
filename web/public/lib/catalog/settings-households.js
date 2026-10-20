/**
 * Households, on the Profiles page: the owner's part. Every live household,
 * who runs it and how many profiles are in it, and the owner's changes — a
 * new household, a new name or admin, a grown-up moved in, this device moved
 * to one, an empty one deleted. Anyone else is told these are the owner's,
 * and may say they are the owner.
 *
 * The owner's PIN is asked once — at the Owner button, or the owner's first
 * change — held while the part is mounted, and dropped when it is torn down.
 * What a row offers follows the household rule over everyone on the install;
 * the server decides all the same, and a refusal is said beside what was refused.
 */

import { el } from "../dom.js";
import * as state from "../watch-state.js";
import * as api from "../household-api.js";
import { unlock } from "../profile-api.js";
import { thisHousehold } from "../household.js";
import { householdAllowed } from "../household-rules.js";
import { HOUSEHOLD_WORDS, householdLine } from "../household-words.js";
import { pill } from "../profile-manage.js";
import { askPin, newPinTitle, promptsOver, refusalText } from "../pin-prompt.js";
import { createForm, personForm, renameForm } from "./household-forms.js";

/** What a change reports once the part is gone: nothing was sent. */
const GONE = { ok: false, reason: "not-found" };

/** A name as the server compares household names: case, spacing and Unicode form aside. */
const key = (name) => name.normalize("NFC").trim().replace(/\s+/g, " ").toLowerCase();

/** Where a refusal is said, read out as it changes. */
function message() {
  const said = el("p", "pin-message");
  said.setAttribute("role", "alert");
  return said;
}

/**
 * @param {HTMLElement} promptHost PIN prompts open over this element
 * @param {{ profile: {id: string}|null, switchProfile: () => void }} chosen who is
 *   watching, if anyone; `switchProfile` asks again once this device moves household
 * @param {() => void} [onChange] runs after each change that went through, once the list is read again
 * @returns {{ box: HTMLElement, stop: () => void }} `stop` removes the part and drops the PIN
 */
export function householdsPart(promptHost, { profile, switchProfile }, onChange = () => {}) {
  const box = el("div", "setting households");
  const body = el("div");
  box.append(el("h2", "setting-title", HOUSEHOLD_WORDS.households), body);
  const prompts = promptsOver(promptHost);
  const owner = () => state.everyone().find((one) => one.owner === true && !one.kids);
  /** The owner's PIN once given; the part opens at once only for the owner. */
  let held = null;
  let open = profile != null && profile.id === owner()?.id;
  let gone = false;

  /** The PIN held, or the owner's asked now; `null` when none was given, or the part is gone. */
  async function ownersPin() {
    const boss = owner();
    if (held === null && boss !== undefined) {
      const given = await prompts.ask(() => askPin(promptHost, { title: HOUSEHOLD_WORDS.ownersPin,
        send: (pin) => (gone ? Promise.resolve(GONE) : unlock(boss.id, pin)) }));
      if (!gone) held = given;
    }
    return gone ? null : held;
  }

  /**
   * Says how a change went in `said`: one that went through redraws. A taken
   * name is the household's when `named`, otherwise a new grown-up's. A wrong
   * PIN means the one held is no longer the owner's, so the next change asks again.
   */
  function report(said, outcome, named = false) {
    if (gone) return;
    if (outcome.ok) {
      draw();
      onChange();
      return;
    }
    if (outcome.reason === "wrong-pin") held = null;
    said.textContent = named && outcome.reason === "name-taken" ? HOUSEHOLD_WORDS.householdNameTaken : refusalText(outcome);
  }

  /**
   * Runs `call(actorId, pin, newPin)` as the owner. `newPinFor` names a
   * grown-up about to run a household with no PIN yet: theirs is asked
   * twice first, and sent with it.
   */
  async function act(said, call, { newPinFor = null, named = false } = {}) {
    if (gone) return;
    const pin = await ownersPin();
    if (pin === null) return;
    const actor = owner().id;
    const send = (next) => (gone ? Promise.resolve(GONE) : call(actor, pin, next));
    if (newPinFor === null) return report(said, await send(), named);
    const given = await prompts.ask(() => askPin(promptHost, { title: newPinTitle(newPinFor), confirm: true, send,
      refused: (outcome) => report(said, outcome, named) }));
    if (given !== null) report(said, { ok: true });
  }

  function draw() {
    body.textContent = "";
    const boss = owner();
    if (!open || boss === undefined) {
      body.append(el("p", "setting-value", HOUSEHOLD_WORDS.ownerNote));
      if (boss !== undefined) {
        body.append(pill(HOUSEHOLD_WORDS.owner, async () => {
          if ((await ownersPin()) === null) return;
          open = true;
          draw();
        }));
      }
      return;
    }
    const everyone = state.everyone();
    const households = state.households();
    const live = households.map((one) => one.id);
    const may = (action, householdId, targetId = "") => householdAllowed(everyone, live, boss.id, action, householdId, targetId);
    const grownUps = everyone.filter((one) => !one.kids);
    for (const household of households) body.append(row(household, everyone, grownUps, may));

    const create = el("div", "household-create");
    const said = message();
    create.append(createForm(grownUps.filter((one) => may("create-household", "", one.id)), (name, admin) => {
      const fresh = admin.id === undefined;
      return act(said, (actor, pin, newPin) => api.create(actor, pin, name, fresh ? { adminName: admin.name, newPin } : { adminId: admin.id, newPin }), {
        newPinFor: fresh || !admin.hasPin ? admin.name : null,
        // Both names can be taken: a household's is said first, as the server checks it first.
        named: !fresh || households.some((one) => key(one.name) === key(name)),
      });
    }), said);
    body.append(create);
  }

  /** One household: its name and line, what the owner may do to it, and where a form opens. */
  function row(household, everyone, grownUps, may) {
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
        act(said, (actor, pin) => api.rename(actor, pin, id, name), { named: true }))));
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

  draw();
  return {
    box,
    stop: () => {
      gone = true;
      held = null;
      prompts.close();
      box.remove();
    },
  };
}
