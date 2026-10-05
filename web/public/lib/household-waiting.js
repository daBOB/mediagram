/**
 * Said in place of "Create the first profile" until the server has heard its
 * household — taken in a sync round of the channel it follows. A first
 * profile made before that, under a household member's name, would merge
 * with that member and hand its PIN to them on every device. Android's
 * pickers say the same words.
 */

import { el } from "./dom.js";

/** The line itself. */
export const WAITING = "Waiting for this household’s profiles…";

/** The line, and a way to read who is here again. */
export function waitingForHousehold(onRetry) {
  const box = el("div", "who-ask");
  const again = el("button", "quiet", "Try again");
  again.type = "button";
  again.addEventListener("click", onRetry);
  box.append(el("h2", null, WAITING), again);
  return box;
}
