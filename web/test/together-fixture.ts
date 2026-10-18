/** A library channel for watch together that is a map of message ids to texts. Nothing here reaches Telegram. */

import { createRouter } from "../src/routes";
import { TogetherHub } from "../src/together/hub";
import type { EditOutcome, TogetherChannel } from "../src/together/channel";
import type { PlayerRequest } from "../src/http/contracts";
import { emptyIndex } from "./index-fixture";
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
  /** Every edit throws, as one does while Telegram can't be reached; it is still recorded. */
  failEdits = false;
  /** Reads answer once this settles, with what the channel holds then. */
  readGate: Promise<void> = Promise.resolve();
  up = true;
  private nextId = 900;

  available() { return this.up; }
  async recent() {
    if (this.failReads) throw new Error("offline");
    return [...this.messages].filter(([id]) => !this.old.has(id)).sort(([a], [b]) => b - a).map(([id, text]) => ({ id, text }));
  }
  async read(id: number) {
    this.reads.push(id);
    await this.readGate;
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
    if (this.failEdits) throw new Error("offline");
    const outcome = await (this.outcomes.shift() ?? "ok");
    if (outcome === "ok") this.messages.set(id, text);
    return outcome;
  }
  async remove(id: number) {
    this.removed.push(id);
    this.messages.delete(id);
  }
}

/** `/api/together` at the router, over a hub whose channel is a fake. */
function db() {
  const index = emptyIndex();
  index.run(
    `INSERT INTO sets(set_id, kind, title, container, duration, total, part_count, status, created_at, spec_version)
     VALUES ('film-1', 'movie', 'A Film', 'mp4', 2400, 10, 1, 'complete', 1700000000, 3)`,
  );
  index.run(`INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
     VALUES ('film-1', 0, 0, 10, -1001, 100, 'done')`);
  return index;
}

const source = { stream: () => new ReadableStream<Uint8Array>({ start: (c) => c.close() }) };

export function harness(withHub = true) {
  const channel = new FakeChannel();
  const hub = new TogetherHub(channel, () => {});
  const route = createRouter({ db: db(), source, together: withHub ? hub : undefined });
  const call = async (method: string, body?: unknown) => {
    const request: PlayerRequest = {
      method, path: "/api/together", range: null,
      ...(body === undefined ? {} : { body: JSON.stringify(body), contentType: "application/json" }),
    };
    const response = await route(request);
    const text = response.body instanceof Uint8Array ? new TextDecoder().decode(response.body) : null;
    return { status: response.status, json: text ? JSON.parse(text) : null };
  };
  return { channel, hub, call };
}
