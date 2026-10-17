/** A report goes to the account itself, scheduled, as plain text, and not silent: a scheduled message in Saved Messages is the only own message Telegram notifies about. */
import { expect, test } from "bun:test";
import { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";
import { TelegramReportSender } from "../src/telegram/report-sender";

test("a report is scheduled a minute ahead into Saved Messages", async () => {
  const asked: unknown[][] = [];
  const client = { connected: true, sendMessage: async (peer: unknown, params: unknown) => { asked.push([peer, params]); return { id: 1 }; } };
  const telegram = Telegram.withChannel({ client } as unknown as Telegram, -1001234567890, 1n);
  const sender = new TelegramReportSender(new TelegramConnection(telegram), () => 1_700_000_000_500);
  await sender.send("#mlib-report\n__x__");
  expect(asked).toEqual([["me", { message: "#mlib-report\n__x__", parseMode: false, schedule: 1_700_000_060 }]]);
});
