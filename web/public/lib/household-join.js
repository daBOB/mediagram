/**
 * Join a household: which household's profiles this device shows, chosen
 * with its admin's PIN or the owner's. The picker draws it for a device that
 * has none yet; the Profiles page draws it to switch to another.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import { join } from "./household-api.js";
import { thisHousehold } from "./household.js";
import { HOUSEHOLD_WORDS, joinPinTitle } from "./household-words.js";
import { pill } from "./profile-manage.js";
import { askPin } from "./pin-prompt.js";

/**
 * The live households but `except` — this device's own, unless said — one
 * button each, or a line saying there is none. A choice asks the PIN — a
 * refusal is said in the prompt, which stays — and once one joins, `onJoined`.
 * @param {HTMLElement} host PIN prompts open over this element
 * @param {{ except?: string, onJoined: () => void }} options
 */
export function joinChoices(host, { except = thisHousehold()?.id, onJoined }) {
  const box = el("div", "who-ask");
  box.append(el("h2", null, HOUSEHOLD_WORDS.joinAHousehold), el("p", "who-note", HOUSEHOLD_WORDS.joinNote));
  const owner = state.everyone().find((one) => one.owner);
  const adminOf = (household) => state.everyone().find((one) => one.id === household.adminId);
  // Neither its admin nor the owner has a PIN to let this device in with, so the server would refuse any.
  const offered = state.households().filter((one) => one.id !== except && (adminOf(one)?.hasPin || owner?.hasPin));
  for (const household of offered) {
    box.append(pill(household.name, async () => {
      const pin = await askPin(host, { title: joinPinTitle(adminOf(household)), send: (given) => join(household.id, given) });
      if (pin !== null) onJoined();
    }));
  }
  if (offered.length === 0) box.append(el("p", "who-note", HOUSEHOLD_WORDS.noneToJoin));
  return box;
}

/**
 * For a device whose household has no grown-up left: the others to join,
 * while a grown-up is anywhere on the install — which also means no first
 * profile can be made — or `null`.
 */
export const joinElsewhere = (host, onJoined) => (state.everyone().some((one) => !one.kids) ? joinChoices(host, { onJoined }) : null);
