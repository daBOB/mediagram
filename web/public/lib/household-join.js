/**
 * Join a household: which household's profiles this device shows, chosen
 * with its admin's PIN or the owner's. The picker draws it for a device that
 * has none yet; the Profiles page draws it to switch to another.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import { join } from "./household-api.js";
import { HOUSEHOLD_WORDS, joinPinTitle } from "./household-words.js";
import { pill } from "./profile-manage.js";
import { askPin } from "./pin-prompt.js";

/**
 * The live households but `except`, one button each. A choice asks the PIN —
 * a refusal is said in the prompt, which stays — and once one joins, `onJoined`.
 * @param {HTMLElement} host PIN prompts open over this element
 * @param {{ except?: string, onJoined: () => void }} options
 */
export function joinChoices(host, { except, onJoined }) {
  const box = el("div", "who-ask");
  box.append(el("h2", null, HOUSEHOLD_WORDS.joinAHousehold), el("p", "who-note", HOUSEHOLD_WORDS.joinNote));
  for (const household of state.households().filter((one) => one.id !== except)) {
    const admin = state.everyone().find((one) => one.id === household.adminId);
    box.append(pill(household.name, async () => {
      const pin = await askPin(host, { title: joinPinTitle(admin), send: (given) => join(household.id, given) });
      if (pin !== null) onJoined();
    }));
  }
  return box;
}
