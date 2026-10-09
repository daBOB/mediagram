/**
 * Watch-together session messages, on the library channel: one unpinned text
 * message per session, edited in place (`docs/mlib-spec.md` §7b).
 *
 * Found by reading the newest messages, never by search. A freshly sent
 * message is never found by search, and `#mlib-together` is read as the
 * hashtag `#mlib` (see `state-channel.ts`). Not pinned: pins are flood-limited.
 *
 * Signed out, every call throws rather than answering empty: a read that
 * found nothing would say the session was deleted.
 */

import { errors } from "teleproto";
import type { Telegram } from "./client";
import type { TelegramConnection } from "./connection";
import { TOGETHER_PREFIX } from "./channel-captions";
import type { EditOutcome, TogetherChannel } from "../together/hub";

/** How far back a screen looks for a session: one history read. */
const RECENT = 100;

const rpcName = (error: unknown): string | undefined => (error as { errorMessage?: string } | null)?.errorMessage;

export class TelegramTogetherChannel implements TogetherChannel {
  constructor(private readonly connection: TelegramConnection) {}

  available(): boolean {
    const telegram = this.connection.current();
    return telegram !== null && telegram.connected !== false;
  }

  async recent(): Promise<{ id: number; text: string }[]> {
    const telegram = await this.telegram();
    const messages = await telegram.client.getMessages(telegram.peer, { limit: RECENT });
    // Only the channel's own posts, as `find-newest-channel-index.ts` reads the index.
    return messages
      .filter((m) => m.post === true && (m.message ?? "").startsWith(TOGETHER_PREFIX))
      .map((m) => ({ id: m.id, text: m.message ?? "" }));
  }

  async read(id: number): Promise<string | null> {
    const telegram = await this.telegram();
    const [message] = await telegram.client.getMessages(telegram.peer, { ids: [id] });
    return message?.message ?? null;
  }

  async send(text: string): Promise<number> {
    const telegram = await this.telegram();
    const sent = await telegram.client.sendMessage(telegram.peer, { message: text, silent: true });
    return sent.id;
  }

  async edit(id: number, text: string): Promise<EditOutcome> {
    const telegram = await this.telegram();
    try {
      await telegram.client.editMessage(telegram.peer, { message: id, text });
      return "ok";
    } catch (error) {
      if (error instanceof errors.FloodWaitError) return { wait: error.seconds };
      if (rpcName(error) === "MESSAGE_NOT_MODIFIED") return "ok";
      if (rpcName(error) === "MESSAGE_ID_INVALID") return "gone";
      throw error;
    }
  }

  async remove(id: number): Promise<void> {
    const telegram = await this.telegram();
    await telegram.client.deleteMessages(telegram.peer, [id], { revoke: true });
  }

  private async telegram(): Promise<Telegram> {
    const telegram = await this.connection.ready();
    if (!telegram) throw new Error("Telegram isn't reachable");
    return telegram;
  }
}
