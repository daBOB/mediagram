/**
 * Which films the Featured reel shows, and in what order.
 *
 * The reel is there to help choose something to watch, so it holds films this
 * profile has not seen, shuffled so each opening suggests something new. A
 * film without a poster is left out: the reel is made of posters, and a blank
 * slide would tease nothing.
 */

/** How many films one opening of the reel runs through. */
export const FEATURED_COUNT = 12;

/**
 * @template {{setId: string, poster?: string|null}} T
 * @param {T[]} movies the Movies shelf, in any order
 * @param {(setId: string) => boolean} isWatched
 * @param {() => number} random a source in [0, 1), passed in so tests can fix it
 * @param {number} [count]
 * @returns {T[]}
 */
export function pickFeatured(movies, isWatched, random, count = FEATURED_COUNT) {
  const pool = movies.filter((set) => set.poster && !isWatched(set.setId));
  // Fisher–Yates over a copy: the shelf itself keeps its title order.
  for (let index = pool.length - 1; index > 0; index--) {
    const other = Math.floor(random() * (index + 1));
    [pool[index], pool[other]] = [pool[other], pool[index]];
  }
  return pool.slice(0, count);
}

/** The slide `by` steps from `index`, wrapping round a reel of `length`. */
export function stepFrom(index, by, length) {
  return (((index + by) % length) + length) % length;
}
