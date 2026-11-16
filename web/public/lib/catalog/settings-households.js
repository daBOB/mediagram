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
import { unlock } from "../profile-api.js";
import { householdAllowed } from "../household-rules.js";
import { HOUSEHOLD_WORDS } from "../household-words.js";
import { pill } from "../profile-manage.js";
import { askPin, newPinTitle, promptsOver, refusalText } from "../pin-prompt.js";
import { createHouseholdBlock, householdHeader } from "./household-rows.js";

/** What a change reports once the part is gone: nothing was sent. */
const GONE = { ok: false, reason: "not-found" };

/**
 * @param {HTMLElement} promptHost PIN prompts open over this element
 * @param {{ profile: {id: string}|null, switchProfile: () => void }} chosen who is
 *   watching, if anyone; `switchProfile` asks again once this device moves household
 * @param {() => void} [onChange] runs after each change that went through, once the list is read again
 * @returns {{ box: HTMLElement, redraw: () => void, stop: () => void }} `redraw` follows a change made
 *   elsewhere on the page; `stop` removes the part and drops the PIN
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
   * name is the household's when `named()` says so, otherwise a new grown-up's.
   * A wrong PIN means the one held is no longer the owner's, so the next change asks again.
   */
  function report(said, outcome, named = () => false) {
    if (gone) return;
    if (outcome.ok) {
      draw();
      onChange();
      return;
    }
    if (outcome.reason === "wrong-pin") held = null;
    said.textContent = outcome.reason === "name-taken" && named() ? HOUSEHOLD_WORDS.householdNameTaken : refusalText(outcome);
  }

  /**
   * Runs `call(actorId, pin, newPin)` as the owner. `newPinFor` names a
   * grown-up about to run a household with no PIN yet: theirs is asked
   * twice first, and sent with it.
   */
  async function act(said, call, { newPinFor = null, named } = {}) {
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
    const ctx = { everyone, households: state.households, may, act, switchProfile };
    for (const household of households) body.append(householdHeader(household, ctx));
    body.append(createHouseholdBlock(ctx));
  }

  draw();
  return {
    box,
    redraw: () => { if (!gone) draw(); },
    stop: () => {
      gone = true;
      held = null;
      prompts.close();
      box.remove();
    },
  };
}
