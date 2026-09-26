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

/**
 * Built, not resolved. `getEntity` would cost a round trip on every start and
 * can fail on a channel the account has never opened in a client; the
 * uploader exported the access hash precisely so this needs no lookup.
 */
function channelOf(chatId: number, accessHash: bigint): Api.InputChannel {
  return new Api.InputChannel({
    channelId: BigInt(bareChannelId(chatId)) as never,
    accessHash: accessHash as never,
  });
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

  /**
   * Opens a client for `config`, or answers `null` for signed out.
   *
   * `null` covers both a `session` never set and one Telegram no longer
   * honours — neither is a reason to refuse to start: the catalog, cached
   * chunks and state on this disk are all still servable, and only an
   * uncached read needs a live client. A caller that instead wants the old
   * throwing behaviour (the CLI, which has nothing useful to serve without
   * one) is `connect`, below.
   */
  static async open(config: Config): Promise<Telegram | null> {
    if (config.session === null) return null;

    const client = new TelegramClient(
      new sessions.StringSession(config.session),
      config.apiId,
      config.apiHash,
      { connectionRetries: 3, ...sessionName() },
    );
    await client.connect();

    if (!(await client.isUserAuthorized())) {
      console.warn("telegram: the configured session is not authorized; signed out");
      await client.disconnect().catch(() => {});
      await client.destroy().catch(() => {});
      return null;
    }

    return new Telegram(client, channelOf(config.chatId, config.channelAccessHash));
  }

  /** `open`, but a signed-out result is a startup failure rather than a mode. */
  static async connect(config: Config): Promise<Telegram> {
    const telegram = await Telegram.open(config);
    if (!telegram) {
      throw new Error(
        "the configured session is not authorized; log in again with `bun run login`",
      );
    }
    return telegram;
  }

  /**
   * The same client, pointed at a different channel — no reconnect, and so
   * no risk of the two-clients-on-one-key failure a restart must avoid.
   */
  static withChannel(existing: Telegram, chatId: number, accessHash: bigint): Telegram {
    return new Telegram(existing.client, channelOf(chatId, accessHash));
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

  /**
   * This account's name and datacenter, for the Settings page's read-only
   * rows. `name` is `null` on any failure — a phone that briefly cannot
   * reach `getMe` is not a reason to hide the rest of the page.
   */
  async account(): Promise<{ name: string | null; username: string | null; dcId: number | null }> {
    const dcId = (this.client.session as unknown as { dcId?: number }).dcId ?? null;
    try {
      const me = await this.client.getMe();
      const user = me as { firstName?: string; lastName?: string; username?: string };
      const name = [user.firstName, user.lastName].filter(Boolean).join(" ").trim();
      return { name: name || null, username: user.username ?? null, dcId };
    } catch {
      return { name: null, username: null, dcId };
    }
  }
}
