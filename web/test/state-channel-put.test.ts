/**
 * Covers the one decision inside the MTProto adapter: what happens to a state
 * document whose pin is refused. Everything else there is a thin call.
 */

import { describe, expect, test } from "bun:test";

import { TelegramStateChannel } from "../src/telegram/state-channel";
import type { Telegram } from "../src/telegram/client";

function fakeTelegram(pin: () => Promise<void>) {
  const deleted: number[][] = [];
  const telegram = {
    peer: {},
    client: {
      sendFile: async () => ({ id: 3200 }),
      pinMessage: pin,
      deleteMessages: async (_peer: unknown, ids: number[]) => void deleted.push(ids),
    },
  } as unknown as Telegram;
  return { telegram, deleted };
}

const body = JSON.stringify({ device: "laptop" });

describe("a first send", () => {
  test("answers the new message once it is pinned", async () => {
    const { telegram, deleted } = fakeTelegram(async () => {});
    expect(await new TelegramStateChannel(telegram).put(body, null)).toBe(3200);
    expect(deleted).toEqual([]);
  });

  test("whose pin is refused takes the document back and fails", async () => {
    // Unpinned, nobody would ever find it — not even this device, which would
    // send another beside it on the next round.
    const { telegram, deleted } = fakeTelegram(async () => {
      throw new Error("FLOOD_WAIT_633");
    });
    await expect(new TelegramStateChannel(telegram).put(body, null)).rejects.toThrow("FLOOD_WAIT_633");
    expect(deleted).toEqual([[3200]]);
  });

  test("still fails as the pin did when taking it back fails too", async () => {
    const { telegram } = fakeTelegram(async () => {
      throw new Error("FLOOD_WAIT_633");
    });
    (telegram.client as unknown as { deleteMessages: () => Promise<void> }).deleteMessages = async () => {
      throw new Error("offline");
    };
    await expect(new TelegramStateChannel(telegram).put(body, null)).rejects.toThrow("FLOOD_WAIT_633");
  });
});
