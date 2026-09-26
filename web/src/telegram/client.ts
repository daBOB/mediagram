/**
 * The only file that knows what speaks MTProto.
 *
 * Everything else in the player deals in parts, ranges and bytes. If
 * `teleproto` — a single-maintainer fork of the archived GramJS — ever needs
 * replacing, this is the file to port.
 */

import { Api, TelegramClient, sessions } from "teleproto";
import type { Config } from "../config";
import { MediaCache } from "./media-cache";
import { sessionName } from "./session-name";

/** Bot-API dialog ids are `-100` followed by the bare channel id. */
export function bareChannelId(chatId: number): number {
  const bare = -chatId - 1_000_000_000_000;
  if (bare <= 0) {
    throw new Error(`chat id ${chatId} is not a -100-prefixed channel id`);
  }
  return bare;
}

export class Telegram {
  private readonly media: MediaCache<Api.TypeMessageMedia>;

  private constructor(
    readonly client: TelegramClient,
    private readonly channel: Api.InputChannel,
  ) {
    this.media = new MediaCache((messageId) => this.fetchPartMedia(messageId));
  }

  /**
   * The channel as a *peer*, which is what the convenience wrappers want.
   *
   * `channels.getMessages` takes an `InputChannel` and everything else —
   * `getMessages`, `sendFile`, `editMessage` — takes an `InputPeer`. Handing
   * one where the other is expected serializes wrong and surfaces as a parse
   * error deep in the response, so the two are kept apart by name rather than
   * by hoping the right one is passed.
   */
  get peer(): Api.InputPeerChannel {
    return new Api.InputPeerChannel({
      channelId: this.channel.channelId,
      accessHash: this.channel.accessHash,
    });
  }

  static async connect(config: Config): Promise<Telegram> {
    const client = new TelegramClient(
      new sessions.StringSession(config.session),
      config.apiId,
      config.apiHash,
      { connectionRetries: 3, ...sessionName() },
    );
    await client.connect();

    // Fail loudly rather than prompting: the player may have no terminal, and
    // a half-open login would look like an empty library.
    if (!(await client.isUserAuthorized())) {
      throw new Error(
        "the configured session is not authorized; log in again with `bun run login`",
      );
    }

    // Built, not resolved. `getEntity` would cost a round trip on every start
    // and can fail on a channel the account has never opened in a client; the
    // uploader exported the access hash precisely so this needs no lookup.
    const channel = new Api.InputChannel({
      channelId: BigInt(bareChannelId(config.chatId)) as never,
      accessHash: config.channelAccessHash as never,
    });
    return new Telegram(client, channel);
  }

  /**
   * The document media of a part's message.
   *
   * Cached for a while rather than fetched fresh on every call: a run's
   * chunk writes, its readahead, and the audio-track probe used to each pay
   * their own `channels.GetMessages` for the same message, on an account
   * where every request shares one flood limit (`download-gate.ts`). The
   * file reference inside a document handle still expires on Telegram's own
   * clock, though, so this is a cache of a recent answer, not of the truth:
   * `forgetPartMedia` drops an entry the moment a download reports it stale,
   * and the caller is expected to ask again and retry once.
   */
  async partMedia(messageId: number): Promise<Api.TypeMessageMedia> {
    return this.media.get(messageId);
  }

  /** Drops a cached document handle so the next `partMedia` asks Telegram again. */
  forgetPartMedia(messageId: number): void {
    this.media.invalidate(messageId);
  }

  /**
   * Invoked directly rather than through `client.getMessages`, because
   * `channels.getMessages` takes an `InputChannel` and the convenience
   * wrapper is happy to be handed an `InputPeer` instead. The request then
   * serializes wrong, and the failure surfaces as a parse error deep in the
   * *response* — "a TLObject was trying to be read when it should not be
   * read" — which reads like a stale schema and is not.
   */
  private async fetchPartMedia(messageId: number): Promise<Api.TypeMessageMedia> {
    const result = await this.client.invoke(
      new Api.channels.GetMessages({
        channel: this.channel,
        id: [new Api.InputMessageID({ id: messageId })],
      }),
    );

    const messages = (result as { messages?: Api.TypeMessage[] }).messages ?? [];
    const message = messages[0];
    if (!message || !(message instanceof Api.Message)) {
      throw new Error(`message ${messageId} no longer exists`);
    }
    if (!message.media || !(message.media instanceof Api.MessageMediaDocument)) {
      throw new Error(`message ${messageId} carries no downloadable document`);
    }
    return message.media;
  }

  /**
   * Whether the MTProto connection is up right now.
   *
   * Asked rather than remembered: the library reconnects on its own, so a
   * flag set at startup would keep saying "connected" through an outage. The
   * property is optional on the client, so an implementation that does not
   * offer it reports `null` rather than a confident guess.
   */
  get connected(): boolean | null {
    const said = (this.client as { connected?: boolean }).connected;
    return typeof said === "boolean" ? said : null;
  }

  async disconnect(): Promise<void> {
    await this.client.disconnect();
    await this.client.destroy();
  }
}
