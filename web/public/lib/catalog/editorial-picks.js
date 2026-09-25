/**
 * What the magazine home page features, decided before anything is drawn.
 *
 * Every pick comes from a fact the library holds — a pin, a provider score,
 * a provider popularity figure, an arrival date — and every label says which
 * fact it was. Nothing here invents a reason to watch something.
 *
 * Choices that rotate are seeded by the day rather than by `Math.random`: the
 * home page is redrawn whenever watch state changes, and a cover that
 * reshuffled under the viewer each time they paused a film elsewhere would be
 * a page that cannot be read.
 *
 * Pure, so the rules are tested without a browser; `home-view.js` draws.
 */

import { pickFeatured } from "./featured-picks.js";

export const DAY_MS = 86_400_000;
/** How many films the cover story rotates through. */
export const COVER_COUNT = 5;
/** The staff pick rotates among this many of the best-rated. */
const STAFF_POOL = 10;
/** "This month" means arrivals in the last thirty days. */
const MONTH_MS = 30 * DAY_MS;
const THIS_MONTH_LIMIT = 5;

/** Which day `now` falls on, counted from the epoch; the rotation's seed. */
export const dayOf = (now) => Math.floor(now / DAY_MS);

/**
 * A small deterministic generator (mulberry32): the same seed gives the same
 * sequence, which is what keeps a day's cover the same across redraws.
 * @returns {() => number} a source in [0, 1)
 */
export function seededRandom(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {"editor"|"staff"|"trending"|"new"} FeatureKind
 * @typedef {{kind: FeatureKind, set: CatalogSet}} Feature
 */

/**
 * The whole page's picks, with no title featured twice.
 *
 * The editor's choice is honoured first — it is the one pick a person made —
 * then the two ranked features, then the cover from what is left, then the
 * quote. A pin may be any title, watched or not, film or episode: pinning is
 * the statement. Everything else is drawn from films this profile has not
 * watched that have artwork to show — a backdrop, for the cover.
 *
 * `onRow` is what the Recently added row already shows; "This month" beside
 * it lists the arrivals after those, so the two never repeat each other.
 *
 * @param {{movies: CatalogSet[], byId: Map<string, CatalogSet>,
 *   isWatched: (setId: string) => boolean, editorsChoice: string|null,
 *   now: number, onRow?: Set<string>}} from
 */
export function homeEditorial({ movies, byId, isWatched, editorsChoice, now, onRow = new Set() }) {
  const day = dayOf(now);
  const taken = new Set();
  const take = (set) => {
    if (set) taken.add(set.setId);
    return set;
  };
  // Features take a poster where there is no backdrop (`home-features.js`
  // crops it); the cover is a full-width photograph and needs a backdrop.
  const open = (art = (set) => set.backdrop || set.poster) =>
    movies.filter((set) => art(set) && !isWatched(set.setId) && !taken.has(set.setId));

  const pinned = editorsChoice ? byId.get(editorsChoice) ?? null : null;
  const editor = take(pinned);

  const trendingSet = take(mostPopular(open()));
  // Nothing carries a popularity figure — an index from before it was
  // recorded. Say what the card actually is rather than claim a trend.
  const trending = trendingSet ? { kind: "trending", set: trendingSet } : newest(open(), take);

  const staff = take(staffPick(open(), day));
  // No pin: the slot follows the staff rule, and is labelled as such.
  const lead = editor ? { kind: "editor", set: editor } : withKind("staff", take(staffPick(open(), day + 1)));

  /** @type {Feature[]} */
  const features = [lead, trending, withKind("staff", staff)].filter(Boolean);

  const cover = pickFeatured(open((set) => set.backdrop), isWatched, seededRandom(day), COVER_COUNT);
  for (const set of cover) take(set);

  return { cover, features, quote: quoteOf(movies, taken, day), thisMonth: arrivedWithin(movies.filter((set) => !onRow.has(set.setId)), now) };
}

const withKind = (kind, set) => (set ? { kind, set } : null);

function mostPopular(pool) {
  let best = null;
  for (const set of pool) {
    if ((set.popularity ?? 0) > (best?.popularity ?? 0)) best = set;
  }
  return best;
}

function newest(pool, take) {
  const set = [...pool].sort((a, b) => (b.addedAt ?? 0) - (a.addedAt ?? 0))[0];
  return set ? { kind: "new", set: take(set) } : null;
}

/** One of the best-rated, turning over daily; ties broken by title for a stable order. */
function staffPick(pool, day) {
  const ranked = pool
    .filter((set) => (set.rating ?? 0) > 0)
    .sort((a, b) => b.rating - a.rating || String(a.title).localeCompare(String(b.title)))
    .slice(0, STAFF_POOL);
  return ranked.length === 0 ? null : ranked[day % ranked.length];
}

/** Past this, a tagline set as a pull-quote runs to eight lines beside four cards. */
const QUOTE_MAX = 90;

/**
 * A real tagline for the typographic break, from a film not featured above.
 * A short one if there is any: the quote is a pause, not a paragraph.
 */
function quoteOf(movies, taken, day) {
  const open = movies.filter((set) => set.tagline && !taken.has(set.setId));
  const short = open.filter((set) => set.tagline.length <= QUOTE_MAX);
  const pool = short.length > 0 ? short : open;
  if (pool.length === 0) return null;
  return pool[Math.floor(seededRandom(day + 7)() * pool.length)];
}

/** Films that arrived in the last thirty days, newest first. */
export function arrivedWithin(movies, now, windowMs = MONTH_MS, limit = THIS_MONTH_LIMIT) {
  return movies
    .filter((set) => (set.addedAt ?? 0) > now - windowMs)
    .sort((a, b) => b.addedAt - a.addedAt)
    .slice(0, limit);
}
