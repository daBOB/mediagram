/**
 * The picker's Manage profiles: the same Profiles page Settings shows, opened
 * over the picker with nobody chosen — so everyone here and managing them, no
 * You. Done or Escape leaves it for the picker again, dropping the PIN.
 */

import { el } from "./dom.js";
import { pill } from "./profile-manage.js";
import { PROFILES, profilesPanel } from "./catalog/settings-profiles.js";

/**
 * Covers `parent` — the picker — with the page.
 * @param {HTMLElement} parent
 * @param {() => void} onClose called once the page is gone
 */
export function openProfilesPage(parent, onClose) {
  const screen = el("div", "who who-manage");
  const card = el("div", "who-card");
  const { box, stop } = profilesPanel(screen, { profile: null });

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

  card.append(el("h1", null, PROFILES), box, pill("Done", close));
  screen.append(card);
  parent.append(screen);
  document.addEventListener("keydown", onKey);
}
