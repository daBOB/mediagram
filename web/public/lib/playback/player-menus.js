/**
 * What the card's buttons open: a short list just above the button — speed,
 * audio, framing, the subtitle options — or, for the subtitle style, a panel
 * of its own in the same place.
 *
 * One at a time, because two open lists over a picture are two things in the
 * way. Choosing closes the list. Esc closes whatever is open and nothing else:
 * the dialog would otherwise take the same key as a request to close the
 * player, and a viewer backing out of a list asked for no such thing.
 */

import { el } from "../dom.js";

/**
 * @typedef {{value: string, label: string, current?: boolean}} MenuItem
 * @param {{onClose?: () => void}} [options] `onClose` hears every close, so
 *   whatever was held up while a list was open can start its clock again.
 */
export function mountPlayerMenus({ onClose } = {}) {
  const dialog = document.getElementById("player");
  const dock = document.getElementById("card-dock");
  const menu = document.getElementById("card-menu");
  /** The button whose list or panel is open, and what it opened. */
  let opener = null;
  let shown = null;

  /** Closes whatever is open; answers the button that opened it, if any. */
  function close() {
    const was = opener;
    if (was === null) return null;
    shown.hidden = true;
    was.setAttribute("aria-expanded", "false");
    opener = shown = null;
    onClose?.();
    return was;
  }

  /** Shows `node` above `button`, closing anything else first. */
  function open(button, node) {
    close();
    opener = button;
    shown = node;
    node.hidden = false;
    button.setAttribute("aria-expanded", "true");
    // Kept inside the card's width: a list opened from the last button on a
    // row must not hang off the card's edge.
    const area = dock.getBoundingClientRect();
    const from = button.getBoundingClientRect().left - area.left;
    const room = area.width - node.getBoundingClientRect().width;
    node.style.left = `${Math.round(Math.max(0, Math.min(from, room)))}px`;
  }

  /**
   * A list of values behind `button`. `items` is asked on every open, so the
   * list marks what is current now rather than when it was first built.
   * @param {HTMLElement} button
   * @param {{items: () => MenuItem[], pick: (value: string) => void}} choices
   */
  function list(button, { items, pick }) {
    button.setAttribute("aria-haspopup", "true");
    button.setAttribute("aria-expanded", "false");
    button.setAttribute("aria-controls", menu.id);
    button.addEventListener("click", () => {
      if (opener === button) {
        close();
        return;
      }
      const rows = items().map((item) => {
        const row = el("button", "menu-item", item.label);
        row.type = "button";
        row.dataset.value = item.value;
        row.setAttribute("aria-pressed", String(item.current === true));
        row.addEventListener("click", () => {
          // The focused row is about to be hidden; without a new home focus falls
          // to the page and the player's keys stop hearing the viewer.
          close()?.focus();
          pick(item.value);
        });
        return row;
      });
      menu.replaceChildren(...rows);
      open(button, menu);
      // A keyboard starts from what is chosen now.
      (rows.find((row) => row.getAttribute("aria-pressed") === "true") ?? rows[0])?.focus();
    });
  }

  /** A panel opened from `button` that stays open while it is used. */
  function panel(button, node) {
    open(button, node);
    node.focus();
  }

  dialog.addEventListener("keydown", (event) => {
    if (event.key !== "Escape" || opener === null) return;
    // Taken here, so neither the dialog nor a listener after this one reads
    // the same key as a request of its own.
    event.preventDefault();
    close()?.focus();
  });
  // Anywhere else in the player is a viewer done with the list.
  dialog.addEventListener("pointerdown", (event) => {
    if (opener !== null && !shown.contains(event.target) && !opener.contains(event.target)) close();
  });

  return { list, panel, close, isOpen: () => opener !== null };
}
