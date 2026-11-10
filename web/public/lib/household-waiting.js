/**
 * Said in place of "Create the first profile" until the server has heard its
 * household — taken in a sync round of the channel it follows. A first
 * profile made before that, under a household member's name, would merge
 * with that member and hand its PIN to them on every device. Android's
 * pickers say the same words.
 */

import { el } from "./dom.js";
import { renderAdminSettings } from "./settings-view.js";

/** The line itself. */
export const WAITING = "Waiting for this household’s profiles…";

/**
 * The line, a way to read who is here again, and Settings in place: a player
 * that is not signed in to Telegram never hears its household, and the app
 * routes to `#/settings` only once a profile is chosen, so signing in has to
 * be reachable from here or a new player waits forever.
 */
export function waitingForHousehold(onRetry) {
  const box = el("div", "who-ask");
  const again = el("button", "quiet", "Try again");
  again.type = "button";
  again.addEventListener("click", onRetry);
  const settings = el("button", "quiet", "Settings");
  settings.type = "button";
  settings.addEventListener("click", () => {
    settings.remove();
    renderAdminSettings(box);
  });
  box.append(el("h2", null, WAITING), again, settings);
  return box;
}
