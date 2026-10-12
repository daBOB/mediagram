/**
 * The session message goes to Telegram as the text it is. teleproto parses
 * Markdown by default, which would eat a label's `__` or `**`, and then no
 * screen could read the session; the Rust core sends plain text too.
 */

import { expect, test } from "bun:test";
import { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";
import { TelegramTogetherChannel } from "../src/telegram/together-channel";

test("a session is sent and edited as plain text", async () => {
  const asked: Record<string, unknown>[] = [];
  const client = {
    connected: true,
    sendMessage: async (_peer: unknown, params: Record<string, unknown>) => { asked.push(params); return { id: 901 }; },
    editMessage: async (_peer: unknown, params: Record<string, unknown>) => { asked.push(params); },
  };
  const telegram = Telegram.withChannel({ client } as unknown as Telegram, -1001234567890, 1n);
  const channel = new TelegramTogetherChannel(new TelegramConnection(telegram));
  const text = '#mlib-together {"label":"__Den__ **TV**"}';
  expect(await channel.send(text)).toBe(901);
  expect(await channel.edit(901, text)).toBe("ok");
  expect(asked).toEqual([
    { message: text, silent: true, parseMode: false },
    { message: 901, text, parseMode: false },
  ]);
});
