/**
 * Fetching one aligned range of a part straight from Telegram.
 *
 * Split out of `source.ts`, which streams a whole set; this is the smaller
 * unit the chunk cache asks for when it has a miss to fill, and the only
 * place a stale file reference is retried.
 */

import { helpers } from "teleproto";
import type { Telegram } from "./client";
import type { TelegramConnection } from "./connection";
import { requestSizeFor } from "../range";
import { DownloadGate } from "./download-gate";

/** Whether a Telegram request failed because the file reference it used expired. */
export function isFileReferenceExpired(error: unknown): boolean {
  const message = (error as { errorMessage?: unknown } | null)?.errorMessage;
  return typeof message === "string" && message.startsWith("FILE_REFERENCE");
}

/**
 * One gate for the whole process: the limit is Telegram's, per account, so
 * every reader of every title shares it.
 */
const downloads = new DownloadGate();

/**
 * Handed to the cache as its way of filling a miss, so the cache knows
 * nothing about MTProto and this file stays the only place that does.
 */
export function partFetcher(telegram: Telegram, messageId: number) {
  return (offset: number, length: number): Promise<Uint8Array> =>
    downloads.run(() => fetchPart(telegram, messageId, offset, length));
}

/**
 * The same fetcher, but resolved from a connection at call time rather than
 * bound to one client: a chunk the cache asks for after a restart is fetched
 * with whatever client is current then, not the one that existed when the
 * stream started.
 */
export function connectionFetcher(connection: TelegramConnection, messageId: number) {
  return (offset: number, length: number): Promise<Uint8Array> =>
    downloads.run(() => fetchPartViaConnection(connection, messageId, offset, length));
}

async function fetchPartViaConnection(
  connection: TelegramConnection,
  messageId: number,
  offset: number,
  length: number,
): Promise<Uint8Array> {
  const startGeneration = connection.generation;
  const telegram = await connection.ready();
  if (!telegram) throw new Error("this part is not cached, and the player is signed out");
  try {
    return await fetchPartOnce(telegram, messageId, offset, length);
  } catch (error) {
    const swapped = connection.generation !== startGeneration;
    if (!isFileReferenceExpired(error) && !swapped) throw error;
    const fresh = swapped ? await connection.ready() : telegram;
    if (!fresh) throw new Error("this part is not cached, and the player is signed out");
    if (!swapped) fresh.forgetPartMedia(messageId);
    return fetchPartOnce(fresh, messageId, offset, length);
  }
}

async function fetchPart(
  telegram: Telegram,
  messageId: number,
  offset: number,
  length: number,
): Promise<Uint8Array> {
  // Collected into `out` before anything is handed back, so a reference that
  // expired mid-download can simply be retried from scratch: nothing has
  // reached a caller yet for the redo to duplicate or skip. Stays inside the
  // gate slot `partFetcher` already holds, so a retry cannot add concurrency.
  try {
    return await fetchPartOnce(telegram, messageId, offset, length);
  } catch (error) {
    if (!isFileReferenceExpired(error)) throw error;
    telegram.forgetPartMedia(messageId);
    return fetchPartOnce(telegram, messageId, offset, length);
  }
}

async function fetchPartOnce(
  telegram: Telegram,
  messageId: number,
  offset: number,
  length: number,
): Promise<Uint8Array> {
  const media = await telegram.partMedia(messageId);
  const out: Uint8Array[] = [];
  let got = 0;
  for await (const chunk of telegram.client.iterDownload(media as never, {
    offset: helpers.returnBigInt(offset) as never,
    requestSize: requestSizeFor(length),
  })) {
    out.push(chunk);
    got += chunk.length;
    if (got >= length) break;
  }
  const joined = new Uint8Array(got);
  let at = 0;
  for (const piece of out) {
    joined.set(piece, at);
    at += piece.length;
  }
  return joined.subarray(0, Math.min(length, got));
}
