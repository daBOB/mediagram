/** The Sections switches Manage profiles draws under a row: one per section, on while it is shown. */

import { el } from "./dom.js";
import { SECTIONS } from "./catalog/sections.js";
import { SECTION_ACCESS_WORDS } from "./section-access.js";

/**
 * A switch per section for `entry`, in the nav's order. Turning one hands
 * `onSet` the whole list it is not shown — keys this build does not know
 * kept as they came — and the key turned, and holds every switch until the
 * panel redraws from the answer, so a second turn never sends a list the
 * first has not settled. `keep` hears of each switch by its key.
 * @param {{ name: string, hiddenSections?: string[] }} entry
 * @param {(hidden: string[], key: string) => void} onSet
 * @param {(key: string, button: HTMLButtonElement) => HTMLButtonElement} [keep]
 */
export function sectionSwitches(entry, onSet, keep = (_key, button) => button) {
  const hidden = entry.hiddenSections ?? [];
  const group = el("div", "section-switches");
  group.setAttribute("role", "group");
  group.setAttribute("aria-label", `${SECTION_ACCESS_WORDS.title} for ${entry.name}`);
  const title = el("span", "section-switches-title", SECTION_ACCESS_WORDS.title);
  title.setAttribute("aria-hidden", "true");
  const switches = Object.entries(SECTIONS).map(([key, { label }]) => {
    const shown = !hidden.includes(key);
    const button = el("button", "section-switch", label);
    button.type = "button";
    button.setAttribute("role", "switch");
    button.setAttribute("aria-checked", String(shown));
    button.addEventListener("click", () => {
      for (const one of switches) one.disabled = true;
      onSet(shown ? [...hidden, key] : hidden.filter((other) => other !== key), key);
    });
    return keep(key, button);
  });
  group.append(title, ...switches);
  return group;
}
