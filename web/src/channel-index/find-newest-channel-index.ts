/**
 * The newest index snapshot the channel holds, found on the player's own
 * client.
 *
 * Two lists, as core's `newest_index` reads them. The pin list is exact and
 * immediate — a message pinned a second ago is on it — and is where a healthy
 * channel keeps its index. A search for the marker finds the snapshots an
 * interrupted publish or a second machine left unpinned; it misses anything
 * freshly sent and also matches every part (Telegram reads `#mlib-index` as
 * the hashtag `#mlib`), which is why it only ever supplements the pins and is
 * allowed to fail.
 *
 * This is the one file here that speaks MTProto; the decision it hands to is
 * `pickNewestIndex`, which is tested against the fixtures core also reads.
 */

import { Api } from "teleproto";
import type { Telegram } from "../telegram/client";
import { INDEX_MARKER } from "../telegram/channel-captions";
import { pickNewestIndex, versionStamp, type NoIndex } from "./pick-newest-index";

/** Core's numbers: a hundred pins, fifty marker matches. */
const MOST_PINNED = 100;
const MOST_MARKED = 50;

export interface FoundIndex {
  messageId: number;
  /** When it was pushed; `now` stands in when the caption cannot be believed. */
  pushedAt: number;
  /** The document's bytes, fetched only when asked for. */
  chunks: () => AsyncIterable<Uint8Array>;
}

export async function findNewestChannelIndex(
  telegram: Telegram,
  now: number = Math.floor(Date.now() / 1000),
): Promise<FoundIndex | NoIndex> {
  const found = new Map<number, Api.Message>();

  // Independent reads over the same connection: the marker search does not
  // need the pinned list's result to start, or the other way around. Only
  // the pinned read is allowed to fail the whole call — the marker search
  // already tolerates missing the channel's own supplement to it.
  const pinnedRequest = telegram.client.getMessages(telegram.peer, {
    filter: new Api.InputMessagesFilterPinned(),
    limit: MOST_PINNED,
  });
  const markedRequest = telegram.client
    .getMessages(telegram.peer, { search: INDEX_MARKER, limit: MOST_MARKED })
    .catch(() => [] as Api.Message[]);

  const pinned = await pinnedRequest;
  for (const message of pinned) found.set(message.id, message);

  // The pins are the half that matters; a stale library is better than none,
  // which is why a failure here was already swallowed above rather than
  // awaited into a throw.
  const marked = await markedRequest;
  for (const message of marked) if (!found.has(message.id)) found.set(message.id, message);

  // Only the channel's own posts: a message a member slipped in is not a
  // snapshot anyone published.
  const posts = [...found.values()].filter((message) => message.post === true);
  const chosen = pickNewestIndex(
    posts.map((message) => ({ text: message.message ?? "", id: message.id })),
    now,
  );
  if (typeof chosen !== "number") return chosen;

  const message = posts[chosen]!;
  const media = message.media;
  if (!(media instanceof Api.MessageMediaDocument)) return "not-an-index";
  return {
    messageId: message.id,
    pushedAt: versionStamp(message.message ?? "", now),
    // Called the way `partFetcher` calls it, which is proven against real parts.
    chunks: () => telegram.client.iterDownload(media as never, {}) as AsyncIterable<Uint8Array>,
  };
}
