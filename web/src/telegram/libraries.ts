/**
 * The account's channels and groups, as choosable libraries.
 *
 * A "library" is Android's decided meaning, ported here rather than
 * reinvented (Surface Parity): a channel whose pins or history carry a
 * `#mlib-index` snapshot. This module only lists candidates; deciding which
 * one holds the newest snapshot is `pick-newest-index.ts`'s job, run against
 * whichever the viewer picks.
 */

import { Api } from "teleproto";
import type { Telegram } from "./client";

/** Core's own ceiling on how many dialogs a listing reads. */
const MOST_DIALOGS = 500;

export interface LibraryCandidate {
  title: string;
  chatId: number;
  accessHash: bigint;
}

/**
 * Channels this account is in, in dialog order (most recently active first).
 *
 * Groups are excluded: `Api.Channel` with `megagroup: true` is a supergroup,
 * and while a snapshot could technically be pinned there too, every channel
 * this project has ever pushed one to is a broadcast channel, and offering
 * every group in the account as a "library" would make the list mostly noise.
 */
export async function listLibraries(telegram: Telegram): Promise<LibraryCandidate[]> {
  const found: LibraryCandidate[] = [];
  let seen = 0;
  for await (const dialog of telegram.client.iterDialogs({})) {
    if (++seen > MOST_DIALOGS) break;
    const entity = dialog.entity;
    if (!(entity instanceof Api.Channel) || entity.megagroup === true) continue;
    const bare = BigInt(entity.id.toString());
    found.push({
      title: entity.title,
      chatId: Number(-1_000_000_000_000n - bare),
      accessHash: BigInt(entity.accessHash?.toString() ?? "0"),
    });
  }
  return found;
}
