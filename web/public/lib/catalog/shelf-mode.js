/**
 * Whether a shelf is a list or a wall of plates.
 *
 * A property of the device rather than of the library, so it lives in
 * `localStorage` beside the chosen profile: the television that shows posters
 * keeps showing posters, and a laptop someone else picks up is not changed by
 * what the television was set to.
 *
 * The list is the default. It is the view this catalogue was designed as, and
 * a viewer who wants posters says so once.
 */

const KEY = "mediagram.shelfView";

export const LIST = "list";
export const GRID = "grid";

let remembered = LIST;
let localOnly = false;

/**
 * @param {string|null} stored what was read out of storage
 * @returns {"list"|"grid"} the mode to use
 *
 * Anything unrecognised is the list, including `null`. A value written by a
 * later version of this page must not leave a viewer with no shelf at all.
 */
export function modeFrom(stored) {
  return stored === GRID ? GRID : LIST;
}

/**
 * The mode this device is set to.
 *
 * Wrapped because a browser with storage disabled must still show a shelf:
 * a private window, or one with site data blocked, throws on the read rather
 * than answering null.
 */
export function shelfMode() {
  if (localOnly) return remembered;
  try {
    remembered = modeFrom(window.localStorage.getItem(KEY));
  } catch {
    // Keep this page's choice when storage becomes unavailable.
  }
  return remembered;
}

/** Remembers `mode`, or forgets the setting when it is the default. */
export function setShelfMode(mode) {
  const chosen = modeFrom(mode);
  remembered = chosen;
  try {
    // The default is stored as its absence, so a viewer who never chose and a
    // viewer who chose the list are the same viewer — and clearing site data
    // returns both to the same place.
    if (chosen === LIST) window.localStorage.removeItem(KEY);
    else window.localStorage.setItem(KEY, chosen);
    localOnly = false;
  } catch {
    // The choice lasts this page load instead, which still works.
    localOnly = true;
  }
  return chosen;
}
