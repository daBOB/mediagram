/**
 * The web server's watch-together hub, against a channel that is a map of
 * message ids to texts. Nothing here reaches Telegram.
 */

import { describe, expect, test } from "bun:test";

import { TogetherHub, type EditOutcome } from "../src/together/hub";
import { parseSession, writeSession, type Member, type Session } from "../public/lib/together/session.js";
import { joined } from "../public/lib/together/moves.js";
import { FakeChannel } from "./together-fixture";

const NOW = 1_791_500_000_000;
const A: Member = { screen: "tab-a", label: "Browser" };
const TV: Member = { screen: "tv-1", label: "TV" };
const FILM = { set: "film-1", playing: true, pos: 10, rate: 1 };

const foreign = (over: Partial<Session> = {}): Session => ({
  session: "s-tv", ...FILM, at: NOW, by: TV.screen, members: [TV], ended: false, ...over,
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

const settle = () => new Promise((done) => setTimeout(done, 0));

function harness() {
  const channel = new FakeChannel();
  const announced: [number, Session | null][] = [];
  const slept: number[] = [];
  let wake: Promise<void> = Promise.resolve();
  const hub = new TogetherHub(channel, (id, session) => announced.push([id, session]), () => NOW, (ms) => {
    slept.push(ms);
    return wake;
  });
  const durationOf = (set: string) => (set === "film-1" ? 100 : null);
  return {
    channel, hub, announced, slept, durationOf,
    holdSleep() { const gate = deferred<void>(); wake = gate.promise; return gate; },
    start: () => hub.start(A, FILM, durationOf).then(({ live }) => live.id),
  };
}

describe("TogetherHub", () => {
  test("start sends one message and announces it; a second start meets the first", async () => {
    const h = harness();
    const first = await h.hub.start(A, FILM, h.durationOf);
    expect(first.conflict).toBe(false);
    expect(first.live.session).toMatchObject({ ...FILM, at: NOW, by: A.screen, members: [A], ended: false });
    expect(h.channel.sent).toEqual([writeSession(first.live.session)]);
    expect(h.announced).toEqual([[first.live.id, first.live.session]]);
    const second = await h.hub.start(TV, { ...FILM, set: "film-2" }, h.durationOf);
    expect(second).toEqual({ live: first.live, conflict: true });
    expect(h.channel.sent).toHaveLength(1);
  });

  test("live skips and removes a stale session, and answers the newest live one", async () => {
    const h = harness();
    h.channel.messages.set(949, writeSession(foreign()));
    h.channel.messages.set(950, writeSession(foreign({ session: "s-2", set: "film-2" })));
    h.channel.messages.set(951, writeSession(foreign({ session: "s-3", pos: 300 })));
    expect(await h.hub.live(h.durationOf)).toEqual({ id: 950, session: foreign({ session: "s-2", set: "film-2" }) });
    expect(h.channel.removed).toEqual([951]);
  });

  test("a session heard by push and pushed out of the newest 100 is read by id, so no second one starts", async () => {
    const h = harness();
    h.channel.messages.set(800, writeSession(foreign()));
    h.channel.old.add(800);
    h.hub.heard(800, writeSession(foreign()));
    const { live, conflict } = await h.hub.start(A, FILM, h.durationOf);
    expect({ id: live.id, conflict }).toEqual({ id: 800, conflict: true });
    expect(h.channel.sent).toEqual([]);
  });

  test("moves made while an edit is on its way: only the newest is sent", async () => {
    const h = harness();
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    h.hub.act(id, A.screen, { playing: false });
    h.hub.act(id, A.screen, { pos: 50 });
    const last = h.hub.act(id, A.screen, { rate: 2 });
    held.resolve("ok");
    await settle();
    expect(h.channel.edits).toHaveLength(2);
    expect(h.channel.edits[1]).toEqual(last!);
    expect(last).toMatchObject({ playing: false, pos: 50, rate: 2 });
  });

  test("a flood wait sends the newest own write after it, with a joiner heard meanwhile", async () => {
    const h = harness();
    const id = await h.start();
    const wait = h.holdSleep();
    h.channel.outcomes.push({ wait: 5 });
    const paused = h.hub.act(id, A.screen, { playing: false, pos: 42 })!;
    await settle();
    expect(h.slept).toEqual([5000]);
    // The TV still sees the message as started, since the pause was refused.
    const tvJoin = joined(parseSession(h.channel.messages.get(id)!)!, TV);
    h.hub.heard(id, writeSession(tvJoin));
    expect(h.announced.at(-1)).toEqual([id, tvJoin]);
    wait.resolve();
    await settle();
    expect(h.channel.edits).toHaveLength(2);
    expect(h.channel.edits[1]).toEqual({ ...paused, members: [A, TV] });
  });

  test("another screen's edit is news; this server's own echo while its newer write waits is not", async () => {
    const h = harness();
    h.hub.heard(950, writeSession(foreign()));
    expect(h.announced).toEqual([[950, foreign()]]);
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    const first = h.hub.act(id, A.screen, { playing: false })!;
    const second = h.hub.act(id, A.screen, { pos: 30 })!;
    const before = h.announced.length;
    h.hub.heard(id, writeSession(first));
    expect(h.announced).toHaveLength(before);
    // Another screen's move while the own write still waits is kept, and the own write still goes.
    const tvRate = { ...first, rate: 1.5, by: TV.screen, members: [A, TV] };
    h.hub.heard(id, writeSession(tvRate));
    expect(h.announced.at(-1)).toEqual([id, tvRate]);
    held.resolve("ok");
    await settle();
    expect(h.channel.edits[1]).toEqual({ ...second, members: [A, TV] });
  });

  test("an own write waiting when another screen ends the session is dropped", async () => {
    const h = harness();
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    const first = h.hub.act(id, A.screen, { playing: false })!;
    h.hub.act(id, A.screen, { pos: 30 });
    h.hub.heard(id, writeSession({ ...first, by: TV.screen, ended: true }));
    expect(h.announced.at(-1)).toEqual([id, null]);
    held.resolve("ok");
    await settle();
    expect(h.channel.edits).toHaveLength(1);
  });

  test("an edit to a deleted message ends the session here", async () => {
    const h = harness();
    const id = await h.start();
    h.channel.outcomes.push("gone");
    h.hub.act(id, A.screen, { playing: false });
    await settle();
    expect(h.announced.at(-1)).toEqual([id, null]);
    expect(h.hub.act(id, A.screen, { playing: true })).toBeNull();
  });

  test("the last to leave writes the end and deletes the message", async () => {
    const h = harness();
    const id = await h.start();
    await h.hub.leave(id, A.screen);
    expect(h.channel.edits).toEqual([expect.objectContaining({ members: [], ended: true, by: A.screen })]);
    expect(h.channel.removed).toEqual([id]);
    expect(h.announced.at(-1)).toEqual([id, null]);
  });

  test("the end of the last title ends it for everyone still in it", async () => {
    const h = harness();
    const id = await h.start();
    h.hub.heard(id, writeSession(joined(parseSession(h.channel.messages.get(id)!)!, TV)));
    await h.hub.end(id, A.screen);
    expect(h.channel.edits).toEqual([expect.objectContaining({ members: [A, TV], playing: false, ended: true })]);
    expect(h.channel.removed).toEqual([id]);
    expect(h.announced.at(-1)).toEqual([id, null]);
  });

  test("join adds this screen to a session it read, and a deleted one cannot be joined", async () => {
    const h = harness();
    h.channel.messages.set(950, writeSession(foreign()));
    const live = await h.hub.join(950, A);
    expect(live).toEqual({ id: 950, session: { ...foreign(), by: A.screen, members: [TV, A] } });
    await settle();
    expect(h.channel.edits).toEqual([live!.session]);
    expect(await h.hub.join(999, A)).toBeNull();
  });

  test("recheck reads the sessions a local screen is in, and tells what changed or vanished", async () => {
    const h = harness();
    const id = await h.start();
    h.hub.heard(950, writeSession(foreign()));
    const paused = foreign({ session: "s-a", playing: false, members: [A, TV] });
    h.channel.messages.set(id, writeSession(paused));
    await h.hub.recheck();
    expect(h.channel.reads).toEqual([id]);
    expect(h.announced.at(-1)).toEqual([id, paused]);
    h.channel.messages.delete(id);
    await h.hub.recheck();
    expect(h.announced.at(-1)).toEqual([id, null]);
  });

  test("a recheck that cannot read tells nothing and does not throw", async () => {
    const h = harness();
    await h.start();
    h.channel.failReads = true;
    const before = h.announced.length;
    await h.hub.recheck();
    expect(h.announced).toHaveLength(before);
  });
});
