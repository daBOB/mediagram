/**
 * Who's managing?: Manage profiles' one question before anyone manages, drawn
 * with Who's watching?'s own tiles. The grown-ups of this device's household,
 * the owner first and labelled Owner; on a device showing another household,
 * where the owner is not one of them, the owner's tile ends the row, the way
 * in to every household from any device. Kids manage nothing and have no tile.
 *
 * A tile asks that person's PIN — the owner's for the owner — and hands it on.
 * A wrong one is said in the prompt over the tiles, and nothing opens.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import * as api from "./profile-api.js";
import { HOUSEHOLD_WORDS } from "./household-words.js";
import { profileTiles } from "./profile-tiles.js";
import { askGrownUp, askPin } from "./pin-prompt.js";

/** What a PIN check reports once the body is gone: nothing was sent. */
const GONE = { ok: false, reason: "not-found" };

/**
 * @param {{ promptHost: HTMLElement, ask: (open: () => Promise<string|null>) => Promise<string|null>,
 *   gone: () => boolean, chosen: (entry: {id: string}, pin: string) => void,
 *   refused: (outcome: object) => void }} tools prompts open over `promptHost` through `ask`;
 *   `chosen` gets whoever proved who they are, with their PIN; `refused`, a first PIN refused
 * @returns {HTMLElement}
 */
export function whoIsManaging({ promptHost, ask, gone, chosen, refused }) {
  const owner = state.everyone().find((one) => one.owner === true && !one.kids);
  const isOwner = (one) => owner !== undefined && one.id === owner.id;
  const here = state.profiles().filter((one) => !one.kids);
  const others = here.filter((one) => !isOwner(one));
  const people = owner === undefined ? here : here.some(isOwner) ? [owner, ...others] : [...others, owner];

  async function choose(entry) {
    const pin = await ask(() => isOwner(entry)
      ? askPin(promptHost, { title: HOUSEHOLD_WORDS.ownersPin, send: (given) => (gone() ? Promise.resolve(GONE) : api.unlock(entry.id, given)) })
      : askGrownUp(promptHost, entry, (given) => (gone() ? Promise.resolve(GONE) : api.prove(entry, given)), refused));
    if (pin !== null && !gone()) chosen(entry, pin);
  }

  const part = el("section", "manage-who");
  part.append(el("h1", null, HOUSEHOLD_WORDS.whosManaging), profileTiles(people, { onChoose: (entry) => void choose(entry),
    extra: (entry) => (isOwner(entry) ? el("span", "who-owner", HOUSEHOLD_WORDS.owner) : null) }));
  return part;
}
