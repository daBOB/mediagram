/**
 * Where a show's page offers to carry on: the button a series header leads
 * with ("Resume S3 E15"), decided without a DOM so it can be tested.
 *
 * In order of what the viewer most plausibly wants:
 *   1. the episode of this show they last stopped partway through;
 *   2. otherwise the one after the furthest episode they finished;
 *   3. otherwise the first episode.
 * A show watched to its end offers its first episode again.
 */

import { episodeNumber } from "../episode-label.js";
import { flattenCollection } from "../library.js";

/**
 * @param {Pick<import("../library.js").Collection, "divisions">} collection
 * @param {{ resumeOf: (setId: string) => number|null,
 *   recent: string[], watched: (setId: string) => boolean }} state
 *   `recent` is every set id with a position, most recently touched first
 * @returns {{ set: import("../library.js").CatalogSet, at: number|null, verb: "Resume"|"Continue"|"Play" }|null}
 */
export function seriesResume(collection, { resumeOf, recent, watched }) {
  const episodes = flattenCollection(collection);
  if (episodes.length === 0) return null;

  const inShow = new Set(episodes.map((set) => set.setId));
  for (const setId of recent) {
    if (!inShow.has(setId)) continue;
    const at = resumeOf(setId);
    if (at !== null) return { set: episodes.find((set) => set.setId === setId), at, verb: "Resume" };
  }

  let furthest = -1;
  episodes.forEach((set, index) => { if (watched(set.setId)) furthest = index; });
  const next = episodes[furthest + 1];
  if (furthest >= 0 && next) return { set: next, at: null, verb: "Continue" };
  return { set: episodes[0], at: null, verb: "Play" };
}

/** `S3 E15` for an episode with both numbers, else its title. A range shows its first episode. */
export function episodeShort(set) {
  const first = episodeNumber(set).split("-")[0];
  if (set.season != null && first) return `S${set.season} E${first}`;
  return set.title ?? "";
}
