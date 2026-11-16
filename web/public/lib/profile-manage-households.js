/**
 * Manage profiles' parts: a button, a section, a profile's row, the line that says who
 * a profile is — which Everyone here and the owner's view both show — and the
 * layout of anyone but the owner, who manages only their own household, drawn
 * as it always was: Grown-ups for its admin, then Kids. The owner's view of
 * every household is `profile-manage-owner.js`.
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

/** A part of Manage headed `title`, at `level`, holding `nodes`. */
export function section(title, nodes, level = "h2") {
  const box = el("section", "manage-section");
  box.append(el(level, null, title), ...nodes);
  return box;
}

/** A profile's row: its name, the line that says who it is when given, then `controls`. */
export function row(name, line, ...controls) {
  const one = el("div", "manage-row");
  one.append(el("span", null, name), ...(line ? [el("span", "setting-value", line)] : []), ...controls);
  return one;
}

/**
 * One quiet line for a profile: its role, for a grown-up whether it has a PIN,
 * and the sections it is not shown. `profiles` are its household's, whose admin
 * a kid with no parent here belongs to.
 */
export function roleLine(entry, profiles) {
  if (entry.kids) {
    const owner = profiles.find((other) => other.id === ownerOf(profiles, entry));
    const parts = ["Kid", owner?.name, `FSK ${kidsLimitOf(entry)}`];
    if (entry.dailyLimit != null) parts.push(dailyLimitWords(entry.dailyLimit));
    return [...parts, hidesWords(entry)].filter(Boolean).join(" · ");
  }
  const role = `${entry.owner ? `${HOUSEHOLD_WORDS.owner} · ` : ""}${entry.admin ? "Admin" : "Grown-up"} · ${entry.hasPin ? "PIN set" : "No PIN"}`;
  return [role, hidesWords(entry)].filter(Boolean).join(" · ");
}

/**
 * The parts `view` (`manageable`'s, for anyone but the owner) is drawn in.
 * @param {{ actor: object, grownUps: object[], kids: object[] }} view
 * @param {{ grownUp: (entry: object) => HTMLElement, kid: (entry: object) => HTMLElement,
 *   addGrownUp: () => HTMLElement, addKid: () => HTMLElement }} draw a grown-up's row, a kid's, and the forms that add one
 * @returns {HTMLElement[]}
 */
export function byHousehold({ actor, grownUps, kids }, draw) {
  const parts = [];
  if (grownUps.length > 0 || actor.admin) {
    parts.push(section("Grown-ups", [...grownUps.map((one) => draw.grownUp(one)), ...(actor.admin ? [draw.addGrownUp()] : [])]));
  }
  parts.push(section("Kids", [...kids.map((one) => draw.kid(one)), draw.addKid()]));
  return parts;
}
