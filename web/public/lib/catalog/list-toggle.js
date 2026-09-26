/**
 * "My List": the watchlist, as a pill beside a title's play button. A toggle
 * rather than two buttons, pressed while the title is on the list.
 *
 * Writes through `watch-state.js`, which redraws the page on a change, so the
 * button never has to keep its own label in step.
 */

import { el } from "../dom.js";
import { isWatchlisted, setWatchlisted } from "../watch-state.js";

/** @param {string} setId a film, or a show's first episode */
export function listToggle(setId, className = "pill pill-line") {
  const listed = isWatchlisted(setId);
  const button = el("button", `${className} list-toggle`);
  button.type = "button";
  button.setAttribute("aria-pressed", String(listed));
  button.append(el("span", "pill-icon", listed ? "✓" : "+"), el("span", null, "My List"));
  button.addEventListener("click", () => setWatchlisted(setId, !isWatchlisted(setId)));
  return button;
}
