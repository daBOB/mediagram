/**
 * The web server's side of watch together (`docs/mlib-spec.md` §7b): the one
 * place its tabs' moves reach the channel, and the channel's edits reach its
 * tabs, which do the following themselves. This keeps the newest session it
 * knows, writes through one sender that keeps only the newest own write per
 * message, and forwards every edit it hears.
 */

import { parseSession, writeSession } from "../../public/lib/together/session.js";
import type { Member, Session } from "../../public/lib/together/session.js";
import { acted, finished, joined, left, started } from "../../public/lib/together/moves.js";
import type { Playback } from "../../public/lib/together/moves.js";
import { isLive } from "../../public/lib/together/follow.js";
import { failureMessage } from "../failure-message";

/** How often the sessions this server's tabs are in are read again, for a push missed while offline. */
export const TOGETHER_RECHECK_MS = 30_000;

export type EditOutcome = "ok" | "gone" | { wait: number };

/** The library channel, as watch together needs it. Every call but `available` throws while Telegram is unreachable. */
export interface TogetherChannel {
  /** Signed in and connected. */
  available(): boolean;
  /** Session messages among the newest in the channel. */
  recent(): Promise<{ id: number; text: string }[]>;
  /** A message's current text; `null` once it is deleted. */
  read(id: number): Promise<string | null>;
  send(text: string): Promise<number>;
  edit(id: number, text: string): Promise<EditOutcome>;
  remove(id: number): Promise<void>;
}

export interface Live { id: number; session: Session }
export type DurationOf = (set: string) => number | null;

export class TogetherHub {
  /** The newest session known per message: heard, read, or written here. */
  private readonly known = new Map<number, Session>();
  /** Screens whose moves went through this server: their echoes are not news. */
  private readonly local = new Set<string>();
  /** The newest own write per message, not yet sent. */
  private readonly outgoing = new Map<number, Session>();
  private sending: Promise<void> | null = null;

  constructor(
    private readonly channel: TogetherChannel,
    private readonly announce: (id: number, session: Session | null) => void,
    private readonly now: () => number = Date.now,
    private readonly sleep: (ms: number) => Promise<void> = (ms) => new Promise((done) => setTimeout(done, ms)),
  ) {}

  available(): boolean { return this.channel.available(); }

  /** An edit heard from the channel. Another screen's is always kept: an own write still waiting is re-based on it. */
  heard(id: number, text: string): void {
    const session = parseSession(text);
    if (!session) return;
    if (this.outgoing.has(id) && this.local.has(session.by)) return; // this server's newer write is still on its way
    this.keep(id, session);
  }

  /**
   * The newest live session: in the newest messages, or the newest one known
   * here, read by id, when a busy channel has pushed it out of them. Stale ones
   * found on the way are deleted, as `docs/mlib-spec.md` §7b asks of whoever reads them.
   */
  async live(durationOf: DurationOf): Promise<Live | null> {
    const found = await this.channel.recent();
    const remembered = Math.max(0, ...this.known.keys());
    if (remembered > 0 && !found.some((m) => m.id === remembered)) {
      const text = await this.channel.read(remembered);
      if (text !== null && parseSession(text)) found.push({ id: remembered, text }); else this.forget(remembered);
    }
    let newest: Live | null = null;
    for (const { id, text } of found) {
      const session = this.outgoing.has(id) ? this.known.get(id) : parseSession(text);
      if (!session) continue;
      if (!isLive(session, durationOf(session.set), this.now())) {
        this.forget(id);
        void this.channel.remove(id).catch(() => {});
        continue;
      }
      this.known.set(id, session);
      if (!newest || id > newest.id) newest = { id, session };
    }
    return newest;
  }

  async start(me: Member, own: Playback, durationOf: DurationOf): Promise<{ live: Live; conflict: boolean }> {
    const existing = await this.live(durationOf);
    if (existing) return { live: existing, conflict: true };
    const session = started(crypto.randomUUID(), me, own, this.now());
    const id = await this.channel.send(writeSession(session));
    this.local.add(me.screen);
    this.keep(id, session);
    return { live: { id, session }, conflict: false };
  }

  async join(id: number, me: Member): Promise<Live | null> {
    const text = this.outgoing.has(id) ? null : await this.channel.read(id);
    if (text === null && !this.outgoing.has(id)) return this.forget(id);
    const current = text === null ? this.known.get(id) : parseSession(text);
    if (!current || current.ended) return null;
    this.local.add(me.screen);
    return { id, session: this.write(id, joined(current, me)) };
  }

  act(id: number, screen: string, change: Partial<Playback>): Session | null {
    const current = this.known.get(id);
    if (!current || current.ended) return null;
    this.local.add(screen);
    return this.write(id, acted(current, change, screen, this.now()));
  }

  async leave(id: number, screen: string): Promise<void> {
    const current = this.known.get(id);
    if (!current || current.ended) return;
    if (this.write(id, left(current, screen)).ended) await this.close(id);
  }

  /** The end of the last title: over for everyone, not just this screen. */
  async end(id: number, screen: string): Promise<void> {
    const current = this.known.get(id);
    if (!current || current.ended) return;
    this.write(id, finished(current, screen));
    await this.close(id);
  }

  /**
   * Re-reads every session a local screen is in. A push missed while the
   * connection was down is not replayed (`channel-events.ts`), so this is how
   * a tab that kept playing learns what it missed. Skipped while a write is on
   * its way: a read then may predate it.
   */
  async recheck(): Promise<void> {
    if (!this.channel.available() || this.sending) return;
    for (const [id, session] of [...this.known]) {
      if (this.outgoing.has(id) || !session.members.some((m) => this.local.has(m.screen))) continue;
      const text = await this.channel.read(id).catch(() => undefined);
      if (text === undefined) continue;
      const read = text === null ? null : parseSession(text);
      // Compared as written here: Kotlin and JS print the same numbers differently.
      if (!read) this.forget(id); else if (writeSession(read) !== writeSession(session)) this.heard(id, writeSession(read));
    }
  }

  private write(id: number, session: Session): Session {
    this.keep(id, session);
    this.outgoing.set(id, session);
    this.kick();
    return session;
  }

  private keep(id: number, session: Session): void {
    this.known.set(id, session);
    this.announce(id, session.ended ? null : session);
  }

  /** The session is over here: forgotten, and every tab told. */
  private forget(id: number): null {
    this.known.delete(id);
    this.announce(id, null);
    return null;
  }

  /** Once the ending write is sent, the message goes. */
  private async close(id: number): Promise<void> {
    while (this.sending) await this.sending;
    await this.channel.remove(id).catch(() => {});
    this.forget(id);
  }

  /** Starts the one sender, unless it runs. Started again for a write queued after its last look but before it stopped. */
  private kick(): void {
    this.sending ??= this.drain().finally(() => { this.sending = null; if (this.outgoing.size > 0) this.kick(); });
  }

  private async drain(): Promise<void> {
    for (let next = this.outgoing.entries().next(); !next.done; next = this.outgoing.entries().next()) {
      const [id, own] = next.value;
      this.outgoing.delete(id);
      // Re-based on the newest known session: the own playback, with the members another screen's edit may have changed.
      const base = this.known.get(id);
      if (!base || (base.ended && !own.ended)) continue; // over meanwhile: the write is dropped
      try {
        const outcome = await this.channel.edit(id, writeSession({ ...own, session: base.session, members: base.members }));
        if (outcome === "gone") this.forget(id);
        else if (outcome !== "ok") {
          // Flood-limited: the newest own write goes once allowed, re-based on whatever is known by then.
          if (!this.outgoing.has(id)) this.outgoing.set(id, own);
          await this.sleep(outcome.wait * 1000);
        }
      } catch (error) {
        console.warn(`together: an edit failed: ${failureMessage(error)}`);
      }
    }
  }
}
