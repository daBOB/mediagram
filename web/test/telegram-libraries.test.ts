import { describe, expect, test } from "bun:test";
import { Api } from "teleproto";
import { listLibraries } from "../src/telegram/libraries";
import type { Telegram } from "../src/telegram/client";

function channel(id: number, title: string, accessHash: bigint, megagroup = false): Api.Channel {
  return new Api.Channel({
    id: id as never,
    title,
    photo: new Api.ChatPhotoEmpty(),
    date: 0,
    accessHash: accessHash as never,
    megagroup,
  });
}

function fakeTelegram(dialogs: { entity: unknown }[]): Telegram {
  return {
    client: {
      iterDialogs: async function* () {
        for (const dialog of dialogs) yield dialog;
      },
    },
  } as unknown as Telegram;
}

describe("listLibraries", () => {
  test("lists broadcast channels, skipping groups and non-channels", async () => {
    const telegram = fakeTelegram([
      { entity: channel(111, "Mediagram", 999n) },
      { entity: channel(222, "A supergroup", 1n, true) },
      { entity: new Api.User({ id: 1 as never, self: true }) },
    ]);

    const found = await listLibraries(telegram);
    expect(found).toEqual([{ title: "Mediagram", chatId: -1_000_000_000_111, accessHash: 999n }]);
  });

  test("stops after the dialog ceiling", async () => {
    const dialogs = Array.from({ length: 501 }, (_, i) => ({ entity: channel(i + 1, `c${i}`, 1n) }));
    const found = await listLibraries(fakeTelegram(dialogs));
    expect(found).toHaveLength(500);
  });
});
