/**
 * What a row or card says about a set beyond its name.
 *
 * Kept apart from either view because both need it: a film is a card and a
 * lesson is a row, and neither owns the question of whether the set will
 * play as it is.
 */

import { el } from "./dom.js";
import { playbackFor } from "./link.js";

/** Whether this set will have to be converted before it plays. */
export function transcodeBadge(set) {
  // `warn` because a shelf tag and a search tag share the badge shape, and
  // only this one is telling the viewer something might go wrong.
  return playbackFor(set).kind === "direct" ? null : el("span", "badge warn", "needs transcode");
}
