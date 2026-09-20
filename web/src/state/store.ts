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
import { normalName, SYNC_FORMAT, type SyncRecord } from "./sync-record";
import type { MergedState } from "./merge";

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
  /**
   * What this viewer chose, by scope and name.
   *
   * Sent whole rather than asked for per title: there are a handful of these
   * per show and the page needs one the instant a title opens, which is
   * exactly when it has no time to ask.
   */
  preferences: Preference[];
}

/** One remembered choice. See the v5 migration for why it is shaped this way. */
export interface Preference {
  scope: string;
  name: string;
  value: string;
}

/** How long a name may be. Long enough for a sentence, short enough to show. */
const MAX_NAME = 120;

/**
 * How long a preference's three strings may be.
 *
 * A scope is a show's key or its name, a name is a word this player chose,
 * and a value is a language tag or a number. None of them is prose, and a
 * cap is what stops a client filling the database with one row.
 */
const MAX_PREFERENCE = 200;

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
    if (!this.db) {
      return { progress: [], watchlist: [], collections: [], watched: [], preferences: [] };
    }

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

    const preferences = this.db
      .query("SELECT scope, name, value FROM preferences WHERE profile_id = ?1")
      .all(profileId) as Preference[];

    return { progress, watchlist, collections, watched, preferences };
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
   * Remembers a choice, or forgets it.
   *
   * An empty value forgets, rather than storing an empty string that every
   * reader would then have to recognise as meaning nothing.
   *
   * Everything is length-capped and nothing is interpreted. This does not
   * know what an audio track or a subtitle offset is, and should not: a
   * preference the file no longer supports has to be survivable, so the
   * meaning lives in the reader that applies it and the failure is a choice
   * quietly ignored rather than a title that will not open.
   */
  setPreference(profileId: string, scope: unknown, name: unknown, value: unknown): boolean {
    // A player with nowhere to write did not remember it, and saying it did
    // would have the page show a choice that is gone on the next title.
    if (!this.db) return false;
    const at = short(scope);
    const called = short(name);
    if (at === null || called === null) return false;

    const held = short(value);
    if (held === null) {
      this.db
        ?.query("DELETE FROM preferences WHERE profile_id = ?1 AND scope = ?2 AND name = ?3")
        .run(profileId, at, called);
      return true;
    }

    tolerate(() =>
      this.db
        ?.query(
          `INSERT INTO preferences(profile_id, scope, name, value, updated_at)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(profile_id, scope, name)
               DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
        )
        .run(profileId, at, called, held, Date.now()),
    );
    return true;
  }

  /**
   * This install's name on the channel, minted once and kept.
   *
   * Not the hostname. A rebuilt machine with the same name is a different
   * device and should not inherit the old one's document; two machines that
   * happened to share a name would write over each other's.
   */
  deviceId(): string {
    const held = this.meta("device_id");
    if (held !== null) return held;
    const made = crypto.randomUUID();
    this.setMeta("device_id", made);
    // A player with nowhere to write still needs to call itself something for
    // the length of this run, or its own document looks like a stranger's.
    return this.meta("device_id") ?? made;
  }

  private meta(key: string): string | null {
    try {
      const row = this.db?.query("SELECT value FROM state_meta WHERE key = ?1").get(key) as
        | { value?: string }
        | null;
      return typeof row?.value === "string" ? row.value : null;
    } catch {
      return null;
    }
  }

  private setMeta(key: string, value: string): void {
    tolerate(() =>
      this.db
        ?.query("INSERT OR REPLACE INTO state_meta(key, value) VALUES (?1, ?2)")
        .run(key, value),
    );
  }

  /**
   * What this player has to say about where things were left off.
   *
   * Every profile, because a document belongs to a device rather than to
   * whoever happens to be watching on it; and with timestamps, which
   * `snapshot` drops — it is built for a page that only needs to know *what*,
   * and a merge has to know *when*.
   */
  exportRecord(device: string): SyncRecord {
    const profiles = this.profiles().map((profile) => ({
      name: profile.name,
      localId: profile.id,
      progress: (this.db
        ?.query(
          `SELECT set_id AS setId, at_seconds AS at, duration, updated_at AS updatedAt
             FROM progress WHERE profile_id = ?1`,
        )
        .all(profile.id) ?? []) as SyncRecord["profiles"][number]["progress"],
      watched: (this.db
        ?.query("SELECT set_id AS setId, finished_at AS updatedAt FROM watched WHERE profile_id = ?1")
        .all(profile.id) ?? []) as SyncRecord["profiles"][number]["watched"],
    }));
    return { format: SYNC_FORMAT, device, writtenAt: Date.now(), profiles };
  }

  /**
   * Takes in what the devices agreed on.
   *
   * **Corrective, never wholesale.** A row absent from the merge is left
   * alone rather than deleted: a sync that reached only some of the devices
   * would otherwise erase everything the missing ones knew. The only thing
   * that removes a position is a completion that supersedes it, which is the
   * one removal the format can actually express.
   *
   * Returns how many rows it changed, so a caller can tell a merge that did
   * something from one that did not.
   */
  importMerged(merged: MergedState): number {
    if (!this.db) return 0;
    let changed = 0;

    for (const profile of merged.profiles) {
      // The identity to match on, and the spelling to create with.
      const profileId = this.profileNamed(profile.name, profile.displayName);
      if (profileId === null) continue;

      for (const row of profile.progress) {
        const standing = this.db
          .query("SELECT updated_at AS updatedAt FROM progress WHERE profile_id = ?1 AND set_id = ?2")
          .get(profileId, row.setId) as { updatedAt: number } | null;
        if (standing !== null && standing.updatedAt >= row.updatedAt) continue;
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
            .run(profileId, row.setId, row.at, row.duration, row.updatedAt),
        );
        changed += 1;
      }

      for (const row of profile.watched) {
        const standing = this.db
          .query("SELECT finished_at AS updatedAt FROM watched WHERE profile_id = ?1 AND set_id = ?2")
          .get(profileId, row.setId) as { updatedAt: number } | null;
        // The position goes whether or not the completion itself is news: a
        // device that learns of a completion it already had may still be
        // holding the position another device has only now told it about.
        const dropped = this.db
          .query("DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2 AND updated_at <= ?3")
          .run(profileId, row.setId, row.updatedAt);
        changed += dropped.changes;
        if (standing !== null && standing.updatedAt >= row.updatedAt) continue;
        tolerate(() =>
          this.db
            ?.query(
              `INSERT INTO watched(profile_id, set_id, finished_at) VALUES (?1, ?2, ?3)
                 ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at`,
            )
            .run(profileId, row.setId, row.updatedAt),
        );
        changed += 1;
      }
    }
    return changed;
  }

  /**
   * This player's id for a viewer, made if it has never seen them.
   *
   * Made rather than skipped, because the first thing a second machine knows
   * about a viewer is a document written by the first — refusing to create
   * one would mean the sync could only ever flow towards a machine that had
   * already met them.
   */
  private profileNamed(name: string, displayName?: string): string | null {
    const wanted = normalName(name);
    if (wanted === null) return null;
    const found = this.profiles().find((profile) => normalName(profile.name) === wanted);
    // Created from the spelling somebody typed, never from the normalised
    // identity — that would greet a viewer as "andré" on every new machine.
    return found ? found.id : (this.createProfile(displayName ?? name)?.id ?? null);
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

/**
 * One of a preference's three strings, trimmed and capped.
 *
 * Deliberately not `cleanName`: that collapses runs of whitespace, which is
 * right for something a person typed and wrong for a value this player wrote
 * and will parse back.
 */
function short(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim().slice(0, MAX_PREFERENCE);
  return clean === "" ? null : clean;
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
