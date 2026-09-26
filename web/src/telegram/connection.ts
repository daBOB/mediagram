/**
 * The one Telegram client this process may hold, made replaceable.
 *
 * Every reader of the account — the byte source, the state channel, the
 * channel-index follower — used to hold a `Telegram` directly, fixed at
 * startup. Settings needs all three to survive a sign-out, a sign-in, or an
 * api id/hash change without a process restart, so they now hold this
 * instead: the same object for the life of the process, with the client it
 * hands back replaced underneath it.
 *
 * `restart` is the only way the client changes identity, and it is
 * serialised: a second call queues behind the first rather than racing it,
 * because two live clients on one auth key break each other
 * (`login.ts`, measured). A read that calls `ready()` while a restart is in
 * flight parks until it settles, then sees the new client — or `null`, for
 * signed out.
 */

import type { Telegram } from "./client";

export class TelegramConnection {
  private telegram: Telegram | null;
  /** Bumped on every restart and channel switch, so a caller can tell its client changed under it. */
  generation = 0;
  private pending: Promise<void> | null = null;
  private chain: Promise<void> = Promise.resolve();

  constructor(initial: Telegram | null) {
    this.telegram = initial;
  }

  /** The current client, or `null` — read without waiting for a restart in progress. */
  current(): Telegram | null {
    return this.telegram;
  }

  /** The current client once any restart in progress has settled. */
  async ready(): Promise<Telegram | null> {
    if (this.pending) await this.pending.catch(() => {});
    return this.telegram;
  }

  /** The same client, a different channel. Throws while signed out — there is no client to point. */
  withChannel(attach: (existing: Telegram) => Telegram): void {
    if (!this.telegram) throw new Error("cannot choose a channel while signed out");
    this.telegram = attach(this.telegram);
    this.generation += 1;
  }

  /**
   * Swaps the live client for a new one.
   *
   * Order: gate reads → disconnect the old client → open the new one. On
   * failure, `reopenPrevious` (when given) restores the credentials that were
   * live before this call, so a bad api id/hash or a wrong code leaves the
   * player exactly as reachable as it was — the error still propagates, so
   * the caller can tell the attempt failed.
   */
  restart(
    open: () => Promise<Telegram | null>,
    reopenPrevious?: () => Promise<Telegram | null>,
  ): Promise<Telegram | null> {
    const run = this.chain.then(() => this.doRestart(open, reopenPrevious));
    // Queue the next restart behind this one whether or not it throws.
    this.chain = run.then(
      () => {},
      () => {},
    );
    return run.then(() => this.telegram);
  }

  private async doRestart(
    open: () => Promise<Telegram | null>,
    reopenPrevious?: () => Promise<Telegram | null>,
  ): Promise<void> {
    let release!: () => void;
    this.pending = new Promise((resolve) => {
      release = resolve;
    });
    try {
      const old = this.telegram;
      // Parked here, not just during the disconnect: a read that starts
      // between the disconnect and the new client succeeding must not be
      // handed the client that is on its way out.
      this.telegram = null;
      if (old) await old.disconnect().catch(() => {});
      try {
        this.telegram = await open();
      } catch (error) {
        if (reopenPrevious) this.telegram = await reopenPrevious().catch(() => null);
        throw error;
      }
    } finally {
      this.generation += 1;
      this.pending = null;
      release();
    }
  }

  /** A holder that never restarts, for a fake client in a test. */
  static fixed(telegram: Telegram | null): TelegramConnection {
    return new TelegramConnection(telegram);
  }
}
