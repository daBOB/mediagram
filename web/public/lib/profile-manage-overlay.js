/** Manage profiles as a panel over the picker, closed by Done or Escape. */

import { el } from "./dom.js";
import { mountManage, pill } from "./profile-manage.js";

/**
 * Covers `parent` — the picker — with the panel.
 * @param {HTMLElement} parent
 * @param {() => void} onClose called once the panel is gone
 */
export function openManage(parent, onClose) {
  const screen = el("div", "who who-manage");
  const card = el("div", "who-card");
  const unmount = mountManage(card, { promptHost: screen });

  function close() {
    unmount();
    document.removeEventListener("keydown", onKey);
    screen.remove();
    onClose();
  }

  // Escape closes the panel as Done does, as Back does on Android — unless a
  // PIN prompt lies over it (a second child of the screen), which goes first.
  function onKey(event) {
    if (event.key === "Escape" && screen.childElementCount === 1) close();
  }

  card.append(pill("Done", close));
  screen.append(card);
  parent.append(screen);
  document.addEventListener("keydown", onKey);
}
