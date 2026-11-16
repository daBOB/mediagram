/**
 * One film held as more than one file.
 *
 * A film can be uploaded at another quality (1080p, 4K HDR) or in another cut
 * (Director's Cut, Extended). The library shows it once and the viewer picks
 * which file to play. Copies are grouped on read by the poster key the
 * catalog already carries (`tmdb-movie-<id>`), so the index, the watch state
 * and the sync record stay exactly as they are: everything here is computed.
 *
 * Quality copies and cuts are told apart because they are different things.
 * Copies of one cut share a timeline, so a position carries between them;
 * cuts do not, so it never crosses from one to another.
 *
 * Plain JavaScript because the browser loads this file directly; the tests and
 * the Android port read the same rules from `test/fixtures/film-versions/`.
 */

import { humanDuration, humanSize } from "./format.js";
import { resumeAt } from "./resume-point.js";

/** Best first; anything else sorts after these. */
const RESOLUTIONS = ["2160p", "1080p", "720p", "480p"];

const resolutionRank = (quality) => {
  const at = RESOLUTIONS.indexOf(quality);
  return at === -1 ? RESOLUTIONS.length : at;
};
const isHdr = (set) => Boolean(set.hdr) && set.hdr !== "SDR";
/** A version's cut; `null` (or an empty label) is the standard one. */
export const cutOf = (set) => set.variant || null;

/**
 * Version order: the standard cut first, other cuts by name; within a cut the
 * higher resolution, then HDR before SDR; then the set id, so the order is
 * total and two devices never disagree about which copy comes first.
 */
export function compareVersions(a, b) {
  const ca = cutOf(a);
  const cb = cutOf(b);
  if (ca !== cb) {
    if (ca === null) return -1;
    if (cb === null) return 1;
    // Plain code-unit order, not the locale's: Android sorts the same way.
    return ca < cb ? -1 : 1;
  }
  return resolutionRank(a.quality) - resolutionRank(b.quality)
    || Number(isHdr(b)) - Number(isHdr(a))
    || (a.setId < b.setId ? -1 : a.setId > b.setId ? 1 : 0);
}

/**
 * A film's identity: its provider poster key, or the set itself. Only a TMDB
 * key names one film; the fallback key is built from the title alone, and two
 * untagged films called "Dune" are not copies of each other.
 */
function filmKey(set) {
  return set.kind === "movie" && set.showKey?.startsWith("tmdb-movie-") ? set.showKey : `set:${set.setId}`;
}

/**
 * One entry per film, in the order each film first appears.
 *
 * A film held once is its set, untouched, so a single copy renders exactly as
 * it always has. A film held more than once is its first version in version
 * order (the representative: poster, title, facts) with every version under
 * `versions`, and is offline when any version is held in full.
 */
export function groupFilms(sets) {
  const byKey = new Map();
  for (const set of sets) {
    const key = filmKey(set);
    const versions = byKey.get(key);
    if (versions) versions.push(set);
    else byKey.set(key, [set]);
  }
  return [...byKey.values()].map((versions) => {
    if (versions.length === 1) return versions[0];
    versions.sort(compareVersions);
    return { ...versions[0], versions, offline: versions.some((version) => version.offline === true) };
  });
}

/** Every version of a film, in version order; a single copy is its own. */
export const versionsOf = (film) => film.versions ?? [film];

/** Every version's set id to the film it belongs to. */
export function filmIndex(films) {
  const index = new Map();
  for (const film of films) for (const version of versionsOf(film)) index.set(version.setId, film);
  return index;
}

/**
 * How a version is told apart from its siblings:
 * `Standard · 4K HDR · 3h 1m · 14.4 GB`. Runtime is always there, because it
 * is how two cuts differ; parts the index does not know are left out.
 */
export function versionLabel(set) {
  const resolution = set.quality === "2160p" ? "4K" : set.quality;
  const picture = [resolution, isHdr(set) ? "HDR" : null].filter(Boolean).join(" ");
  const size = Number(set.total) > 0 ? humanSize(set.total) : null;
  return [cutOf(set) ?? "Standard", picture, humanDuration(set.duration), size].filter(Boolean).join(" · ");
}

/** A film is played once any of its versions has been finished. */
export function filmPlayed(film, watchedAt) {
  return versionsOf(film).some((version) => watchedAt(version.setId) !== null);
}

/**
 * The position `version` starts from, as a progress row, or `null`.
 *
 * The newest position saved in any version of the same cut, unless a version
 * of that cut was finished after it was saved: finishing the 4K copy closes
 * the half-watched 1080p one too, while a Director's Cut keeps its own place.
 * Thresholds (`resumeAt`) are the caller's, as for any single title.
 */
export function versionPosition(version, film, progressOf, watchedAt) {
  const cut = cutOf(version);
  const sameCut = versionsOf(film).filter((other) => cutOf(other) === cut);
  let newest = null;
  for (const other of sameCut) {
    const row = progressOf(other.setId);
    if (row && (newest === null || row.updatedAt > newest.updatedAt)) newest = row;
  }
  if (newest === null) return null;
  const closed = sameCut.some((other) => (watchedAt(other.setId) ?? -Infinity) > newest.updatedAt);
  return closed ? null : newest;
}

/** The film's newest resumable position across all its cuts, for its card. */
export function filmPosition(film, progressOf, watchedAt) {
  let newest = null;
  for (const version of versionsOf(film)) {
    const row = versionPosition(version, film, progressOf, watchedAt);
    if (row && resumeAt(row) !== null && (newest === null || row.updatedAt > newest.updatedAt)) newest = row;
  }
  return newest;
}

/**
 * Progress rows (newest first) as Continue should see them. A film held more
 * than once appears once, by its newest row that is still its cut's position
 * (`versionPosition`) and worth resuming, so Continue, the card and the
 * chooser never disagree. Rows for anything held once pass untouched, so
 * episodes and single films keep the rules they always had.
 */
export function continueRows(rows, filmOf, watchedAt) {
  const byId = new Map(rows.map((row) => [row.setId, row]));
  const progressOf = (setId) => byId.get(setId) ?? null;
  const shown = new Set();
  return rows.filter((row) => {
    const film = filmOf(row.setId);
    if (!film?.versions) return true;
    if (resumeAt(row) === null) return true; // every caller drops it anyway
    const version = film.versions.find((candidate) => candidate.setId === row.setId);
    if (versionPosition(version, film, progressOf, watchedAt) !== row) return false;
    if (shown.has(film.setId)) return false;
    shown.add(film.setId);
    return true;
  });
}

/**
 * Sets (a list, the watchlist, search hits) with each copy of a film held more
 * than once standing in as its whole film, once. Anything else is kept as it
 * came, with whatever the caller attached to it.
 */
export function asFilms(items, filmOf) {
  const seen = new Set();
  const out = [];
  for (const item of items) {
    const film = filmOf(item.setId);
    const shown = film?.versions ? film : item;
    if (seen.has(shown.setId)) continue;
    seen.add(shown.setId);
    out.push(shown);
  }
  return out;
}
