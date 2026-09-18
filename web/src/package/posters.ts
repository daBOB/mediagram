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

import { existsSync, readFileSync, readdirSync } from "node:fs";
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
 * Asked of the filesystem each time rather than listed once. A package's
 * artwork arrives all at once and never moves, which is what a cached listing
 * was built for — but the same store now serves a local library, where
 * `mediagram posters` adds artwork to a directory a player is already running
 * against, and a listing taken at startup would hide it until a restart.
 *
 * Caching bought nothing here anyway: `has` is asked once per title when the
 * catalog is built, so a cache guarded by the directory's mtime costs one
 * `stat` per title to save one `readdir` per request. A missing poster is a
 * blank card, never an error.
 */
export class PosterStore {
  constructor(private readonly dir: string | null) {}

  /** Where one poster would be, or `null` when there is nowhere to look. */
  private pathOf(key: string): string | null {
    if (this.dir === null || !posterKeyIsValid(key)) return null;
    return join(this.dir, "posters", `${key}.jpg`);
  }

  has(key: string | null): boolean {
    if (key === null) return false;
    const path = this.pathOf(key);
    return path !== null && existsSync(path);
  }

  /** How many are held. Asked once, for the line the player logs at startup. */
  count(): number {
    if (this.dir === null) return 0;
    try {
      return readdirSync(join(this.dir, "posters")).filter(
        (name) => name.endsWith(".jpg") && posterKeyIsValid(name.slice(0, -4)),
      ).length;
    } catch {
      // A library with no artwork is a normal library.
      return 0;
    }
  }

  /** The bytes of one poster, or `null`. */
  read(key: string): Uint8Array | null {
    const path = this.pathOf(key);
    if (path === null) return null;
    try {
      return new Uint8Array(readFileSync(path));
    } catch {
      // Absent, or removed between the check and the read.
      return null;
    }
  }
}
