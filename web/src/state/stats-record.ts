/**
 * Viewing stats on the sync record: two new keys on a profile, `titleStats`
 * and `dayStats`, and reading them as if a stranger wrote them.
 *
 * New keys rather than a `SYNC_FORMAT` bump, for the reason `sync-record.ts`
 * gives for `unwatched`: a reader that predates them drops what it does not
 * know and goes on merging everything else, where a bumped format would make
 * it refuse the whole document.
 *
 * **Every row names the device that counted it.** Each device writes only its
 * own rows and passes on everyone else's unchanged, so a total is a sum over
 * devices of rows each one owns — two documents carrying the same row can
 * never count it twice.
 *
 * Stricter than the older rows' parsing: a number must be a JSON number.
 * Those coerce numeric strings because documents from before they were
 * checked carry them; nothing ever wrote these keys that way.
 */

/** One title, as one device counted it. Times are epoch ms. */
export interface TitleStatRow {
  setId: string;
  device: string;
  startedAt: number;
  lastWatchedAt: number;
  seconds: number;
  /** When this device last started the title over after finishing it. */
  againAt?: number;
  updatedAt: number;
}

/** One local day (`YYYY-MM-DD`, the counting device's own date), as one device counted it. */
export interface DayStatRow {
  day: string;
  device: string;
  seconds: number;
  updatedAt: number;
}

/** The two keys as a profile carries them: each omitted when it has no rows. */
export interface StatsRows {
  titleStats?: TitleStatRow[];
  dayStats?: DayStatRow[];
}

import { objectRow, parseRows, text_ } from "./record-scalars";

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** One device cannot watch more than a day in a day. */
const MAX_DAY_SECONDS = 86_400;

/** A JSON number, finite and not negative, or `null`. */
function amountOf(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
}

/**
 * A time a row carries: an amount no later than `Number.MAX_SAFE_INTEGER`,
 * or `null`. A stamp past it is no clock's, and the core, which stores
 * stamps as 64-bit integers, would overflow its next own write's `+ 1` on one
 * near the top of that range.
 */
function stampOf(value: unknown): number | null {
  const at = amountOf(value);
  return at !== null && at <= Number.MAX_SAFE_INTEGER ? at : null;
}

function titleRow(value: unknown): TitleStatRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const setId = text_(raw.setId);
  const device = text_(raw.device);
  const startedAt = stampOf(raw.startedAt);
  const lastWatchedAt = stampOf(raw.lastWatchedAt);
  const seconds = amountOf(raw.seconds);
  const updatedAt = stampOf(raw.updatedAt);
  if (setId === null || device === null || startedAt === null || lastWatchedAt === null) return null;
  if (seconds === null || updatedAt === null) return null;
  const row = { setId, device, startedAt, lastWatchedAt, seconds, updatedAt };
  // `null` is no restart, the same as absent: a writer that serialises an
  // empty optional as `null` has said nothing, not something malformed.
  if (raw.againAt === undefined || raw.againAt === null) return row;
  const againAt = stampOf(raw.againAt);
  return againAt === null ? null : { ...row, againAt };
}

function dayRow(value: unknown): DayStatRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const day = typeof raw.day === "string" && DAY.test(raw.day) ? raw.day : null;
  const device = text_(raw.device);
  const seconds = amountOf(raw.seconds);
  const updatedAt = stampOf(raw.updatedAt);
  if (day === null || device === null || seconds === null || updatedAt === null) return null;
  return seconds > MAX_DAY_SECONDS ? null : { day, device, seconds, updatedAt };
}

/** Row by row: a bad row is dropped, never the document. Not an array → absent. */
export const parseTitleStatRows = (value: unknown) =>
  Array.isArray(value) ? parseRows(value, titleRow) : undefined;
export const parseDayStatRows = (value: unknown) =>
  Array.isArray(value) ? parseRows(value, dayRow) : undefined;
