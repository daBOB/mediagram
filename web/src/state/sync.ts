/**
 * Keeping the Continue shelf the same on every machine.
 *
 * The local database stays the source of truth for the machine it is on.
 * This is an addition to it, never a replacement — everything here can fail,
 * and a player whose sync fails is a player that works exactly as it did
 * before any of this existed. That is the safety property the whole design
 * hangs on, and it is why nothing below is allowed to throw.
 *
 * The channel is behind an interface rather than reached for directly. The
 * decisions worth getting right — when to send, whether anything changed,
 * what to do with what comes back — are here and are tested against a fake.
 * Only the MTProto calls are left untested, and they are a thin adapter in
 * `telegram/state-channel.ts`.
 */

import { mergeStates } from "./merge";
import { parseRecord, type SyncRecord } from "./sync-record";
import type { WatchState } from "./store";

/** One device's document, as it sits on the channel. */
export interface ChannelDocument {
  messageId: number;
  /** Whose it is, from the caption. */
  device: string;
  /** The JSON body. */
  text: string;
}

/**
 * Where the documents live.
 *
 * Deliberately small: list them, and put mine. A device only ever writes its
 * own message — Telegram has no compare-and-swap, so two devices editing one
 * shared message would clobber each other with no way to notice.
 */
export interface StateChannel {
  list(): Promise<ChannelDocument[]>;
  /** Sends, or edits `messageId` when this device has written before. */
  put(body: string, messageId: number | null): Promise<number>;
}

export interface SyncOutcome {
  /** Rows this machine took in. */
  pulled: number;
  /** Whether a document was actually sent. */
  pushed: boolean;
  /** What went wrong, if anything. Never thrown. */
  failed?: string;
}

export class StateSync {
  /** This device's own message, once it is known. */
  private mine: number | null = null;
  /** The last body sent, so an unchanged one is not sent again. */
  private lastSent: string | null = null;
  /** The round in progress, if any. Never rejects: `round` catches. */
  private running: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly state: WatchState,
    private readonly channel: StateChannel,
    private readonly device: string,
  ) {}

  /**
   * One round: read everyone's, merge, take in what is newer, write back.
   *
   * Pull before push so what this machine sends already reflects what it just
   * learnt. A device that pushed first would publish a document it knew to be
   * out of date, and every other device would have to do the reconciling that
   * this one had the information to do.
   */
  async once(): Promise<SyncOutcome> {
    // One round at a time. The timer and a pushed update can both ask for one,
    // and two rounds overlapping on a device's first send would each find no
    // document of its own and each send one. Queued, not skipped: a round
    // asked for mid-round may carry news the running one already missed.
    const round = this.running.then(() => this.round());
    this.running = round;
    return round;
  }

  private async round(): Promise<SyncOutcome> {
    try {
      const documents = await this.channel.list();
      const pulled = this.take(documents);
      const pushed = await this.give();
      return { pulled, pushed };
    } catch (error) {
      // Swallowed on purpose. A channel that cannot be reached costs a log
      // line, and the player carries on against its own database.
      return { pulled: 0, pushed: false, failed: describe(error) };
    }
  }

  /**
   * Merges what the channel holds into this machine.
   *
   * **This device's own document is part of the merge.** Without it the merge
   * would answer only what the others knew, and `importMerged` is corrective
   * rather than wholesale precisely so that this cannot erase anything — but
   * including it is what makes the answer complete rather than merely safe.
   */
  private take(documents: ChannelDocument[]): number {
    const records: SyncRecord[] = [this.state.exportRecord(this.device)];
    for (const document of documents) {
      // A device's own message is recognised here rather than filtered out, so
      // that the id is learnt even on a run where nothing needs sending.
      if (document.device === this.device) this.mine = document.messageId;
      else {
        const record = parseRecord(document.text);
        if (record !== null) records.push(record);
      }
    }
    return this.state.importMerged(mergeStates(records));
  }

  /** Writes this machine's document, unless it would be the same one again. */
  private async give(): Promise<boolean> {
    const record = this.state.exportRecord(this.device);
    // `writtenAt` changes on every export and nothing reads it during a merge,
    // so it is left out of the comparison: including it would make every
    // document different from the last and upload one on every tick for ever.
    const body = JSON.stringify(record);
    const comparable = JSON.stringify({ ...record, writtenAt: 0 });
    if (comparable === this.lastSent) return false;

    this.mine = await this.channel.put(body, this.mine);
    this.lastSent = comparable;
    return true;
  }
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
