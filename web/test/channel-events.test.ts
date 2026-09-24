/**
 * The teleproto adapter and the listener around the shared classifier.
 * Classifying itself is pinned by `channel-updates-fixtures.test.ts`; this
 * covers what only the web has: reading teleproto's classes, and turning a
 * burst of updates into one callback per window.
 */

import { describe, expect, spyOn, test } from "bun:test";
import { Api } from "teleproto";

import { listenForLibraryEvents, toChannelUpdate, type Clock, type UpdateSource } from "../src/telegram/channel-events";
import type { LibraryEvent } from "../src/telegram/updates";

const CHANNEL = 3816522481;
const OTHER_CHANNEL = 42;
const ME = "dev-web";

function peer(channel: number) {
  return new Api.PeerChannel({ channelId: channel as never });
}

function message(text: string, channel = CHANNEL) {
  return new Api.Message({ id: 7, peerId: peer(channel), message: text, date: 0 } as never);
}

function newMessage(text: string, channel = CHANNEL) {
  return new Api.UpdateNewChannelMessage({ message: message(text, channel), pts: 0, ptsCount: 0 });
}

function edited(text: string, channel = CHANNEL) {
  return new Api.UpdateEditChannelMessage({ message: message(text, channel), pts: 0, ptsCount: 0 });
}

function pinned(isPinned: boolean) {
  return new Api.UpdatePinnedChannelMessages({ channelId: CHANNEL as never, messages: [7], pts: 0, ptsCount: 0, pinned: isPinned });
}

const stateBy = (device: string) => `#mlib-state v=1 device=${device}`;

describe("toChannelUpdate", () => {
  test("a message edit keeps its channel and caption", () => {
    expect(toChannelUpdate(edited(stateBy("dev-tv")))).toEqual({ kind: "edit", channel: CHANNEL, caption: stateBy("dev-tv") });
  });

  test("a pin notice is a service message, not a caption", () => {
    const notice = new Api.MessageService({ id: 8, peerId: peer(CHANNEL), date: 0, action: new Api.MessageActionPinMessage() } as never);
    const update = new Api.UpdateNewChannelMessage({ message: notice, pts: 0, ptsCount: 0 });
    expect(toChannelUpdate(update)).toEqual({ kind: "new", channel: CHANNEL, service: true });
  });

  test("pin and unpin say which", () => {
    expect(toChannelUpdate(pinned(true))).toEqual({ kind: "pinned", channel: CHANNEL, pinned: true });
    expect(toChannelUpdate(pinned(false))).toEqual({ kind: "pinned", channel: CHANNEL, pinned: false });
  });

  test("anything else is not a channel update", () => {
    expect(toChannelUpdate(new Api.UpdateReadChannelInbox({ channelId: CHANNEL as never, maxId: 1, stillUnreadCount: 0, pts: 0 }))).toBeNull();
    expect(toChannelUpdate({})).toBeNull();
  });
});

/** A client that remembers its one handler, and a clock that only moves when told. */
function harness() {
  let handler: ((update: unknown) => void) | null = null;
  const source: UpdateSource = {
    addEventHandler: (h) => {
      handler = h;
    },
    removeEventHandler: (h) => {
      if (handler === h) handler = null;
    },
  };
  let now = 0;
  const timers = new Map<number, { at: number; run: () => void }>();
  let nextId = 1;
  const clock: Clock = {
    now: () => now,
    setTimeout: (run, ms) => {
      timers.set(nextId, { at: now + ms, run });
      return nextId++;
    },
    clearTimeout: (id) => void timers.delete(id as number),
  };
  const events: LibraryEvent[] = [];
  const stop = listenForLibraryEvents(source, { channel: CHANNEL, ownDevice: ME }, (e) => events.push(e), clock, 5000);
  return {
    events,
    stop,
    push: (update: unknown) => handler?.(update),
    listening: () => handler !== null,
    pendingTimers: () => timers.size,
    advance(ms: number) {
      now += ms;
      for (const [id, timer] of [...timers].sort((a, b) => a[1].at - b[1].at)) {
        if (timer.at <= now) {
          timers.delete(id);
          timer.run();
        }
      }
    },
  };
}

describe("listenForLibraryEvents", () => {
  test("another device's write is one state event, after the window", () => {
    const h = harness();
    h.push(edited(stateBy("dev-tv")));
    h.advance(4999);
    expect(h.events).toEqual([]);
    h.advance(1);
    expect(h.events).toEqual(["state"]);
  });

  test("this device's own write is not news", () => {
    const h = harness();
    h.push(edited(stateBy(ME)));
    h.advance(10_000);
    expect(h.events).toEqual([]);
    expect(h.pendingTimers()).toBe(0);
  });

  test("a burst is folded into one event per kind", () => {
    const h = harness();
    h.push(edited(stateBy("dev-tv")));
    h.advance(1000);
    h.push(edited(stateBy("dev-phone")));
    h.push(newMessage("#mlib-index v=2\n{}"));
    h.push(pinned(true));
    h.advance(4000);
    expect(h.events).toEqual(["state"]);
    h.advance(1000);
    expect(h.events).toEqual(["state", "index"]);
  });

  test("another channel, a pin notice and an unpin change nothing", () => {
    const h = harness();
    h.push(edited(stateBy("dev-tv"), OTHER_CHANNEL));
    h.push(pinned(false));
    h.advance(10_000);
    expect(h.events).toEqual([]);
  });

  test("a failing callback does not stop later events", () => {
    let calls = 0;
    let handler: ((u: unknown) => void) | null = null;
    const source: UpdateSource = { addEventHandler: (h) => void (handler = h), removeEventHandler: () => {} };
    let queued: (() => void) | null = null;
    const clock: Clock = { now: () => 0, setTimeout: (run) => ((queued = run), 1), clearTimeout: () => {} };
    listenForLibraryEvents(source, { channel: CHANNEL, ownDevice: ME }, () => {
      calls += 1;
      throw new Error("boom");
    }, clock, 0);
    handler!(edited(stateBy("dev-tv")));
    queued!();
    handler!(edited(stateBy("dev-tv")));
    queued!();
    expect(calls).toBe(2);
  });

  test("stopping removes the handler and the pending timer", () => {
    const h = harness();
    h.push(edited(stateBy("dev-tv")));
    h.stop();
    expect(h.listening()).toBe(false);
    expect(h.pendingTimers()).toBe(0);
    h.advance(10_000);
    expect(h.events).toEqual([]);
  });

  test.each([Symbol("failed"), Object.create(null)])("an unprintable callback failure preserves due and future delivery (%s)", (failure) => {
    let handler!: (update: unknown) => void;
    let queued!: () => void;
    const events: LibraryEvent[] = [];
    const source: UpdateSource = { addEventHandler: (run) => { handler = run; }, removeEventHandler: () => {} };
    const clock: Clock = { now: () => 0, setTimeout: (run) => { queued = run; return 1; }, clearTimeout: () => {} };
    const warning = spyOn(console, "warn").mockImplementation(() => {});
    const stop = listenForLibraryEvents(source, { channel: CHANNEL, ownDevice: ME }, (event) => {
      events.push(event);
      if (events.length === 1) throw failure;
    }, clock, 0);
    try {
      handler(edited(stateBy("dev-tv")));
      handler(pinned(true));
      expect(() => queued()).not.toThrow();
      expect(events).toEqual(["state", "index"]);
      handler(edited(stateBy("dev-tv")));
      queued();
      expect(events).toEqual(["state", "index", "state"]);
      expect(warning.mock.calls).toEqual([[
        `updates: acting on state failed: ${typeof failure === "symbol" ? "Symbol(failed)" : "unprintable rejection"}`,
      ]]);
    } finally { stop(); warning.mockRestore(); }
  });
});
