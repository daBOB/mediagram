/** A library channel for watch together that is a map of message ids to texts. Nothing here reaches Telegram. */

import type { EditOutcome, TogetherChannel } from "../src/together/channel";
import { parseSession, type Session } from "../public/lib/together/session.js";

export function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

/** Lets every queued promise run. */
export const settle = () => new Promise((done) => setTimeout(done, 0));

export class FakeChannel implements TogetherChannel {
  readonly messages = new Map<number, string>();
  /** Pushed out of the newest 100 by a busy channel: readable by id only. */
  readonly old = new Set<number>();
  readonly sent: string[] = [];
  readonly edits: Session[] = [];
  readonly removed: number[] = [];
  readonly reads: number[] = [];
  /** Answers for the next edits, in order; "ok" once they run out. */
  readonly outcomes: (EditOutcome | Promise<EditOutcome>)[] = [];
  failReads = false;
  up = true;
  private nextId = 900;

  available() { return this.up; }
  async recent() {
    if (this.failReads) throw new Error("offline");
    return [...this.messages].filter(([id]) => !this.old.has(id)).sort(([a], [b]) => b - a).map(([id, text]) => ({ id, text }));
  }
  async read(id: number) {
    this.reads.push(id);
    if (this.failReads) throw new Error("offline");
    return this.messages.get(id) ?? null;
  }
  async send(text: string) {
    this.sent.push(text);
    this.messages.set(++this.nextId, text);
    return this.nextId;
  }
  async edit(id: number, text: string) {
    this.edits.push(parseSession(text)!);
    const outcome = await (this.outcomes.shift() ?? "ok");
    if (outcome === "ok") this.messages.set(id, text);
    return outcome;
  }
  async remove(id: number) {
    this.removed.push(id);
    this.messages.delete(id);
  }
}
