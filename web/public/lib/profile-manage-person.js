/**
 * A person on the Profiles page: one calm row — their name, the line that says
 * who they are, and a › when there is something the manager may change — that
 * opens those controls under it on a tap. One person is open at a time, and
 * stays open across a redraw, so a change made there is followed by the same view.
 */

import { el } from "./dom.js";
import { initialOf } from "./profile-tiles.js";

/** Tells apart the controls drawn, which their rows name. */
let panels = 0;

/**
 * What draws people for one page, remembering who is open. `keep` hears of
 * each row that opens, by its person, to give it focus again after a redraw.
 * @param {(name: string, node: HTMLElement) => HTMLElement} [keep]
 * @returns {(entry: {id: string, name: string}, line: string, controls: (() => HTMLElement[])|null) => HTMLElement}
 *   a person's part: `controls` are drawn when they are opened, and `null` is
 *   someone the manager may not change, whose row is not a button
 */
export function people(keep = (_name, node) => node) {
  let openId = null;
  let shut = () => {};

  return function person(entry, line, controls) {
    const box = el("div", "manage-person");
    const parts = [el("span", "manage-name", entry.name), el("span", "setting-value", line)];
    const row = el(controls === null ? "div" : "button", "manage-row");
    // The initial the picker's tile shows, drawn by the row's own style: not a word to read out.
    row.dataset.initial = initialOf(entry.name);
    row.append(...parts);
    box.append(row);
    if (controls === null) return box;

    keep(`person ${entry.id}`, row);
    row.type = "button";
    const chevron = el("span", "manage-chevron", "›");
    chevron.setAttribute("aria-hidden", "true");
    row.append(chevron);
    const panel = el("div", "manage-controls");
    panel.id = `manage-person-${++panels}`;
    row.setAttribute("aria-controls", panel.id);
    const show = (on) => {
      row.setAttribute("aria-expanded", String(on));
      panel.replaceChildren(...(on ? controls() : []));
      panel.hidden = !on;
      if (on) shut = () => show(false);
    };
    row.addEventListener("click", () => {
      const opening = openId !== entry.id;
      shut();
      shut = () => {};
      openId = opening ? entry.id : null;
      if (opening) show(true);
    });
    show(entry.id === openId);
    box.append(panel);
    return box;
  };
}
