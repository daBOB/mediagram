/**
 * Watch state: positions, the watchlist, and hand-built collections.
 *
 * Synchronous throughout, because `bun:sqlite` is and because every one of
 * these is a single indexed row — wrapping them in promises would buy nothing
 * and would put a scheduling boundary between a viewer pressing pause and the
 * position being written.
 *
 * Tolerant throughout, too. A state directory that cannot be written is a
 * player that forgets where you were, which is a bad afternoon; a player that
 * refuses to start because of it is a broken one. Every method degrades to
 * "remembers nothing" rather than throwing into a request handler.
 */

import { Database } from "bun:sqlite";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";

import { GROUPS } from "./schema";

export interface Progress {
  setId: string;
  at: number;
  duration: number | null;
  updatedAt: number;
}

export interface Collection {
  id: string;
  name: string;
  createdAt: number;
  items: string[];
}

export interface Profile {
  id: string;
  name: string;
  createdAt: number;
}

export interface StateSnapshot {
  progress: Progress[];
  watchlist: string[];
  collections: Collection[];
  /** Watched to the end. Kept because finishing clears the position. */
  watched: string[];
}

/** How long a name may be. Long enough for a sentence, short enough to show. */
const MAX_NAME = 120;

export class WatchState {
  private readonly db: Database | null;

  /**
   * Opens, creating the file and its directory if they are not there.
   *
   * `null` means this player does not remember anything, which is a
   * legitimate way to run one and is what a failure here degrades to.
   */
  constructor(path: string | null) {
    this.db = path === null ? null : open(path);
  }

  /** Whether anything written here will actually be kept. */
  get remembers(): boolean {
    return this.db !== null;
  }

  /** Who watches this library. Empty until someone says. */
  profiles(): Profile[] {
    if (!this.db) return [];
    return this.db
      .query("SELECT id, name, created_at AS createdAt FROM profiles ORDER BY created_at")
      .all() as Profile[];
  }

  createProfile(name: unknown): Profile | null {
    if (!this.db) return null;
    const clean = cleanName(name);
    if (clean === null) return null;

    const profile = { id: crypto.randomUUID(), name: clean, createdAt: Date.now() };
    this.db
      .query("INSERT INTO profiles(id, name, created_at) VALUES (?1, ?2, ?3)")
      .run(profile.id, profile.name, profile.createdAt);
    return profile;

  }

  renameProfile(id: string, name: unknown): boolean {
    if (!this.db) return false;
    const clean = cleanName(name);
    if (clean === null) return false;
    return this.db.query("UPDATE profiles SET name = ?2 WHERE id = ?1").run(id, clean).changes > 0;
  }

  /** Takes everything that was theirs with it — every table cascades. */
  deleteProfile(id: string): boolean {
    if (!this.db) return false;
    return this.db.query("DELETE FROM profiles WHERE id = ?1").run(id).changes > 0;
  }

  has(profileId: string): boolean {
    if (!this.db) return false;
    return this.db.query("SELECT 1 FROM profiles WHERE id = ?1").get(profileId) !== null;
  }

  /**
   * The set ids a query of one `set_id AS setId` column answers.
   *
   * Four readers wanted the same four lines of cast-and-map ceremony around
   * their one interesting line of SQL. Empty on a player that cannot
   * remember, like everything else here.
   */
  private setIds(sql: string, ...params: (string | number)[]): string[] {
    if (!this.db) return [];
    return (this.db.query(sql).all(...params) as { setId: string }[]).map((row) => row.setId);
  }

  /** One profile's everything, in one read. The page asks once and holds it. */
  snapshot(profileId: string): StateSnapshot {
    if (!this.db) return { progress: [], watchlist: [], collections: [], watched: [] };

    const progress = this.db
      .query(
        `SELECT set_id AS setId, at_seconds AS at, duration, updated_at AS updatedAt
           FROM progress WHERE profile_id = ?1 ORDER BY updated_at DESC`,
      )
      .all(profileId) as Progress[];

    const watchlist = this.setIds(
      "SELECT set_id AS setId FROM watchlist WHERE profile_id = ?1 ORDER BY added_at DESC",
      profileId,
    );

    const collections = (
      this.db
        .query(
          `SELECT id, name, created_at AS createdAt FROM collections
            WHERE profile_id = ?1 ORDER BY created_at`,
        )
        .all(profileId) as Omit<Collection, "items">[]
    ).map((row) => ({
      ...row,
      items: this.setIds(
        "SELECT set_id AS setId FROM collection_items WHERE collection_id = ?1 ORDER BY position",
        row.id,
      ),
    }));

    const watched = this.setIds(
      "SELECT set_id AS setId FROM watched WHERE profile_id = ?1",
      profileId,
    );

    return { progress, watchlist, collections, watched };
  }

  /** Where this profile is in `setId`. */
  setProgress(profileId: string, setId: string, at: number, duration: number | null): void {
    tolerate(() =>
      this.db
        ?.query(
        `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5)
           ON CONFLICT(profile_id, set_id) DO UPDATE SET
             at_seconds = excluded.at_seconds,
             duration = excluded.duration,
             updated_at = excluded.updated_at`,
      )
        .run(profileId, setId, Math.max(0, at), duration, Date.now()),
    );
  }

  /** Forgets a position: started again, or watched to the end. */
  clearProgress(profileId: string, setId: string): void {
    this.db?.query("DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2").run(profileId, setId);
  }

  setWatchlisted(profileId: string, setId: string, listed: boolean): void {
    if (listed) {
      tolerate(() =>
        this.db
          ?.query("INSERT OR IGNORE INTO watchlist(profile_id, set_id, added_at) VALUES (?1, ?2, ?3)")
          .run(profileId, setId, Date.now()),
      );
    } else {
      this.db
        ?.query("DELETE FROM watchlist WHERE profile_id = ?1 AND set_id = ?2")
        .run(profileId, setId);
    }
  }

  /**
   * Records that a title was watched to the end, or takes it back.
   *
   * The position is cleared at the same moment — a finished title has no
   * resume point — so this is the only thing that survives it. Without it the
   * Continue shelf would be right and everything else would believe the title
   * had never been opened.
   */
  setWatched(profileId: string, setId: string, finished: boolean): void {
    if (finished) {
      tolerate(() =>
        this.db
          ?.query(
            `INSERT INTO watched(profile_id, set_id, finished_at) VALUES (?1, ?2, ?3)
               ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at`,
          )
          .run(profileId, setId, Date.now()),
      );
    } else {
      this.db
        ?.query("DELETE FROM watched WHERE profile_id = ?1 AND set_id = ?2")
        .run(profileId, setId);
    }
  }

  /**
   * The titles marked as a child's, for everyone on this player.
   *
   * Not scoped to a profile, unlike everything else here: see the v3
   * migration. Ordered newest first, the way the watchlist is, so the shelf
   * shows what was just marked at the top.
   */
  kids(): string[] {
    return this.setIds("SELECT set_id AS setId FROM kids ORDER BY marked_at DESC");
  }

  setKids(setId: string, marked: boolean): void {
    if (marked) {
      tolerate(() =>
        this.db
          ?.query("INSERT OR IGNORE INTO kids(set_id, marked_at) VALUES (?1, ?2)")
          .run(setId, Date.now()),
      );
    } else {
      this.db?.query("DELETE FROM kids WHERE set_id = ?1").run(setId);
    }
  }

  /** A new, empty list. Returns it, so the page need not re-read everything. */
  createCollection(profileId: string, name: unknown): Collection | null {
    if (!this.db) return null;
    const clean = cleanName(name);
    if (clean === null) return null;

    const id = crypto.randomUUID();
    const createdAt = Date.now();
    let made = false;
    tolerate(() => {
      this.db!
        .query("INSERT INTO collections(id, profile_id, name, created_at) VALUES (?1, ?2, ?3, ?4)")
        .run(id, profileId, clean, createdAt);
      made = true;
    });
    return made ? { id, name: clean, createdAt, items: [] } : null;
  }

  /** Whether the list is there to rename, so a caller can answer 404. */
  renameCollection(profileId: string, id: string, name: unknown): boolean {
    if (!this.db) return false;
    const clean = cleanName(name);
    if (clean === null) return false;
    return (
      this.db
        .query("UPDATE collections SET name = ?3 WHERE id = ?2 AND profile_id = ?1")
        .run(profileId, id, clean).changes > 0
    );
  }

  /** Items go with it: `collection_items` cascades. */
  deleteCollection(profileId: string, id: string): boolean {
    if (!this.db) return false;
    return (
      this.db
        .query("DELETE FROM collections WHERE id = ?2 AND profile_id = ?1")
        .run(profileId, id).changes > 0
    );
  }

  /**
   * Adds `setId` to the end of a list, or leaves it where it already is.
   *
   * Idempotent on purpose: the button that calls this is next to a title that
   * may well be in the list already, and adding twice should not make a list
   * that holds something twice.
   */
  addToCollection(profileId: string, id: string, setId: string): boolean {
    if (!this.db) return false;
    // Scoped, so a list belonging to someone else is simply not there.
    const exists = this.db
      .query("SELECT 1 FROM collections WHERE id = ?2 AND profile_id = ?1")
      .get(profileId, id);
    if (!exists) return false;

    const last = this.db
      .query("SELECT COALESCE(MAX(position), -1) AS last FROM collection_items WHERE collection_id = ?1")
      .get(id) as { last: number };
    this.db
      .query(
        `INSERT INTO collection_items(collection_id, set_id, position) VALUES (?1, ?2, ?3)
           ON CONFLICT(collection_id, set_id) DO NOTHING`,
      )
      .run(id, setId, last.last + 1);
    return true;
  }

  removeFromCollection(profileId: string, id: string, setId: string): boolean {
    if (!this.db) return false;
    return (
      this.db
        .query(
          `DELETE FROM collection_items
            WHERE collection_id = ?2 AND set_id = ?3
              AND EXISTS(SELECT 1 FROM collections c
                          WHERE c.id = ?2 AND c.profile_id = ?1)`,
        )
        .run(profileId, id, setId).changes > 0
    );
  }

  close(): void {
    this.db?.close();
  }
}

/**
 * Runs a write, and swallows a refusal.
 *
 * The refusal worth expecting is a foreign key: a profile deleted on one
 * device while another is still watching means positions arriving for a
 * profile that is no longer there. That is not an error a viewer can act on
 * and not one worth ending a request over — the write simply has nowhere to
 * go. Reads are already safe; only mutations pass through here.
 */
function tolerate(write: () => void): void {
  try {
    write();
  } catch {
    /* Nowhere to put it. See above. */
  }
}

/** A name with its edges trimmed, or `null` when there is nothing left. */
function cleanName(name: unknown): string | null {
  if (typeof name !== "string") return null;
  const clean = name.trim().replace(/\s+/g, " ").slice(0, MAX_NAME);
  return clean === "" ? null : clean;
}

/**
 * Opens the state database, or returns `null` having said why.
 *
 * Said rather than thrown: this runs at startup, and a player that will not
 * start because it could not create a directory for watch positions has
 * traded the whole library for a convenience.
 */
function open(path: string): Database | null {
  try {
    mkdirSync(dirname(path), { recursive: true });
    const db = new Database(path, { create: true });
    // Survives a kill without taking the file with it, and lets a reader on
    // another device's request run while a position is being written.
    db.exec("PRAGMA journal_mode = WAL");
    migrate(db);
    db.exec("PRAGMA foreign_keys = ON");
    return db;
  } catch (error) {
    const why = error instanceof Error ? error.message : String(error);
    console.warn(`state: not remembering anything (${why})`);
    return null;
  }
}

/**
 * Applies only the migrations this file has not had.
 *
 * Which is the whole reason they are grouped by the version they produce.
 * Replaying all of them on every open survives a schema built entirely from
 * `CREATE TABLE IF NOT EXISTS`, and destroys one that rebuilds a table: the
 * second open copies the live rows into a fresh table, drops the original and
 * renames the copy over it, which reads as working right up until someone
 * looks at whose rows they now are.
 *
 * Each group runs in a transaction, so a migration that fails half way leaves
 * the version it started at rather than a shape matching no version at all.
 */
function migrate(db: Database): void {
  const at = versionOf(db);
  // Off for the length of a migration: rebuilding a table means dropping one
  // that another references, which enforcement refuses. This is the order
  // SQLite's own guidance gives.
  db.exec("PRAGMA foreign_keys = OFF");

  for (const [index, group] of GROUPS.entries()) {
    const version = index + 1;
    if (version <= at) continue;

    db.exec("BEGIN");
    try {
      for (const statement of group) db.exec(statement);
      db.query("INSERT OR REPLACE INTO state_meta(key, value) VALUES ('schema_version', ?1)").run(
        String(version),
      );
      db.exec("COMMIT");
    } catch (error) {
      db.exec("ROLLBACK");
      throw error;
    }
  }
}

/** The version this file is at, or 0 for one that has never been touched. */
function versionOf(db: Database): number {
  try {
    const row = db.query("SELECT value FROM state_meta WHERE key = 'schema_version'").get() as
      | { value: string }
      | null;
    return Number(row?.value ?? 0) || 0;
  } catch {
    // No `state_meta` at all, which is what an empty file looks like.
    return 0;
  }
}
