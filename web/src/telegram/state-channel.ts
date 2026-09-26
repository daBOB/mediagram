/**
 * Watch-state documents, on the same channel as everything else.
 *
 * The adapter half of `state/sync.ts`: this is the only part of syncing that
 * speaks MTProto, and the only part that cannot be tested without a real
 * account. Everything worth getting right — when to send, whether anything
 * changed, what to do with what comes back — is on the other side of the
 * `StateChannel` interface and is tested against a fake.
 *
 * The caption convention follows `push_index.rs`: a marker, a version, and
 * then what distinguishes one message from another.
 *
 * **These are pinned, and discovery is the pin list.** The first version of
 * this searched for the caption instead, on the reasoning that a pin is how a
 * reader finds *the* index and extra pins would get in its way. Measuring it
 * killed that idea twice over:
 *
 *  - A freshly sent message is **never** found by search. Not slowly — a
 *    probe polled for a full minute and it never appeared, while the same
 *    search returned index messages days old immediately.
 *  - The search was returning the wrong messages anyway. Telegram parses
 *    `#mlib-state` as the hashtag `#mlib`, so it matched every `#mlib v=4`
 *    part document in the channel. `deviceFromCaption` rejected them all,
 *    which is the only reason this looked like "found nothing" rather than
 *    something worse.
 *
 * A pin list is not full text: it is exact and immediate. And the worry that
 * prompted the original decision does not survive contact with the code —
 * both readers of the pin list, `rescan.rs` and the Android core's
 * `pick_index`, filter on `#mlib-index` before counting anything, and core
 * reads up to a hundred pins. A handful of devices cannot crowd out an index.
 */

import { Api } from "teleproto";
import { CustomFile } from "teleproto/client/uploads";
import type { ChannelDocument, StateChannel } from "../state/sync";
import type { TelegramConnection } from "./connection";

import { stateCaption, deviceFromCaption } from "./channel-captions";

const STATE_DOCUMENT_NAME = "watch-state.json";
/** How many pins to read. One state message per device; this is not close. */
const MOST = 100;

/**
 * Watch-state documents, on whichever channel the connection currently
 * points at.
 *
 * Reads the client fresh from the connection on every call rather than
 * holding one: signed out, there is no channel to read or write, and
 * `StateSync.once` already treats a failed round as one to try again later
 * rather than a reason to stop.
 */
export class TelegramStateChannel implements StateChannel {
  constructor(private readonly connection: TelegramConnection) {}

  async list(): Promise<ChannelDocument[]> {
    const telegram = await this.connection.ready();
    if (!telegram) return [];

    const messages = await telegram.client.getMessages(telegram.peer, {
      filter: new Api.InputMessagesFilterPinned(),
      limit: MOST,
    });

    const documents: ChannelDocument[] = [];
    for (const message of messages) {
      const device = deviceFromCaption(message.message);
      if (device === null || !message.media) continue;
      // Downloaded one at a time rather than in parallel: there are as many of
      // these as there are devices, and a burst of downloads on startup is a
      // good way to meet a flood wait for no benefit.
      const body = await telegram.client.downloadMedia(message);
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
    const telegram = await this.connection.ready();
    if (!telegram) throw new Error("cannot sync while signed out");

    const caption = stateCaption(JSON.parse(body).device as string);
    // `CustomFile`, not a web `File`. teleproto is a fork of GramJS, which
    // predates `File` being a thing in Node and rejects one outright —
    // "Cannot use [object Blob] as file". In-memory data goes through this,
    // which is also the only way to give the document a name without a path
    // on disk to take one from.
    const file = new CustomFile(STATE_DOCUMENT_NAME, Buffer.byteLength(body), "", Buffer.from(body));

    if (messageId !== null) {
      // Edited, never re-sent. A device writes one message for ever; a second
      // would be a second opinion nobody asked for and nothing would clean up.
      const edited = await telegram.client.editMessage(telegram.peer, {
        message: messageId,
        text: caption,
        file,
      });
      return edited?.id ?? messageId;
    }

    const sent = await telegram.client.sendFile(telegram.peer, {
      file,
      caption,
      // Sent as a document rather than letting Telegram decide: a `.json` is
      // not media, and anything it guessed would be wrong.
      forceDocument: true,
      attributes: [new Api.DocumentAttributeFilename({ fileName: STATE_DOCUMENT_NAME })],
    });

    // Pinned once, when the message is first sent — every later write edits it
    // in place, so the pin stays and this costs one service message per device
    // for the life of the install. Silent, because nobody wants a notification
    // that a machine has recorded where a film got to.
    try {
      await telegram.client.pinMessage(telegram.peer, sent.id, { notify: false });
    } catch (error) {
      // Unpinned, the document is invisible — discovery is the pin list — so
      // the next round would send another beside it, and nothing would ever
      // clean either up. Pins are flood-limited hard (a wait of over ten
      // minutes, measured), so this is not hypothetical. Take the document
      // back and let the next round start over: a refused pin then costs a
      // round, not a stray document per round.
      await telegram.client.deleteMessages(telegram.peer, [sent.id], { revoke: true }).catch(() => {});
      throw error;
    }
    return sent.id;
  }
}
