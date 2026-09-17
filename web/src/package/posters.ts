/**
 * The artwork a package carries.
 *
 * A poster key is `tmdb-movie-<id>` or `tmdb-tv-<id>`. TMDB's film and
 * television id spaces are independent, so the kind is part of the key and a
 * film sharing an id with a series never collides with it.
 *
 * The key reaches a URL and then a file name, so it is spelled out rather
 * than passed through: a store that accepts any string is one caption away
 * from reading a path the package never named.
 */

import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const KEY = /^tmdb-(?:movie|tv)-\d{1,12}$/;

/** The key for a set, or `null` when it has no TMDB id to build one from. */
export function posterKeyFor(kind: string, tmdb: number | null): string | null {
  if (tmdb === null || !Number.isInteger(tmdb) || tmdb <= 0) return null;
  // Everything that is not a film is filed under a show: an episode's artwork
  // is the series', which is also the only poster the package carries for it.
  return `tmdb-${kind === "movie" ? "movie" : "tv"}-${tmdb}`;
}

export function posterKeyIsValid(key: string): boolean {
  return KEY.test(key);
}

/**
 * The posters in one catalog directory.
 *
 * The listing is taken once, when the catalog is opened: the directory only
 * changes when a refresh replaces the whole catalog, and that builds a new
 * store. A missing poster is a blank card, never an error.
 */
export class PosterStore {
  private readonly available: Set<string>;

  constructor(private readonly dir: string | null) {
    this.available = new Set();
    if (dir === null) return;
    let names: string[];
    try {
      names = readdirSync(join(dir, "posters"));
    } catch {
      // A package without artwork is a normal package.
      return;
    }
    for (const name of names) {
      const key = name.replace(/\.jpg$/, "");
      if (name.endsWith(".jpg") && posterKeyIsValid(key)) this.available.add(key);
    }
  }

  has(key: string | null): boolean {
    return key !== null && this.available.has(key);
  }

  count(): number {
    return this.available.size;
  }

  /** The bytes of one poster, or `null`. */
  read(key: string): Uint8Array | null {
    if (this.dir === null || !this.has(key)) return null;
    try {
      return new Uint8Array(readFileSync(join(this.dir, "posters", `${key}.jpg`)));
    } catch {
      return null;
    }
  }
}
