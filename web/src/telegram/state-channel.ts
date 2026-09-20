/**
 * Watch-state documents, on the same channel as everything else.
 *
 * The adapter half of `state/sync.ts`: this is the only part of syncing that
 * speaks MTProto, and the only part that cannot be tested without a real
 * account. Everything worth getting right — when to send, whether anything
 * changed, what to do with what comes back — is on the other side of the
 * `StateChannel` interface and is tested against a fake.
 *
 * The caption convention follows `push_index.rs`: a marker a search can find,
 * a version, and then what distinguishes one message from another. Unlike the
 * index these are **not pinned** — a pin is how a reader finds *the* index,
 * and a handful of state messages competing for that space would make the
 * index harder to find rather than the state easier.
 */

import { Api } from "teleproto";
import type { ChannelDocument, StateChannel } from "../state/sync";
import type { Telegram } from "./client";

/** What a search looks for, and what every state caption begins with. */
export const STATE_MARKER = "#mlib-state";
const STATE_VERSION = 1;
const STATE_DOCUMENT_NAME = "watch-state.json";
const STATE_MIME_TYPE = "application/json";

/** How many state messages to consider. One per device; this is not close. */
const MOST = 50;

/** `#mlib-state v=1 device=…`, which is also how a reader knows whose it is. */
export function stateCaption(device: string): string {
  return `${STATE_MARKER} v=${STATE_VERSION} device=${device}`;
}

/**
 * Whose document a caption says this is.
 *
 * Returns `null` for anything that is not a state caption, including the
 * index's — a search for a marker matches on words, and being strict here is
 * what stops a malformed caption being read as a device called nothing.
 */
export function deviceFromCaption(caption: string | undefined): string | null {
  if (typeof caption !== "string" || !caption.startsWith(`${STATE_MARKER} `)) return null;
  const found = /\bdevice=(\S+)/.exec(caption);
  return found ? found[1]! : null;
}

export class TelegramStateChannel implements StateChannel {
  constructor(private readonly telegram: Telegram) {}

  async list(): Promise<ChannelDocument[]> {
    const messages = await this.telegram.client.getMessages(this.telegram.peer, {
      search: STATE_MARKER,
      limit: MOST,
    });

    const documents: ChannelDocument[] = [];
    for (const message of messages) {
      const device = deviceFromCaption(message.message);
      if (device === null || !message.media) continue;
      // Downloaded one at a time rather than in parallel: there are as many of
      // these as there are devices, and a burst of downloads on startup is a
      // good way to meet a flood wait for no benefit.
      const body = await this.telegram.client.downloadMedia(message);
      if (!body) continue;
      documents.push({
        messageId: message.id,
        device,
        text: typeof body === "string" ? body : new TextDecoder().decode(body as Uint8Array),
      });
    }
    return documents;
  }

  async put(body: string, messageId: number | null): Promise<number> {
    const file = new File([body], STATE_DOCUMENT_NAME, { type: STATE_MIME_TYPE });
    const caption = stateCaption(JSON.parse(body).device as string);

    if (messageId !== null) {
      // Edited, never re-sent. A device writes one message for ever; a second
      // would be a second opinion nobody asked for and nothing would clean up.
      const edited = await this.telegram.client.editMessage(this.telegram.peer, {
        message: messageId,
        text: caption,
        file,
      });
      return edited?.id ?? messageId;
    }

    const sent = await this.telegram.client.sendFile(this.telegram.peer, {
      file,
      caption,
      forceDocument: true,
      attributes: [new Api.DocumentAttributeFilename({ fileName: STATE_DOCUMENT_NAME })],
    });
    return sent.id;
  }
}
