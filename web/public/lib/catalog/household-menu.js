/**
 * A household's ⋯ on the owner's view: one round button, named for the
 * household, that opens a small list under it of what may be done to it.
 * Choosing one closes it, as do Escape — which hands focus back to the ⋯ and
 * goes no further, so the page under it stays open — a press anywhere else,
 * and focus moving on past it.
 */

import { el } from "../dom.js";

/** Tells apart the lists drawn, which their buttons name. */
let menus = 0;

/**
 * @param {string} label the ⋯'s name, which says whose actions these are
 * @param {Array<[string, () => void]>} actions each one's word, and what choosing it does
 * @param {(node: HTMLElement) => HTMLElement} [keep] hears of the ⋯, to give it focus again after a redraw
 * @returns {HTMLElement}
 */
export function actionsMenu(label, actions, keep = (node) => node) {
  const box = el("div", "more-menu household-more");
  const more = keep(el("button", "more-button", "⋯"));
  more.type = "button";
  more.setAttribute("aria-label", label);
  const list = el("div", "more-list household-actions");
  list.id = `household-actions-${++menus}`;
  more.setAttribute("aria-controls", list.id);
  const outside = (event) => {
    if (!box.contains(event.target)) show(false);
  };
  function show(on) {
    list.hidden = !on;
    more.setAttribute("aria-expanded", String(on));
    if (on) document.addEventListener("pointerdown", outside);
    else document.removeEventListener("pointerdown", outside);
  }
  for (const [word, run] of actions) {
    const item = el("button", "more-item", word);
    item.type = "button";
    item.addEventListener("click", () => {
      show(false);
      run();
    });
    list.append(item);
  }
  more.addEventListener("click", () => {
    show(list.hidden);
    if (!list.hidden) list.children[0]?.focus();
  });
  // Tabbed past, it closes rather than cover what is below. Focus going nowhere (a
  // press on the page, which some browsers do not focus) is the press's to close.
  box.addEventListener("focusout", (event) => {
    if (event.relatedTarget && !box.contains(event.relatedTarget)) show(false);
  });
  box.addEventListener("keydown", (event) => {
    if (event.key !== "Escape" || list.hidden) return;
    event.stopPropagation();
    show(false);
    more.focus();
  });
  show(false);
  box.append(more, list);
  return box;
}
