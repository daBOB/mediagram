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
  const decision = playbackFor(set);
  if (decision.kind === "direct") return null;

  // `warn` because a shelf tag and a search tag share the badge shape, and
  // only this one is telling the viewer something might go wrong.
  const badge = el("span", "badge warn", "needs transcode");
  // The reason as a tooltip rather than as more text: a shelf of forty cards
  // each carrying "Matroska container, HEVC video" is a wall, and the reason
  // is a question a viewer asks about one card at a time. The player states
  // it in full once the title is open.
  badge.title = decision.reason;
  return badge;
}
