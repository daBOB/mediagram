/**
 * "My List": the watchlist, as a pill beside a title's play button. A toggle
 * rather than two buttons, pressed while the title is on the list.
 *
 * Writes through `watch-state.js`, which redraws the page on a change, so the
 * button never has to keep its own label in step.
 */

import { el } from "../dom.js";
import { isWatchlisted, setWatchlisted } from "../watch-state.js";

/**
 * A film held more than once is on the list when any copy is: the player's
 * own toggle stores the copy playing, and the first copy changes when a better
 * one arrives. Adding stores the first; taking it off removes every copy listed.
 * @param {string|string[]} ids a film's copies, first first, or a show's first episode
 */
export function listToggle(ids, className = "pill pill-line") {
  const copies = [ids].flat();
  const onList = () => copies.filter(isWatchlisted);
  const listed = onList().length > 0;
  const button = el("button", `${className} list-toggle`);
  button.type = "button";
  button.setAttribute("aria-pressed", String(listed));
  button.append(el("span", "pill-icon", listed ? "✓" : "+"), el("span", null, "My List"));
  button.addEventListener("click", () => {
    const held = onList();
    if (held.length === 0) setWatchlisted(copies[0], true);
    else for (const id of held) setWatchlisted(id, false);
  });
  return button;
}
