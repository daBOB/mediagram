/**
 * The way to make a title the household's editor's choice, from its page.
 *
 * A toggle: pressed on the current pick, where pressing it again unpins.
 * Pinning another title replaces the pick (the server retires the old one),
 * and the page redraws from the change, so this never has to listen.
 */

import { el } from "../dom.js";
import { editorsChoice, setEditorsChoice } from "../editors-choice.js";
import { profile } from "../watch-state.js";

/** The title whose control was just pressed, so its replacement takes focus. */
let pressed = null;

/**
 * @param {string} setId the title to pin; a show pins by its first episode
 * @returns {HTMLElement|null} `null` on a kids profile: the pick is the whole
 *   household's, and choosing it is a grown-up's decision
 */
export function pinControl(setId) {
  if (profile()?.kids === true) return null;
  const pinned = editorsChoice() === setId;
  const button = el("button", pinned ? "pin-control on" : "pin-control",
    pinned ? "Editor’s choice ✓" : "Make editor’s choice");
  button.type = "button";
  button.setAttribute("aria-pressed", String(pinned));
  button.title = pinned ? "Leads the home page's features. Press to unpin." : "Lead the home page's features with this.";
  button.addEventListener("click", () => {
    pressed = setId;
    setEditorsChoice(setId, editorsChoice() !== setId);
  });
  // Pressing it redraws the page, which replaces this button; the new one
  // takes focus back once it is in the document, so a keyboard viewer stays put.
  if (pressed === setId) {
    pressed = null;
    queueMicrotask(() => button.focus());
  }
  return button;
}
