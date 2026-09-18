/**
 * What the player may offer, and where a set's bytes live.
 *
 * "Playable" is not redefined here. The uploader already says what it means —
 * complete, every part done, lengths summing to the recorded total — and the
 * player asks that question rather than forming a second opinion. A second
 * opinion is how a catalog ends up offering titles that stall halfway through.
 *
 * `PLAYABLE_SQL` below is a copy of `mlib_spec::schema::PLAYABLE_SQL`, and
 * `crates/mediagram/tests/shared_playable_sql.rs` fails if the two stop
 * matching. Edit the Rust constant, not this one.
 */

import type { Database } from "bun:sqlite";
import type { PartSpan } from "./range";

/**
 * The index layout this build reads.
 *
 * Kept in step with `mlib_spec::schema::SCHEMA_VERSION` by
 * `crates/mediagram/tests/shared_playable_sql.rs`, which fails if the two
 * drift — the player reads the uploader's database and cannot migrate it.
 */
export const EXPECTED_SCHEMA = 6;

/**
 * Refuses an index written by an older uploader.
 *
 * The player opens the index read-only and must not migrate what the uploader
 * owns. Saying so beats failing three calls later inside a query with "no
 * such column", which is what a missing migration actually looks like.
 */
export function assertSchema(db: Database): void {
  const row = db
    .query("SELECT value FROM meta WHERE key = 'schema_version'")
    .get() as { value: string } | null;
  const found = Number(row?.value ?? 0);

  if (!Number.isFinite(found) || found < EXPECTED_SCHEMA) {
    throw new Error(
      `this index is at schema v${found || "unknown"}, and the player needs v${EXPECTED_SCHEMA}. ` +
        "Run any writing mediagram command once (`mediagram verify --all` will do) to migrate it.",
    );
  }
}

export const PLAYABLE_SQL = `s.status = 'complete'
    AND s.part_count = (SELECT COUNT(*) FROM parts p WHERE p.set_id = s.set_id AND p.status = 'done')
    AND s.total = (SELECT COALESCE(SUM(byte_length), 0) FROM parts p WHERE p.set_id = s.set_id)`;

/**
 * One title the player can offer.
 *
 * Deliberately carries no `chatId`, `messageId` or `docId`. This is what the
 * browser is served, and where a set's bytes live is the secret the package
 * format exists to protect.
 */
export interface PlayableSet {
  setId: string;
  kind: string;
  title: string | null;
  show: string | null;
  chap: string | null;
  /** Folders within the collection, `/`-separated. See the caption spec. */
  path: string | null;
  season: number | null;
  episode: string | null;
  year: number | null;
  container: string;
  vcodec: string | null;
  acodec: string | null;
  /** Resolution label, e.g. `1080p`. */
  quality: string | null;
  /** `SDR`, `HDR10`, `HLG` or `DV`. */
  hdr: string | null;
  /** Audio languages as a JSON array, the way the index stores them. */
  alang: string | null;
  /** Subtitle languages the file itself carries, as a JSON array. */
  slang: string | null;
  duration: number | null;
  total: number;
  partCount: number;
  /** The provider id the artwork is filed under. Server-side only. */
  tmdb: number | null;
}

/** Where one part lives. Server-side only. */
export interface PartLocation {
  span: PartSpan;
  chatId: number;
  messageId: number;
}

const COLUMNS = `set_id AS setId, kind, title, show, chap, path, season, episode, year,
     container, vcodec, acodec, quality, hdr, alang, slang, duration, total,
     part_count AS partCount, tmdb`;

/**
 * Every playable set with the text a search reads, summaries included.
 *
 * Built once: the catalog is read-only for the life of the process, so the
 * search index this feeds can be folded at startup and never invalidated.
 */
export function listSearchable(db: Database): Array<PlayableSet & { summary: string | null }> {
  return db
    .query(
      `SELECT ${COLUMNS},
              (SELECT body FROM assets a
                WHERE a.set_id = s.set_id AND a.kind = 'summary' AND a.lang = '') AS summary
         FROM sets s WHERE ${PLAYABLE_SQL}`,
    )
    .all() as Array<PlayableSet & { summary: string | null }>;
}

export function listPlayable(db: Database): PlayableSet[] {
  return db
    .query(`SELECT ${COLUMNS} FROM sets s WHERE ${PLAYABLE_SQL} ORDER BY created_at DESC`)
    .all() as PlayableSet[];
}

/**
 * One set, if the player may play it. Asks the same question as
 * {@link listPlayable}, so a set can never be listed but not streamable, or
 * the other way round.
 */
export function playableSet(db: Database, setId: string): PlayableSet | null {
  return db
    .query(`SELECT ${COLUMNS} FROM sets s WHERE s.set_id = ?1 AND ${PLAYABLE_SQL}`)
    .get(setId) as PlayableSet | null;
}

/**
 * A set's parts in order, with the message each one lives in. Only `done`
 * parts: a part without a message has no bytes to serve.
 */
export function partLocations(db: Database, setId: string): PartLocation[] {
  const rows = db
    .query(
      `SELECT idx, byte_offset AS off, byte_length AS len, chat_id AS chatId, message_id AS messageId
       FROM parts
       WHERE set_id = ?1 AND status = 'done'
         AND chat_id IS NOT NULL AND message_id IS NOT NULL
       ORDER BY idx`,
    )
    .all(setId) as { idx: number; off: number; len: number; chatId: number; messageId: number }[];

  return rows.map((row) => ({
    span: { idx: row.idx, off: row.off, len: row.len },
    chatId: row.chatId,
    messageId: row.messageId,
  }));
}
