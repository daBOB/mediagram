import { expect, test } from "bun:test";
import { Api } from "teleproto";
import { findNewestChannelIndex } from "../src/channel-index/find-newest-channel-index";
import type { Telegram } from "../src/telegram/client";

function message(id: number, pushedAt: number, post = true, document = true): Api.Message {
  return {
    id,
    post,
    message: `#mlib-index v=2\n${JSON.stringify({ pushed_at: pushedAt })}`,
    media: document ? new Api.MessageMediaDocument({}) : undefined,
  } as Api.Message;
}

function channel(pinned: Api.Message[], searched: Api.Message[] | Error) {
  const requests: Record<string, unknown>[] = [];
  const downloads: unknown[] = [];
  const peer = { channel: "fixture" };
  const client = {
    async getMessages(actualPeer: unknown, options: Record<string, unknown>) {
      expect(actualPeer).toBe(peer);
      requests.push(options);
      if (options.filter instanceof Api.InputMessagesFilterPinned) return pinned;
      if (searched instanceof Error) throw searched;
      return searched;
    },
    async *iterDownload(media: unknown) {
      downloads.push(media);
      yield new Uint8Array([1, 2]);
      yield new Uint8Array([3]);
    },
  };
  return { telegram: { peer, client } as unknown as Telegram, requests, downloads };
}

test("discovery preserves the pinned duplicate and downloads its document only on demand", async () => {
  const pinned = message(7, 900);
  const fixture = channel([pinned], [message(7, 999, true, false), message(8, 800)]);
  const found = await findNewestChannelIndex(fixture.telegram, 1000);
  expect(typeof found).not.toBe("string");
  if (typeof found === "string") throw new Error(found);
  expect(found.messageId).toBe(7);
  expect(found.pushedAt).toBe(900);
  expect(fixture.requests).toHaveLength(2);
  expect(fixture.requests[0]!.filter).toBeInstanceOf(Api.InputMessagesFilterPinned);
  expect(fixture.requests[0]!.limit).toBe(100);
  expect(fixture.requests[1]).toEqual({ search: "#mlib-index", limit: 50 });
  expect(fixture.downloads).toEqual([]);
  expect(await Array.fromAsync(found.chunks())).toEqual([new Uint8Array([1, 2]), new Uint8Array([3])]);
  expect(fixture.downloads).toEqual([pinned.media]);
});

test("a failed supplementary search retains a usable pinned index", async () => {
  const fixture = channel([message(1, 900)], new Error("search unavailable"));
  expect(await findNewestChannelIndex(fixture.telegram, 1000)).toMatchObject({ messageId: 1, pushedAt: 900 });
});

test("newer member messages cannot displace the channel's own index", async () => {
  const fixture = channel([message(1, 800)], [message(2, 900, false)]);
  expect(await findNewestChannelIndex(fixture.telegram, 1000)).toMatchObject({ messageId: 1 });
});

test("a selected caption without document media is refused without downloading", async () => {
  const fixture = channel([message(1, 900, true, false)], []);
  expect(await findNewestChannelIndex(fixture.telegram, 1000)).toBe("not-an-index");
  expect(fixture.downloads).toEqual([]);
});

test("supplementary search can supply an unpinned index, while an empty channel remains empty", async () => {
  expect(await findNewestChannelIndex(channel([], [message(2, 900)]).telegram, 1000)).toMatchObject({ messageId: 2 });
  expect(await findNewestChannelIndex(channel([], []).telegram, 1000)).toBe("nothing-pinned");
});
