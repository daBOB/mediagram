/**
 * `library-session.js` against a fake port and a fake profile — no DOM, no
 * `Bun.build` bundle. What is asserted here is the exact behaviour that used
 * to live in `browser-application.test.ts`: coalescing, the hold/release
 * that defers a redraw, the kids filter, and the guarantee that a body
 * `groupLibrary` cannot make sense of never becomes the current catalog.
 */

import { describe, expect, test } from "bun:test";
import { createLibrarySession } from "../public/lib/library-session.js";
import type { LibrarySessionState } from "../public/lib/library-session.js";
import { catalogSet } from "./support/catalog-set";
import { fakeLibraryPort } from "./support/library-session-port";
import { deferred, settle } from "./support/player-environment";

const film = (setId: string, over: Record<string, unknown> = {}) => catalogSet({ setId, ...over });

/** The two calls the session reads from `watch-state.js`, held by a test. */
function fakeState(
  profile: { kids?: boolean; kidsAge?: number | null } | null = null,
  marks: Record<string, number> = {},
): LibrarySessionState & { set(next: typeof profile): void } {
  let current = profile;
  return {
    profile: () => current,
    kidsMarks: () => new Map(Object.entries(marks)),
    set(next) { current = next; },
  };
}

/** A listener that counts its own calls, for `onData`/`onRedraw`/`remoteState`. */
function spy() {
  let calls = 0;
  const fn = () => { calls++; };
  // `Object.assign` would read a getter once and freeze its value; defined
  // this way `calls` stays live as the closure counts further calls.
  Object.defineProperty(fn, "calls", { get: () => calls });
  return fn as typeof fn & { calls: number };
}

function session(over: Partial<Parameters<typeof createLibrarySession>[0]> = {}) {
  const fake = fakeLibraryPort(JSON.stringify([film("First")]));
  const state = fakeState();
  const remoteState = spy();
  const built = createLibrarySession({ port: fake.port, state, remoteState, ...over });
  return { fake, state, remoteState, session: built };
}

test("concurrent catalog events coalesce into one follow-up read, and none is dropped", async () => {
  const { fake, session: lib } = session();
  await lib.start();
  const first = deferred<string>();
  fake.queueFetch(first.promise);
  fake.resolveNextWith(JSON.stringify([film("Latest")]));
  fake.fire("catalog"); fake.fire("catalog"); fake.fire("open");
  // Two so far: the first load, and the one read the three fires share.
  expect(fake.fetches).toBe(2);
  first.resolve(JSON.stringify([film("Intermediate")]));
  await settle();
  // Exactly one follow-up, for a body newer than the one it coalesced from.
  expect(fake.fetches).toBe(3);
  expect(lib.current().byId.has("Intermediate")).toBe(false);
  expect(lib.current().byId.has("Latest")).toBe(true);
});

test("a change while held is delivered once on the last release", async () => {
  const { session: lib } = session();
  await lib.start();
  const redraws = spy();
  lib.onRedraw(redraws);
  const releasePlayer = lib.hold();
  const releaseEditing = lib.hold();
  lib.invalidate();
  lib.invalidate();
  expect(redraws.calls).toBe(0);
  releasePlayer();
  expect(redraws.calls).toBe(0);
  releaseEditing();
  expect(redraws.calls).toBe(1);
});

test("a second release() of the same hold is ignored", async () => {
  const { session: lib } = session();
  await lib.start();
  const redraws = spy();
  lib.onRedraw(redraws);
  const releaseA = lib.hold();
  const releaseB = lib.hold();
  lib.invalidate();
  releaseA();
  // Repeated: a hold that already released must not count down a second
  // time and let B's still-open hold look like the last one out.
  releaseA();
  expect(redraws.calls).toBe(0);
  releaseB();
  expect(redraws.calls).toBe(1);
});

test("a delivered redraw is not delivered again by a later release", async () => {
  const { session: lib } = session();
  await lib.start();
  const redraws = spy();
  lib.onRedraw(redraws);
  const releaseFirst = lib.hold();
  lib.invalidate();
  releaseFirst();
  expect(redraws.calls).toBe(1);
  // Nothing new is owed; a later, unrelated hold releasing must not replay
  // the redraw already delivered above.
  const releaseSecond = lib.hold();
  releaseSecond();
  expect(redraws.calls).toBe(1);
});

test("a catalog event before the first load finishes is ignored", async () => {
  // Deliberate, not a gap in "never a notice dropped": the first load draws
  // the page itself once it lands, so a stream event racing ahead of it has
  // nothing to add and earns no read of its own.
  const fake = fakeLibraryPort();
  const first = deferred<string>();
  fake.queueFetch(first.promise);
  const lib = createLibrarySession({ port: fake.port, state: fakeState(), remoteState: () => {} });
  const starting = lib.start();
  fake.fire("catalog");
  first.resolve(JSON.stringify([film("First")]));
  await starting;
  await settle();
  expect(fake.fetches).toBe(1);
});

test("counts reach onData at once, even while a redraw is held", async () => {
  const { fake, session: lib } = session();
  await lib.start();
  const data = spy();
  const redraws = spy();
  lib.onData(data);
  lib.onRedraw(redraws);
  const release = lib.hold();
  fake.resolveNextWith(JSON.stringify([film("Second")]));
  fake.fire("catalog");
  await settle();
  expect(data.calls).toBe(1);
  expect(lib.current().byId.has("Second")).toBe(true);
  expect(redraws.calls).toBe(0);
  release();
  expect(redraws.calls).toBe(1);
});

test("hide/show owns one event stream, and becoming visible refreshes remote state", () => {
  const { fake, remoteState, session: lib } = session();
  void lib;
  expect(fake.streamOpens).toBe(1);
  expect(fake.streamOpen).toBe(true);
  fake.setVisible(false);
  expect(fake.streamOpen).toBe(false);
  expect(remoteState.calls).toBe(0);
  fake.setVisible(true);
  expect(fake.streamOpens).toBe(2);
  expect(remoteState.calls).toBe(1);
  // A redundant "visible" (already visible) does not open a second stream.
  fake.setVisible(true);
  expect(fake.streamOpens).toBe(2);
  expect(remoteState.calls).toBe(2);
});

describe("a kids profile", () => {
  const rated = (setId: string, fsk: string | null) => film(setId, { fsk });

  test("filters on start and again on a refresh", async () => {
    const fake = fakeLibraryPort(JSON.stringify([rated("Family", "6"), rated("Grown", "16")]));
    const lib = createLibrarySession({ port: fake.port, state: fakeState({ kids: true }), remoteState: () => {} });
    await lib.start();
    expect(lib.current().library.movies.map((set) => set.setId)).toEqual(["Family"]);

    fake.resolveNextWith(JSON.stringify([rated("Family", "6"), rated("Grown", "16"), rated("Also", "0")]));
    fake.fire("catalog");
    await settle();
    expect(lib.current().library.movies.map((set) => set.setId).sort()).toEqual(["Also", "Family"]);
  });

  test("reapply() switches profile without a fetch", async () => {
    const fake = fakeLibraryPort(JSON.stringify([rated("Family", "6"), rated("Grown", "16")]));
    const state = fakeState(null);
    const lib = createLibrarySession({ port: fake.port, state, remoteState: () => {} });
    await lib.start();
    expect(lib.current().library.movies).toHaveLength(2);
    const before = fake.fetches;

    state.set({ kids: true });
    lib.reapply();
    expect(lib.current().library.movies.map((set) => set.setId)).toEqual(["Family"]);
    expect(fake.fetches).toBe(before);
  });

  test("each kid sees up to its own limit, hand marks included", async () => {
    const fake = fakeLibraryPort(JSON.stringify([rated("Six", "6"), rated("Twelve", "12"), rated("FromSix", null), rated("FromTwelve", null)]));
    const state = fakeState({ kids: true, kidsAge: 6 }, { FromSix: 6, FromTwelve: 12 });
    const lib = createLibrarySession({ port: fake.port, state, remoteState: () => {} });
    await lib.start();
    expect(lib.current().library.movies.map((set) => set.setId).sort()).toEqual(["FromSix", "Six"]);
    state.set({ kids: true, kidsAge: 12 });
    lib.reapply();
    expect(lib.current().library.movies.map((set) => set.setId).sort()).toEqual(["FromSix", "FromTwelve", "Six", "Twelve"]);
  });
});

test("onData says when a new catalog arrived, and only then", async () => {
  // The colophon states the catalogue's publish date: worth asking the server
  // again for a new catalogue, not for a profile switch or a watch change —
  // and not for `start()`'s own first load, which has no earlier one to say
  // arrived over: a caller that also asks the server on `catalog: true` would
  // otherwise ask it twice at startup for the one thing it just read itself.
  const { fake, state, session: lib } = session();
  const seen: boolean[] = [];
  lib.onData((change) => { seen.push(change.catalog); });
  await lib.start();
  fake.resolveNextWith(JSON.stringify([film("First"), film("Second")]));
  fake.fire("catalog");
  await settle();
  state.set({ kids: true });
  lib.reapply();
  lib.invalidate();
  expect(seen[0]).toBe(false);
  expect(seen[1]).toBe(true);
  expect(seen.slice(2).every((catalog) => catalog === false)).toBe(true);
});

test("an unchanged body is no change", async () => {
  const { fake, session: lib } = session();
  await lib.start();
  const data = spy();
  const redraws = spy();
  lib.onData(data);
  lib.onRedraw(redraws);
  fake.fire("catalog");
  await settle();
  expect(data.calls).toBe(0);
  expect(redraws.calls).toBe(0);
});

test("a body that throws in grouping is not committed", async () => {
  const { fake, session: lib } = session();
  await lib.start();
  fake.resolveNextWith("[null]");
  fake.fire("catalog");
  await settle();
  expect(lib.current().byId.has("First")).toBe(true);
  expect(lib.current().library.movies).toHaveLength(1);
});

test("a failed refresh keeps what is showing", async () => {
  const { fake, session: lib } = session();
  await lib.start();
  fake.failNextWith(new Error("offline"));
  fake.fire("catalog");
  await settle();
  expect(lib.current().byId.has("First")).toBe(true);
  expect(lib.current().library.movies).toHaveLength(1);
});

test("start() rejects on a failed first read", async () => {
  const fake = fakeLibraryPort();
  fake.failNextWith(new Error("offline"));
  const lib = createLibrarySession({ port: fake.port, state: fakeState(), remoteState: () => {} });
  await expect(lib.start()).rejects.toThrow("offline");
});
