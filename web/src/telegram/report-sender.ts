/**
 * A broken-video report, sent as a scheduled message to the account's own
 * Saved Messages. Scheduled, not immediate: Telegram notifies for a
 * reminder that comes due, but never for a message you send yourself. Not
 * silent, for the same reason; plain text, so a note's `__` or `**` stays.
 */

import type { TelegramConnection } from "./connection";
import { REPORT_DELAY_SECONDS } from "../../public/lib/report/report-text.js";

export interface ReportSender {
  available(): boolean;
  send(text: string): Promise<void>;
}

export class TelegramReportSender implements ReportSender {
  constructor(
    private readonly connection: TelegramConnection,
    private readonly now: () => number = () => Date.now(),
  ) {}

  available(): boolean {
    const telegram = this.connection.current();
    return telegram !== null && telegram.connected !== false;
  }

  async send(text: string): Promise<void> {
    const telegram = await this.connection.ready();
    if (!telegram) throw new Error("Telegram isn't reachable");
    await telegram.client.sendMessage("me", {
      message: text,
      parseMode: false,
      schedule: Math.floor(this.now() / 1000) + REPORT_DELAY_SECONDS,
    });
  }
}
