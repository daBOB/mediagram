/**
 * The web server's side of watch together (`docs/mlib-spec.md` §7b). Its tabs'
 * moves reach the channel through one sender (`sender.ts`), each re-applied to
 * the newest session known when it goes; every edit heard reaches the tabs,
 * which follow it.
 */

import { parseSession, writeSession } from "../../public/lib/together/session.js";
import type { Member, Session } from "../../public/lib/together/session.js";
import { acted, finished, joined, left, started } from "../../public/lib/together/moves.js";
import type { Playback } from "../../public/lib/together/moves.js";
import { isLive } from "../../public/lib/together/follow.js";
import type { TogetherChannel } from "./channel";
import { TogetherSender, type Move } from "./sender";

/** How often the sessions this server's tabs are in are read again, for a push missed while offline. */
export const TOGETHER_RECHECK_MS = 30_000;

export interface Live { id: number; session: Session }
export type DurationOf = (set: string) => number | null;

const same = (a: Session, b: Session) => writeSession(a) === writeSession(b);

export class TogetherHub {
  /** The newest session known per message: heard, read, or on its way from here. */
  private readonly known = new Map<number, Session>();
  /** Screens whose moves went through this server: their echoes are not news. */
  private readonly local = new Set<string>();
  private readonly sender: TogetherSender;
  private starting: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly channel: TogetherChannel,
    private readonly announce: (id: number, session: Session | null) => void,
    private readonly now: () => number = Date.now,
    sleep: (ms: number) => Promise<void> = (ms) => new Promise((done) => setTimeout(done, ms)),
  ) {
    this.sender = new TogetherSender(channel, this.known, (id) => this.forget(id), sleep);
  }

  available(): boolean {
    return this.channel.available();
  }

  /**
   * An edit heard from the channel. Another screen's is always kept: moves
   * still waiting are applied to it. This server's own echo is old news while
   * a newer move waits, or when it is not the edit on its way.
   */
  heard(id: number, text: string): void {
    const session = parseSession(text);
    if (!session) return;
    if (this.local.has(session.by)) {
      if (this.sender.queued(id)) return;
      const flying = this.sender.inFlight(id);
      if (flying && !same(flying, session)) return;
    }
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
      if (text !== null && parseSession(text)) found.push({ id: remembered, text });
      else this.forget(remembered);
    }
    let newest: Live | null = null;
    for (const { id, text } of found) {
      const pending = this.sender.pending(id);
      const session = pending ? this.sender.view(id) : parseSession(text);
      if (!session) continue;
      if (!isLive(session, durationOf(session.set), this.now())) {
        // With a move of this server's still to go, ending and deleting it is the sender's job.
        if (!pending) {
          this.forget(id);
          void this.channel.remove(id).catch(() => {});
        }
        continue;
      }
      if (!pending) this.known.set(id, session);
      if (!newest || id > newest.id) newest = { id, session };
    }
    return newest;
  }

  /** One at a time, so a second tab's start finds the first one's session. */
  start(me: Member, own: Playback, durationOf: DurationOf): Promise<{ live: Live; conflict: boolean }> {
    const run = this.starting.then(async () => {
      const existing = await this.live(durationOf);
      if (existing) return { live: existing, conflict: true };
      const session = started(crypto.randomUUID(), me, own, this.now());
      const id = await this.channel.send(writeSession(session));
      this.local.add(me.screen);
      this.keep(id, session);
      return { live: { id, session }, conflict: false };
    });
    this.starting = run.catch(() => {});
    return run;
  }

  async join(id: number, me: Member): Promise<Live | null> {
    if (!this.sender.pending(id)) {
      const text = await this.channel.read(id);
      const read = text === null ? null : parseSession(text);
      if (!read || read.ended) return this.forget(id);
      if (!this.sender.pending(id)) this.known.set(id, read); // else a move made meanwhile already knows newer
    }
    const session = this.queue(id, me.screen, (s) => joined(s, me));
    return session && { id, session };
  }

  act(id: number, screen: string, change: Partial<Playback>): Session | null {
    const current = this.sender.view(id);
    if (!current || current.ended) return null;
    const { set, playing, pos, rate, at, by } = acted(current, change, screen, this.now());
    return this.queue(id, screen, (s) => ({ ...s, set, playing, pos, rate, at, by }));
  }

  /** Over for everyone only if nobody is left once it is sent; the sender then deletes the message. */
  leave(id: number, screen: string): void {
    this.queue(id, screen, (s) => left(s, screen));
  }

  /** The end of the last title: over for everyone, not just this screen. */
  end(id: number, screen: string): void {
    this.queue(id, screen, (s) => finished(s, screen));
  }

  /**
   * Re-reads every session a local screen is in. A push missed while the
   * connection was down is not replayed (`channel-events.ts`), so this is how
   * a tab that kept playing learns what it missed. A read made while a move
   * of this server's is on its way may predate it, so it is set aside.
   */
  async recheck(): Promise<void> {
    if (!this.channel.available() || this.sender.busy) return;
    for (const [id, session] of [...this.known]) {
      if (this.sender.pending(id) || !session.members.some((m) => this.local.has(m.screen))) continue;
      const text = await this.channel.read(id).catch(() => undefined);
      if (text === undefined || this.sender.pending(id)) continue;
      const read = text === null ? null : parseSession(text);
      // Compared as written here: Kotlin and JS print the same numbers differently.
      if (!read) this.forget(id);
      else if (!same(read, session)) this.heard(id, writeSession(read));
    }
  }

  private queue(id: number, screen: string, move: Move): Session | null {
    const current = this.sender.view(id);
    if (!current || current.ended) return null;
    this.local.add(screen);
    this.sender.queue(id, move);
    return this.tell(id);
  }

  private keep(id: number, session: Session): void {
    this.known.set(id, session);
    this.tell(id);
  }

  /** Tells every tab what this server holds the session to be; `null` once it is over. */
  private tell(id: number): Session | null {
    const session = this.sender.view(id);
    const shown = session && !session.ended ? session : null;
    this.announce(id, shown);
    return shown;
  }

  /** The session is over here: forgotten, and every tab told. */
  private forget(id: number): null {
    this.known.delete(id);
    this.announce(id, null);
    return null;
  }
}
