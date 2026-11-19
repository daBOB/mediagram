/**
 * The picker's Manage profiles: the same Profiles page Settings shows, opened
 * over the picker with nobody chosen — so no You, only Who's managing?, drawn
 * as Who's watching? is, with Done where the picker has Stay as I am. Done or
 * Escape leaves it for the picker again, dropping the PIN.
 */

import { el } from "./dom.js";
import { profilesPanel } from "./catalog/settings-profiles.js";

/**
 * Covers `parent` — the picker — with the page.
 * @param {HTMLElement} parent
 * @param {() => void} onClose called once the page is gone
 */
export function openProfilesPage(parent, onClose) {
  const screen = el("div", "who who-manage");
  const card = el("div", "who-card");
  // Switching household goes back to the picker, which then shows that household.
  const { box, stop } = profilesPanel(screen, { profile: null, switchProfile: close });

  function close() {
    stop();
    document.removeEventListener("keydown", onKey);
    screen.remove();
    onClose();
  }

  // Escape closes the page as Done does, as Back does on Android — unless a
  // PIN prompt lies over it (a second child of the screen), which goes first.
  function onKey(event) {
    if (event.key === "Escape" && screen.childElementCount === 1) close();
  }

  const done = el("button", "quiet", "Done");
  done.type = "button";
  done.addEventListener("click", close);
  card.append(box, done);
  screen.append(card);
  parent.append(screen);
  document.addEventListener("keydown", onKey);
}
