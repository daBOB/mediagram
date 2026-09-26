/**
 * Who is in a title, which titles a person is in, and the film franchises —
 * read from the `credits` and `franchises` tables schema v9 added.
 *
 * An index older than v9 has neither table, and every read here answers
 * "nobody" rather than failing: the Cast tab and People results simply do
 * not appear until the uploader has written them.
 *
 * A person's titles are returned as the keys the catalog rows already carry
 * (`showKey`), never as titles: the page resolves them against the rows it
 * was allowed to see, so a kids profile is shown only the titles it can
 * already open.
 */

import type { Database } from "bun:sqlite";
import { terms, variants } from "../search/normalize";
import { personKeyFor } from "../package/posters";

export interface Credit {
  personId: number;
  name: string;
  /** The character played, or the job (`Director`, `Creator`). */
  role: string | null;
  /** A portrait key when this device holds the image, else `null`. */
  portrait: string | null;
}

export interface Person { personId: number; name: string; portrait: string | null; titles: string[] }
export interface Franchise { id: number; name: string; overview: string | null }

const KEY = /^tmdb-(movie|tv)-(\d{1,12})$/;
type Has = (key: string) => boolean;

function hasTable(db: Database, name: "credits" | "franchises"): boolean {
  return db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?1").get(name) !== null;
}

const portraitOf = (has: Has, personId: number) => (has(personKeyFor(personId)) ? personKeyFor(personId) : null);

/** A title's cast in billing order, and its directors or creators. */
export function creditsFor(db: Database, key: string, has: Has): { cast: Credit[]; crew: Credit[] } {
  const parts = KEY.exec(key);
  if (!parts || !hasTable(db, "credits")) return { cast: [], crew: [] };
  const rows = db.query(
    `SELECT person_id AS personId, name, role, dept FROM credits
      WHERE source = 'tmdb' AND kind = ?1 AND id = ?2 ORDER BY ord`,
  ).all(parts[1]!, Number(parts[2])) as { personId: number; name: string; role: string | null; dept: string }[];
  const shaped = (row: (typeof rows)[number]): Credit => ({
    personId: row.personId, name: row.name, role: row.role?.trim() || null, portrait: portraitOf(has, row.personId),
  });
  return {
    cast: rows.filter((row) => row.dept === "cast").map(shaped),
    crew: rows.filter((row) => row.dept !== "cast").map(shaped),
  };
}

/** One person and the keys of every title they are credited on. */
export function personFor(db: Database, personId: number, has: Has): Person | null {
  if (!hasTable(db, "credits")) return null;
  const rows = db.query(
    "SELECT kind, id, name FROM credits WHERE source = 'tmdb' AND person_id = ?1 ORDER BY id",
  ).all(personId) as { kind: string; id: number; name: string }[];
  if (rows.length === 0) return null;
  const titles = [...new Set(rows.map((row) => `tmdb-${row.kind}-${row.id}`))];
  return { personId, name: rows[0]!.name, portrait: portraitOf(has, personId), titles };
}

/** Every franchise the index names, by name. */
export function franchises(db: Database): Franchise[] {
  if (!hasTable(db, "franchises")) return [];
  return (db.query(
    "SELECT id, name, overview FROM franchises WHERE source = 'tmdb' ORDER BY name",
  ).all() as Franchise[]).map((row) => ({ ...row, overview: row.overview?.trim() || null }));
}

/**
 * People for search: built once per catalog, matched per query. A person is
 * found when every word of the query is in their name — either spelling of an
 * umlaut, as titles are matched — and the most-credited come first, so a lead
 * outranks a walk-on. Each carries its title keys, so the page can drop anyone
 * whose every title this profile cannot see.
 */
export function peopleSearch(db: Database, has: Has) {
  const people = hasTable(db, "credits")
    ? (db.query(
      `SELECT person_id AS personId, MIN(name) AS name,
              GROUP_CONCAT(DISTINCT 'tmdb-' || kind || '-' || id) AS keys
         FROM credits WHERE source = 'tmdb' GROUP BY person_id`,
    ).all() as { personId: number; name: string; keys: string }[])
      .map((row) => ({ personId: row.personId, name: row.name, titles: row.keys.split(","), forms: variants(row.name) }))
    : [];
  return (query: string, limit = 12) => {
    const words = terms(query);
    if (words.length === 0) return [];
    return people
      .filter((person) => words.every((word) => person.forms.some((form) => form.includes(word))))
      .sort((a, b) => b.titles.length - a.titles.length || a.name.localeCompare(b.name))
      .slice(0, limit)
      .map(({ personId, name, titles }) => ({ personId, name, titles, portrait: portraitOf(has, personId) }));
  };
}
