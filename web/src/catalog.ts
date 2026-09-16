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
  season: number | null;
  episode: string | null;
  year: number | null;
  container: string;
  vcodec: string | null;
  acodec: string | null;
  duration: number | null;
  total: number;
  partCount: number;
}

/** Where one part lives. Server-side only. */
export interface PartLocation {
  span: PartSpan;
  chatId: number;
  messageId: number;
}

const COLUMNS = `set_id AS setId, kind, title, show, chap, season, episode, year,
     container, vcodec, acodec, duration, total, part_count AS partCount`;

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
