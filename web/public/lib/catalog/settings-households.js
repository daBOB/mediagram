/**
 * Households, on the Profiles page wherever the owner's view is not showing:
 * a note that households are the owner's to set up, and an Owner button. On a
 * device that shows another household the owner is not among those to choose,
 * so this is the way in from any device: the button asks the owner's PIN and
 * hands it on, and the owner's view of every household opens with it held.
 */

import { el } from "../dom.js";
import * as state from "../watch-state.js";
import { unlock } from "../profile-api.js";
import { HOUSEHOLD_WORDS } from "../household-words.js";
import { pill } from "../profile-manage.js";
import { askPin, promptsOver } from "../pin-prompt.js";

/** What an unlock reports once the part is gone: nothing was sent. */
const GONE = { ok: false, reason: "not-found" };

/**
 * @param {HTMLElement} promptHost PIN prompts open over this element
 * @param {{ onOwner: (pin: string) => void }} on `onOwner` gets the owner's PIN once it is given
 * @returns {{ box: HTMLElement, stop: () => void }} `stop` removes the part and closes a prompt still up
 */
export function householdsPart(promptHost, { onOwner }) {
  const box = el("div", "setting households");
  box.append(el("h2", "setting-title", HOUSEHOLD_WORDS.households), el("p", "setting-value", HOUSEHOLD_WORDS.ownerNote));
  const prompts = promptsOver(promptHost);
  let gone = false;
  const owner = state.everyone().find((one) => one.owner === true && !one.kids);
  // An install from before the owner has nobody to say they are.
  if (owner !== undefined) {
    box.append(pill(HOUSEHOLD_WORDS.owner, async () => {
      const pin = await prompts.ask(() => askPin(promptHost, { title: HOUSEHOLD_WORDS.ownersPin,
        send: (given) => (gone ? Promise.resolve(GONE) : unlock(owner.id, given)) }));
      if (pin !== null && !gone) onOwner(pin);
    }));
  }
  return {
    box,
    stop: () => {
      gone = true;
      prompts.close();
      box.remove();
    },
  };
}
