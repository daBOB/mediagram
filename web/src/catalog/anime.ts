/**
 * Whether a title is anime: Japanese animation, or a hand-set override.
 *
 * Kept apart from `shows.ts` because the rule itself is shared verbatim with
 * the Rust core's port for the Android app (`crates/mediagram-core/src/shows/anime.rs`) — one
 * small pure function is easy to keep the two languages honest about, a
 * whole projection module is not. The fixture both read is
 * `web/test/fixtures/anime/cases.json`.
 *
 * The genre check is by name, not TMDB id: this library is `de-DE`, and TMDB
 * names genre 16 "Animation" the same in English, German and French. A
 * library in another language does not match — see the fixture's last case —
 * and is the one ceiling this rule has; store genre ids if that ever bites.
 */

import type { Database } from "bun:sqlite";

/** Only these two kinds ever carry a provider entry an override can key. */
type AnimeKind = "movie" | "ep";

/**
 * @param kind the catalog set's own kind; only a film or an episode can be anime
 * @param genres the title's provider genres, in the library's language
 * @param originalLanguage TMDB's code for the title, or `null` on an older index
 * @param forced the hand-set override for this title, or `null` for "automatic"
 */
export function isAnime(
  kind: string,
  genres: readonly string[],
  originalLanguage: string | null,
  forced: boolean | null,
): boolean {
  if (kind !== "movie" && kind !== "ep") return false;
  if (forced !== null) return forced;
  return originalLanguage === "ja" && genres.includes("Animation");
}

/** A poster key: `tmdb-movie-603` or `tmdb-tv-1396`. */
type OverrideKey = string;

/**
 * Every hand-set anime decision, by the same key `providerFactsByShow` uses
 * (`tmdb-${kind}-${id}`) — `kind` here is the `shows` table's own spelling
 * (`movie`/`tv`), not the catalog set's (`movie`/`ep`).
 *
 * `NULL` rows ("back to automatic") are dropped: a map with no entry for a
 * title already means "no override", so keeping them would only cost every
 * lookup a wasted check for a value that means the same as absent.
 */
export function animeOverrides(db: Database): Map<OverrideKey, boolean> {
  const overrides = new Map<OverrideKey, boolean>();
  const exists = db
    .query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'anime_overrides'")
    .get();
  if (!exists) return overrides; // No such table: an index written before v11.
  const rows = db
    .query(
      `SELECT kind, id, anime FROM anime_overrides WHERE source = 'tmdb' AND anime IS NOT NULL`,
    )
    .all() as { kind: string; id: number; anime: number }[];
  for (const row of rows) overrides.set(`tmdb-${row.kind}-${row.id}`, row.anime !== 0);
  return overrides;
}
