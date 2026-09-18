/**
 * What the provider says about a show, as opposed to about a file.
 *
 * Read from the `shows` table the uploader fills, which an older index simply
 * does not have. Every read tolerates that: a library from before the table
 * existed shows no description, which is what it showed yesterday.
 */

import type { Database } from "bun:sqlite";

export interface ShowMeta {
  overview: string | null;
  tagline: string | null;
  /** Comma-separated, in the order the provider lists them. */
  genres: string | null;
  rating: number | null;
  network: string | null;
  status: string | null;
  firstAir: string | null;
  lastAir: string | null;
  /** What the provider says exists, as against what the index holds. */
  totalSeasons: number | null;
  totalEpisodes: number | null;
}

/** A poster key: `tmdb-tv-1396` or `tmdb-movie-550`. */
const KEY = /^(tmdb)-(movie|tv)-(\d{1,12})$/;

/**
 * One show's description, or `null` when there is none — which covers a key
 * that names nothing, an index predating the table, and a title the provider
 * had nothing to say about. None of those is an error worth a 500.
 */
export function showMeta(db: Database, key: string): ShowMeta | null {
  const parts = KEY.exec(key);
  if (!parts) return null;
  const [, source, kind, id] = parts;
  try {
    const row = db
      .query(
        `SELECT overview, tagline, genres, rating, network, status,
                first_air AS firstAir, last_air AS lastAir,
                total_seasons AS totalSeasons, total_episodes AS totalEpisodes
           FROM shows WHERE source = ?1 AND kind = ?2 AND id = ?3`,
      )
      .get(source!, kind!, Number(id)) as ShowMeta | null;
    return row ?? null;
  } catch {
    // No such table: an index written before this existed.
    return null;
  }
}
