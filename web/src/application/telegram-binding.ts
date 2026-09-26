/**
 * Keeping the update listener and the followed channel in step with a
 * connection that can be restarted or pointed at a different channel.
 *
 * `TelegramSource` and `TelegramStateChannel` read the connection fresh on
 * every call, so neither needs rebuilding when the client changes. The
 * update listener is different: it registers a handler on one concrete
 * client object and reads one channel id out of a closure, so a restart or a
 * channel switch leaves it listening to something that is gone. `rebind`
 * is how the caller says "the client or the channel changed" — stop
 * whatever was listening, and if there is a live client now, listen again.
 */

import { bareChannelId } from "../telegram/client";
import type { TelegramConnection } from "../telegram/connection";
import { listenForLibraryEvents } from "../telegram/channel-events";
import { LibraryUpdates, type Sync } from "./lifecycle";
import type { CatalogFollower } from "./catalog-follow";

/**
 * The channel this player follows for its catalog and its watch-state sync,
 * mutable across a library switch.
 *
 * Kept set even across a sign-in to a different account: the access hash
 * stops working for the new account (it is bound to the old one), so any
 * read against it fails until Settings' "Change library" picks one this
 * account can actually see — the same recoverable failure a channel that
 * changed ownership would produce, not a special case.
 */
export class ChannelState {
  constructor(
    public chatId: number,
    public accessHash: bigint,
    public title: string | null,
  ) {}
}

export class UpdatesBinding {
  private updates: LibraryUpdates | null = null;

  constructor(
    private readonly connection: TelegramConnection,
    private readonly channel: ChannelState,
    private readonly ownDevice: string,
    private readonly sync: Sync,
    private follower: Pick<CatalogFollower, "refresh"> | null,
    private readonly listen: typeof listenForLibraryEvents = listenForLibraryEvents,
  ) {}

  /** Which follower a future `followCatalog`/`rebind` binds to; `null` follows nothing (a package catalog). */
  setFollower(follower: Pick<CatalogFollower, "refresh"> | null): void {
    this.follower = follower;
  }

  /**
   * Subscribes for whichever client is live now; a no-op signed out.
   *
   * Kept apart from `followCatalog` so the caller can subscribe before the
   * catalog and its follower exist — the shape startup needs, since a
   * listener bound and later torn down on a startup failure must not depend
   * on how far startup got.
   */
  async start(): Promise<void> {
    this.updates?.stop();
    this.updates = null;
    const telegram = this.connection.current();
    if (!telegram) return;
    const chatId = this.channel.chatId;
    const next = new LibraryUpdates(
      (onEvent) => this.listen(telegram.client, { channel: bareChannelId(chatId), ownDevice: this.ownDevice }, onEvent),
      this.sync,
    );
    this.updates = next;
    await next.start();
  }

  /** Binds the current follower and kicks off its first refresh, without waiting for it to finish. */
  followCatalog(): Promise<void> {
    return this.updates?.followCatalog(this.follower) ?? Promise.resolve();
  }

  /** `start` then `followCatalog`, for a restart or channel switch once startup is already over. */
  async rebind(): Promise<{ ready: Promise<void> }> {
    await this.start();
    return { ready: this.followCatalog() };
  }

  stop(): void {
    this.updates?.stop();
    this.updates = null;
  }
}
