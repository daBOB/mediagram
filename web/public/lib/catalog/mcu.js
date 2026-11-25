/**
 * The Marvel Cinematic Universe as one collection: every MCU film the library
 * holds, in story order rather than release order. TMDB has no collection
 * for it, only one per hero, so the order is kept here by TMDB id, after
 * Marvel's own Disney+ timeline. A film missing from the library is skipped.
 *
 * The Android port keeps the same ids in `MarvelStoryOrder.kt`; its test reads
 * this file, so the two cannot drift.
 */

import { cutOf, groupFilms, versionsOf } from "../film-versions.js";

/** Below every TMDB collection id, which are all positive, so it never collides with one. */
export const MCU_ID = -1;

/** TMDB movie ids in story order. */
export const MCU_STORY_ORDER = [
  1771, // Captain America: The First Avenger
  299537, // Captain Marvel
  1726, // Iron Man
  10138, // Iron Man 2
  1724, // The Incredible Hulk
  10195, // Thor
  24428, // The Avengers
  76338, // Thor: The Dark World
  68721, // Iron Man 3
  100402, // Captain America: The Winter Soldier
  118340, // Guardians of the Galaxy
  283995, // Guardians of the Galaxy Vol. 2
  99861, // Avengers: Age of Ultron
  102899, // Ant-Man
  271110, // Captain America: Civil War
  497698, // Black Widow
  284054, // Black Panther
  315635, // Spider-Man: Homecoming
  284052, // Doctor Strange
  284053, // Thor: Ragnarok
  363088, // Ant-Man and the Wasp
  299536, // Avengers: Infinity War
  299534, // Avengers: Endgame
  429617, // Spider-Man: Far From Home
  566525, // Shang-Chi and the Legend of the Ten Rings
  524434, // Eternals
  634649, // Spider-Man: No Way Home
  453395, // Doctor Strange in the Multiverse of Madness
  616037, // Thor: Love and Thunder
  505642, // Black Panther: Wakanda Forever
  774752, // The Guardians of the Galaxy Holiday Special
  640146, // Ant-Man and the Wasp: Quantumania
  447365, // Guardians of the Galaxy Vol. 3
  609681, // The Marvels
  533535, // Deadpool & Wolverine
  822119, // Captain America: Brave New World
  986056, // Thunderbolts*
  617126, // The Fantastic Four: First Steps
  969681, // Spider-Man: Brand New Day
];

/** The cut the collection plays when a film is held in more than one: Endgame's Encore. */
const PREFERRED_CUT = new Map([[299534, "Encore"]]);

/**
 * The MCU as a franchise, or `null` below two held films. Each film appears
 * once; a film with a preferred cut is that copy, still carrying every version.
 * @returns {{ id: number, name: string, order: string, films: any[], art: string|null } | null}
 */
export function mcuIn(films) {
  const place = new Map(MCU_STORY_ORDER.map((id, at) => [`tmdb-movie-${id}`, at]));
  const held = groupFilms(films.flatMap(versionsOf).filter((copy) => place.has(copy.showKey)))
    .map(preferredCopy)
    .sort((a, b) => place.get(a.showKey) - place.get(b.showKey));
  if (held.length < 2) return null;
  // The same picture rule as a TMDB franchise: its most popular pictured film.
  const lead = held.filter((f) => f.backdrop || f.poster).sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))[0];
  return { id: MCU_ID, name: "Marvel Cinematic Universe", order: "In story order", films: held, art: lead?.backdrop ?? lead?.poster ?? null };
}

function preferredCopy(film) {
  const cut = PREFERRED_CUT.get(Number(film.showKey.slice("tmdb-movie-".length)));
  const copy = cut ? versionsOf(film).find((version) => cutOf(version) === cut) : null;
  return copy && film.versions ? { ...copy, versions: film.versions, offline: film.offline } : film;
}
