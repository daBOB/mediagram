/**
 * The one sender of watch-together edits (`docs/mlib-spec.md` §7b), the twin
 * of the Android `TogetherSender.kt`. It keeps the tabs' moves per message,
 * composed in the order they were made, and sends them one edit at a time,
 * each applied to the newest session known when it goes.
 *
 * An edit that throws, as one does while Telegram can't be reached, is tried
 * again after 30 s, never sooner, until it has thrown three times. The move
 * is then given up on, and the tabs are told what the channel holds.
 */

import { writeSession, type Session } from "../../public/lib/together/session.js";
import { failureMessage } from "../failure-message";
import type { TogetherChannel } from "./channel";

/** A tab's move, applied to whatever session is newest when it is sent. Each is idempotent. */
export type Move = (s: Session) => Session;

/** How long a move whose edit threw waits before it is tried again. */
export const EDIT_RETRY_MS = 30_000;
/** How many times a move's edit may throw before the move is given up on, so a lasting error is not tried for ever. */
export const EDIT_TRIES = 3;

export class TogetherSender {
  /** The moves per message not yet sent, composed in the order they were made. */
  private readonly outgoing = new Map<number, Move>();
  /** The edit on its way: what it says is the newest known until it resolves. */
  private flying: { id: number; session: Session } | null = null;
  private sending: Promise<void> | null = null;
  /** How many times in a row each message's edit has thrown. */
  private readonly failures = new Map<number, number>();

  constructor(
    private readonly channel: TogetherChannel,
    /** The hub's newest known session per message, which this reads and writes. */
    private readonly known: Map<number, Session>,
    /** The session is over: its message deleted, or found gone. */
    private readonly forget: (id: number) => void,
    /** A move was given up on: the tabs follow what the channel holds again, the one that moved included. */
    private readonly lost: (id: number) => void,
    private readonly sleep: (ms: number) => Promise<void>,
  ) {}

  /** Whether any edit is queued or on its way. */
  get busy(): boolean { return this.sending !== null; }

  /** Whether a move for this message is waiting to go. */
  queued(id: number): boolean { return this.outgoing.has(id); }

  /** Whether a move for this message is waiting to go or on its way. */
  pending(id: number): boolean { return this.outgoing.has(id) || this.flying?.id === id; }

  /** The session this message's edit on its way says, if one is. */
  inFlight(id: number): Session | null { return this.flying?.id === id ? this.flying.session : null; }

  /** What this server holds the session to be: the newest known, with its moves not yet sent. */
  view(id: number): Session | undefined {
    const base = this.known.get(id);
    const move = this.outgoing.get(id);
    return base && move && !base.ended ? move(base) : base;
  }

  queue(id: number, move: Move): void {
    const before = this.outgoing.get(id);
    this.outgoing.set(id, before ? (s) => move(before(s)) : move);
    this.kick();
  }

  /** Starts the drain unless it runs. Started again for a move queued after its last look but before it stopped. */
  private kick(): void {
    this.sending ??= this.drain().finally(() => {
      this.sending = null;
      if (this.outgoing.size > 0) this.kick();
    });
  }

  private async drain(): Promise<void> {
    for (let next = this.outgoing.entries().next(); !next.done; next = this.outgoing.entries().next()) {
      const [id, move] = next.value;
      this.outgoing.delete(id);
      const base = this.known.get(id);
      if (!base || base.ended) continue; // over meanwhile: the move is dropped
      await this.send(id, move, base);
    }
  }

  private async send(id: number, move: Move, base: Session): Promise<void> {
    const session = move(base);
    this.known.set(id, session);
    this.flying = { id, session };
    const outcome = await this.channel.edit(id, writeSession(session)).catch((error: unknown) => {
      console.warn(`together: an edit failed: ${failureMessage(error)}`);
      return null;
    });
    this.flying = null;
    // Refused or failed: the channel still holds what it held, unless another screen's edit came meanwhile.
    if (outcome !== "ok" && this.known.get(id) === session) this.known.set(id, base);
    if (outcome === null) return this.failed(id, move);
    this.failures.delete(id);
    if (outcome === "gone") {
      this.forget(id);
    } else if (outcome !== "ok") {
      // Flood-limited: the move goes again once allowed.
      this.again(id, move);
      await this.sleep(outcome.wait * 1000);
    } else if (session.ended && this.known.get(id)?.ended) {
      // Ending and then deleting the message is this sender's alone, so nothing deletes it before the end is written.
      await this.channel.remove(id).catch(() => {});
      this.forget(id);
    }
  }

  /** A thrown edit goes again after the retry wait, or, the last time, is given up on. Nothing is tried sooner either way. */
  private async failed(id: number, move: Move): Promise<void> {
    const tries = (this.failures.get(id) ?? 0) + 1;
    if (tries < EDIT_TRIES) {
      this.failures.set(id, tries);
      this.again(id, move);
    } else {
      this.failures.delete(id);
      console.warn(`together: a move was given up after ${EDIT_TRIES} failed edits`);
      this.lost(id);
    }
    await this.sleep(EDIT_RETRY_MS);
  }

  /** The move goes again, ahead of any made since, onto whatever is newest by then. */
  private again(id: number, move: Move): void {
    const later = this.outgoing.get(id);
    this.outgoing.set(id, later ? (s) => later(move(s)) : move);
  }
}
