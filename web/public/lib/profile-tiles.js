/**
 * The avatar tiles of Who's watching?: a big initial, the name, and under it
 * whatever line the caller has for that profile. Who's managing? draws the
 * same ones, so the two questions look alike.
 */

import { el } from "./dom.js";

/** The letter on a profile's tile. */
export const initialOf = (name) => (name ?? "?").trim().charAt(0).toUpperCase() || "?";

/**
 * One tile per profile in `people`, in their order.
 * @param {Array<{ name: string }>} people
 * @param {{ onChoose: (entry: any) => void, extra?: (entry: any) => HTMLElement|null, disabled?: boolean }} options
 *   `extra` is the line under a tile's name, if it has one
 * @returns {HTMLElement}
 */
export function profileTiles(people, { onChoose, extra = () => null, disabled = false }) {
  const row = el("div", "who-tiles");
  for (const entry of people) {
    const tile = el("button", "who-tile");
    tile.append(el("span", "who-initial", initialOf(entry.name)), el("span", "who-name", entry.name));
    const line = extra(entry);
    if (line) tile.append(line);
    tile.disabled = disabled;
    tile.addEventListener("click", () => onChoose(entry));
    row.append(tile);
  }
  return row;
}
