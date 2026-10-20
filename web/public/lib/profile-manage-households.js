/**
 * Manage profiles, by household. Anyone but the owner manages only their own
 * household, drawn as it always was: Grown-ups for its admin, then Kids. The
 * owner manages everyone on the install (`manageable`), so while there is
 * more than one household each is drawn under its name: theirs first — this
 * device's, as Who are you? offers only its grown-ups — then the others in
 * the live order, and one with nobody in it to manage not at all. Adding a
 * grown-up or a kid stays with theirs; the Households part places people elsewhere.
 */

import { el } from "./dom.js";
import { placed } from "./household.js";
import { households } from "./watch-state.js";

/** A part of Manage headed `title`, at `level`, holding `nodes`. */
export function section(title, nodes, level = "h2") {
  const box = el("section", "manage-section");
  box.append(el(level, null, title), ...nodes);
  return box;
}

/**
 * The parts `view` (`manageable`'s) is drawn in.
 * @param {{ actor: object, grownUps: object[], kids: object[] }} view
 * @param {{ grownUp: (entry: object) => HTMLElement, kid: (entry: object) => HTMLElement,
 *   addGrownUp: () => HTMLElement, addKid: () => HTMLElement }} draw a grown-up's row, a kid's, and the forms that add one
 * @returns {HTMLElement[]}
 */
export function byHousehold({ actor, grownUps, kids }, draw) {
  const live = households();
  const named = actor.owner === true && live.length > 1;
  const mine = placed(actor);
  // The server places everyone in a live household, a gone one reading as home, so every row's is listed.
  const ids = named ? [...new Set([mine, ...live.map((one) => one.id)])] : [mine];
  return ids.flatMap((id) => {
    const own = id === mine;
    const of = (list) => (named ? list.filter((one) => placed(one) === id) : list);
    const level = named ? "h3" : "h2";
    const parts = [];
    if (of(grownUps).length > 0 || (own && actor.admin)) {
      parts.push(section("Grown-ups", [...of(grownUps).map(draw.grownUp), ...(own && actor.admin ? [draw.addGrownUp()] : [])], level));
    }
    if (of(kids).length > 0 || own) parts.push(section("Kids", [...of(kids).map(draw.kid), ...(own ? [draw.addKid()] : [])], level));
    // A household the list doesn't name yet (a sync mid-way) keeps its rows, unheaded, rather than breaking the page.
    const name = live.find((one) => one.id === id)?.name;
    if (!named || parts.length === 0 || name === undefined) return parts;
    const household = section(name, parts);
    household.classList.add("manage-household");
    return [household];
  });
}
