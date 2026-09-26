/**
 * The row of tabs under a title's feature hero (Overview · Cast · Similar ·
 * Details, or Episodes · About · …), as WAI-ARIA tabs: arrow keys move
 * between them, and only the selected tab is in the Tab order.
 *
 * A panel is built when its tab is first chosen, not up front — Similar ranks
 * the whole shelf, and most visits never open it. A tab whose content arrives
 * later (Cast, once its credits load) is added with `root.addTab`.
 */

import { el, focusWhenAttached } from "../dom.js";

let serial = 0;

/**
 * The tab last chosen on each page, by the tabs' name (the title), and the
 * page whose tabs held keyboard focus. A page is rebuilt whenever watch
 * state changes — My List, a pin, another device's position — and without
 * these a viewer on Cast would be thrown back to Overview, focus lost.
 */
const chosenOn = new Map();
let focusedOn = null;

/**
 * @param {{ label: string, build: () => Node }[]} tabs in order; the first is selected
 * @param {string} name what the tabs are about, for a screen reader
 * @returns {HTMLElement}
 */
export function tabbed(tabs, name) {
  const root = el("section", "tabs");
  const list = el("div", "tab-list");
  list.setAttribute("role", "tablist");
  list.setAttribute("aria-label", name);
  const id = `tabs-${++serial}`;
  const buttons = [];
  const panels = [];

  const entries = [...tabs];
  let selected = 0;

  function makeTab(tab) {
    const button = el("button", "tab", tab.label);
    button.type = "button";
    button.setAttribute("role", "tab");
    button.addEventListener("click", () => select(buttons.indexOf(button)));
    button.addEventListener("focus", () => { focusedOn = name; });
    // Moving elsewhere forgets it; the tab vanishing in a rebuild does not.
    button.addEventListener("blur", (event) => { if (event.relatedTarget) focusedOn = null; });
    button.addEventListener("keydown", (event) => {
      const at = buttons.indexOf(button);
      const next = {
        ArrowRight: (at + 1) % buttons.length,
        ArrowLeft: (at - 1 + buttons.length) % buttons.length,
        Home: 0,
        End: buttons.length - 1,
      }[event.key];
      if (next === undefined) return;
      event.preventDefault();
      select(next);
      buttons[next].focus();
    });
    const panel = el("div", "tab-panel");
    panel.setAttribute("role", "tabpanel");
    panel.hidden = true;
    return { button, panel };
  }

  /** Ids follow position, so they stay right when a tab is inserted. */
  function number() {
    buttons.forEach((button, at) => {
      button.id = `${id}-tab-${at}`;
      button.setAttribute("aria-controls", `${id}-panel-${at}`);
      panels[at].id = `${id}-panel-${at}`;
      panels[at].setAttribute("aria-labelledby", button.id);
    });
  }

  for (const tab of entries) {
    const { button, panel } = makeTab(tab);
    buttons.push(button);
    panels.push(panel);
    list.append(button);
  }

  function select(chosen) {
    selected = chosen;
    chosenOn.set(name, entries[chosen].label);
    buttons.forEach((button, at) => {
      const on = at === chosen;
      button.setAttribute("aria-selected", String(on));
      button.tabIndex = on ? 0 : -1;
      button.classList.toggle("on", on);
      panels[at].hidden = !on;
    });
    const panel = panels[chosen];
    if (panel.childElementCount === 0) panel.append(entries[chosen].build());
  }

  /** Inserts a tab at `at` (default: last) without disturbing the one selected. */
  root.addTab = (tab, at = entries.length) => {
    const { button, panel } = makeTab(tab);
    entries.splice(at, 0, tab);
    buttons.splice(at, 0, button);
    panels.splice(at, 0, panel);
    list.insertBefore(button, list.children[at] ?? null);
    root.insertBefore(panel, panels[at + 1] ?? null);
    number();
    // The tab the viewer was on may be this one, arriving late (Cast) —
    // unless they have chosen another since.
    if (tab.label === chosenOn.get(name)) select(at);
    else select(at <= selected ? selected + 1 : selected);
    if (focusedOn === name) focusWhenAttached(buttons[selected]);
  };

  const remembered = chosenOn.get(name);
  root.append(list, ...panels);
  number();
  select(Math.max(0, entries.findIndex((tab) => tab.label === remembered)));
  // A late tab may still claim it, so keep what the viewer chose.
  if (remembered) chosenOn.set(name, remembered);
  if (focusedOn === name) focusWhenAttached(buttons[selected]);
  return root;
}
