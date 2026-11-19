/**
 * Manage profiles' parts: a button, where a refusal is said, one line of an
 * open part, a quiet button that opens a form in place, the line that says who
 * a profile is — and the calm list of anyone but the owner, who manages only
 * their own household, unnamed. The owner's view of every household is
 * `profile-manage-owner.js`.
 */

import { el } from "./dom.js";
import { ownerOf } from "./profile-api.js";
import { kidsLimitOf } from "./age-rating.js";
import { dailyLimitWords } from "./daily-limit.js";
import { hidesWords } from "./section-access.js";
import { HOUSEHOLD_WORDS } from "./household-words.js";

/** A quiet button saying `label`. */
export function pill(label, onClick) {
  const button = el("button", "pill pill-line", label);
  button.type = "button";
  button.addEventListener("click", onClick);
  return button;
}

/** Where a refusal is said, beside what was refused, read out as it changes. */
export function message() {
  const said = el("p", "pin-message");
  said.setAttribute("role", "alert");
  return said;
}

/** One line of an open part: what it sets, for the eye — each control names itself — then the control. */
export function line(label, control) {
  const one = el("div", "manage-line");
  const said = el("span", "manage-label", label);
  said.setAttribute("aria-hidden", "true");
  one.append(said, control);
  return one;
}

/** The first field under `node`, which a form opened in place has focus on. */
export const firstField = (node) =>
  ["INPUT", "SELECT"].includes(node.tagName) ? node : [...node.children].map(firstField).find(Boolean);

/** Tells apart the parts opened in place, which their buttons name. */
let opened = 0;

/**
 * A quiet button saying `label` that opens what `build` draws under it, in
 * place, with focus on its first field — and closes it again. `keep` hears of
 * the button, to give it focus again after a redraw; `name`, when given, is
 * the button's name where `label` alone would not tell it from its like.
 */
export function opener(label, build, keep = (node) => node, name = null) {
  const box = el("div", "manage-opener");
  const button = keep(el("button", "manage-add", label));
  button.type = "button";
  if (name) button.setAttribute("aria-label", name);
  const slot = el("div", "manage-opened");
  slot.id = `manage-opened-${++opened}`;
  button.setAttribute("aria-controls", slot.id);
  const show = (on) => {
    slot.replaceChildren(...(on ? build() : []));
    slot.hidden = !on;
    button.setAttribute("aria-expanded", String(on));
  };
  button.addEventListener("click", () => {
    show(slot.hidden);
    if (!slot.hidden) firstField(slot)?.focus();
  });
  show(false);
  box.append(button, slot);
  return box;
}

/**
 * One quiet line for a profile: its role, for a grown-up whether it has a PIN,
 * and the sections it is not shown. `profiles` are its household's, whose admin
 * a kid with no parent here belongs to.
 */
export function roleLine(entry, profiles) {
  if (entry.kids) {
    const owner = profiles.find((other) => other.id === ownerOf(profiles, entry));
    const parts = [HOUSEHOLD_WORDS.kid, owner?.name, `FSK ${kidsLimitOf(entry)}`];
    if (entry.dailyLimit != null) parts.push(dailyLimitWords(entry.dailyLimit));
    return [...parts, hidesWords(entry)].filter(Boolean).join(" · ");
  }
  const role = `${entry.owner ? `${HOUSEHOLD_WORDS.owner} · ` : ""}${entry.admin ? HOUSEHOLD_WORDS.admin : HOUSEHOLD_WORDS.grownUp} · ${entry.hasPin ? "PIN set" : "No PIN"}`;
  return [role, hidesWords(entry)].filter(Boolean).join(" · ");
}

/**
 * The household of anyone but the owner, as the owner's view draws one but
 * unnamed: themselves, then the grown-ups and the kids they manage, then `add`.
 * @param {{ actor: object, grownUps: object[], kids: object[] }} view `manageable`'s
 * @param {object[]} profiles the household's, which the role lines are read against
 * @param {{ person: Function, draws: { you: Function, grownUp: Function, kid: Function }, add: HTMLElement }} tools
 *   how a person's row is drawn, what opens under it, and Add someone
 */
export function byHousehold({ actor, grownUps, kids }, profiles, { person, draws, add }) {
  const part = el("div", "manage-people");
  const row = (one, controls) => person(one, roleLine(one, profiles), controls);
  part.append(row(actor, () => draws.you(actor)), ...grownUps.map((one) => row(one, () => draws.grownUp(one))),
    ...kids.map((one) => row(one, () => draws.kid(one))), add);
  return part;
}
