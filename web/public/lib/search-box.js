/**
 * The masthead's search box: what typing in it asks for, and what the address
 * writes back into it.
 *
 * The address carries the query trimmed, so a link to a result list reads
 * cleanly, while the box holds exactly what was typed. Both halves of that
 * rule live here, since each has to know what the other does with a space.
 */

import { go } from "./address.js";

/** How long the typing has to pause before the address follows it. */
const PAUSE_MS = 200;

/**
 * Typing searches, with a pause.
 *
 * The hash carries the query so a result list can be linked and the back
 * button leaves it, but writing the hash on every keystroke would fill the
 * history with half-typed words — so the address waits for the typing to
 * pause. An emptied box goes back to Movies.
 */
export function searchWhileTyping(box) {
  let typing = null;
  box.addEventListener("input", () => {
    clearTimeout(typing);
    typing = setTimeout(() => {
      const query = box.value.trim();
      go(query === "" ? { page: "department", section: "movies" } : { page: "search", query });
    }, PAUSE_MS);
  });
}

/**
 * Makes the box say `query`, the search the address names.
 *
 * A box that already says it is left as the viewer typed it. The pause above
 * falls between words as easily as after them, and writing the trimmed query
 * back then ate the space just typed — "star ", a pause, "trek" came out as
 * "startrek" — and threw the caret to the end of a query being edited in the
 * middle.
 */
export function showQuery(box, query) {
  if (box.value.trim() !== query) box.value = query;
}
