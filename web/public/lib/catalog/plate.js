/**
 * The picture a title is recognised by.
 *
 * One copy, because three views want it now: the shelf that lays them out as
 * a wall, the index that sets a small one beside each row, and a hand-built
 * list. A poster that failed to load must leave something readable behind in
 * all three, and a watched rule must sit in the same place on all three.
 */

import { el } from "../dom.js";
import { isWatched, progressOf } from "../watch-state.js";
import { watchedFraction } from "../resume-point.js";
import { progressRule } from "./set-badge.js";

/**
 * Where a piece of artwork is served: a poster, a season's poster or a
 * backdrop, by its key. The one place the path is spelled.
 */
export function artworkUrl(key) {
  return `/api/posters/${encodeURIComponent(key)}.jpg`;
}

/** Two letters to stand in for artwork that is not there. */
export function initialsOf(text) {
  return (text ?? "?")
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0] ?? "")
    .join("")
    .toUpperCase();
}

/**
 * @param {{poster: string|null, name: string, initials: string,
 *          progress?: number|null, watched?: boolean}} options
 */
export function plate({ poster, name, initials, progress, watched }) {
  const thumb = el("div", "thumb", poster ? undefined : initials);

  if (poster) {
    // The initials stay underneath as the alt text, so a poster that fails to
    // load leaves a plate that still says what it is.
    const image = el("img");
    image.src = artworkUrl(poster);
    image.alt = name;
    image.loading = "lazy";
    thumb.append(image);
  }

  // Across the foot of the plate, where a library sticker would be, and only
  // when there is a runtime to measure against — see `watchedFraction`.
  const rule = progressRule(progress);
  if (rule) thumb.append(rule);

  // A finished title has no progress rule, because finishing clears the
  // position that would have drawn one — so without this a plate watched to
  // the end is indistinguishable from one never opened.
  if (watched) {
    const tick = el("div", "plate-tick", "✓");
    tick.title = "Watched";
    thumb.append(tick);
  }

  return thumb;
}

/**
 * The plate for one catalog row, with its own artwork and its own place.
 * @param {Pick<import("../library.js").CatalogSet, "setId"|"title"|"show"|"poster">} set
 */
export function plateOf(set) {
  const name = set.title ?? set.show ?? set.setId;
  return plate({
    poster: set.poster ?? null,
    name,
    initials: initialsOf(set.title ?? set.show),
    progress: watchedFraction(progressOf(set.setId)),
    watched: isWatched(set.setId),
  });
}
