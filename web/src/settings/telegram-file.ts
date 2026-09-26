/**
 * The account this player is bound to: api id/hash, session, and the chosen
 * channel — kept beside `state.db` rather than in it (plan's Q1: the state
 * database is copied and backed up casually, and holds no secret today).
 *
 * Read as absent on anything this file did not write itself: a missing file,
 * one that is not JSON, or one missing a required field. A player that
 * cannot make sense of its own secrets file should fall back to the
 * environment, not fail to start.
 */

import { chmod, mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { failureMessage } from "../failure-message";

export interface TelegramFile {
  apiId: number;
  apiHash: string;
  /** `null` means signed out; a key present with `null` wins over any env session. */
  session: string | null;
  chatId: number;
  /** Decimal, because a bigint does not survive `JSON.stringify`. */
  accessHash: string;
  /** The channel's title, shown read-only; not used for anything but display. */
  title: string | null;
}

/** The file's shape, checked field by field rather than trusted from `JSON.parse`. */
function parse(text: string): TelegramFile | null {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return null;
  }
  if (typeof raw !== "object" || raw === null) return null;
  const value = raw as Record<string, unknown>;
  if (typeof value.apiId !== "number" || !Number.isInteger(value.apiId)) return null;
  if (typeof value.apiHash !== "string" || value.apiHash === "") return null;
  if (value.session !== null && typeof value.session !== "string") return null;
  if (typeof value.chatId !== "number" || !Number.isInteger(value.chatId)) return null;
  if (typeof value.accessHash !== "string" || !/^-?\d+$/.test(value.accessHash)) return null;
  if (value.title !== null && typeof value.title !== "string") return null;
  return {
    apiId: value.apiId,
    apiHash: value.apiHash,
    session: value.session as string | null,
    chatId: value.chatId,
    accessHash: value.accessHash,
    title: (value.title as string | null) ?? null,
  };
}

/** The stored connection, or `null` when there is none to read (absent or unusable). */
export async function readTelegramFile(path: string): Promise<TelegramFile | null> {
  let text: string;
  try {
    text = await readFile(path, "utf8");
  } catch (error) {
    if ((error as { code?: string }).code === "ENOENT") return null;
    console.warn(`telegram.json: could not be read (${failureMessage(error)}); falling back to the environment`);
    return null;
  }
  const parsed = parse(text);
  if (parsed === null) {
    console.warn("telegram.json: malformed; falling back to the environment");
  }
  return parsed;
}

/**
 * Writes `value`, replacing whatever was there.
 *
 * tmp-then-rename, so a reader never sees a half-written file and a crash
 * mid-write leaves the previous version intact. Mode 0600 on the file and
 * 0700 on its directory: this is the one file on disk that carries a
 * session, which is the account itself.
 */
export async function writeTelegramFile(path: string, value: TelegramFile): Promise<void> {
  const dir = dirname(path);
  await mkdir(dir, { recursive: true, mode: 0o700 });
  await chmod(dir, 0o700).catch(() => {});
  const tmp = `${path}.${process.pid}.${Math.random().toString(36).slice(2)}.tmp`;
  await writeFile(tmp, JSON.stringify(value), { mode: 0o600 });
  await chmod(tmp, 0o600);
  try {
    await rename(tmp, path);
  } catch (error) {
    await rm(tmp, { force: true });
    throw error;
  }
}
