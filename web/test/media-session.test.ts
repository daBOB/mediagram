/** The OS lock screen and media keys, driven through a stand-in for `navigator.mediaSession`. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { clearLockScreen, showOnLockScreen } from "../public/lib/playback/media-session.js";

class FakeMetadata {
  constructor(public init: { title: string; artwork?: { src: string }[] }) {
    Object.assign(this, init);
  }
}

const handlers = new Map<string, (details?: unknown) => void>();
const session: { metadata: unknown; setActionHandler: (a: string, h: (d?: unknown) => void) => void } = {
  metadata: undefined,
  setActionHandler: (action, handler) => void handlers.set(action, handler),
};

beforeEach(() => {
  handlers.clear();
  session.metadata = undefined;
  Object.assign(globalThis, { navigator: { mediaSession: session }, MediaMetadata: FakeMetadata });
});
afterEach(() => {
  Object.assign(globalThis, { navigator: {} });
});

test("a book puts its title and whole cover on the lock screen and wires all five actions", () => {
  const video = { currentTime: 10, duration: 500, paused: true, play() { this.paused = false; }, pause() { this.paused = true; } };
  showOnLockScreen({ poster: "x" } as never, video as never, "Die unendliche Geschichte");
  const meta = session.metadata as { title: string; artwork: { src: string }[] };
  expect(meta.title).toBe("Die unendliche Geschichte");
  expect(meta.artwork[0]?.src).toBe("/api/posters/x.jpg");
  expect([...handlers.keys()].sort()).toEqual(["pause", "play", "seekbackward", "seekforward", "seekto"]);

  handlers.get("seekto")?.({ seekTime: 120 });
  expect(video.currentTime).toBe(120);
  handlers.get("seekforward")?.({});
  expect(video.currentTime).toBe(135);
  handlers.get("seekbackward")?.({ seekOffset: 5 });
  expect(video.currentTime).toBe(130);
});

test("a title with no cover carries no artwork, and stopping clears the lock screen", () => {
  const video = { currentTime: 0 };
  showOnLockScreen({ poster: null } as never, video as never, "A film");
  expect((session.metadata as { artwork?: unknown[] }).artwork ?? []).toEqual([]);
  clearLockScreen();
  expect(session.metadata).toBeNull();
});

test("without a media session both are no-ops", () => {
  Object.assign(globalThis, { navigator: {} });
  expect(() => showOnLockScreen({} as never, {} as never, "x")).not.toThrow();
  expect(() => clearLockScreen()).not.toThrow();
});
