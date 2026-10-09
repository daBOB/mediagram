/**
 * The web server's watch-together hub, against a channel that is a map of
 * message ids to texts. Nothing here reaches Telegram.
 */

import { describe, expect, test } from "bun:test";

import { TogetherHub } from "../src/together/hub";
import type { EditOutcome } from "../src/together/channel";
import { parseSession, writeSession, type Member, type Session } from "../public/lib/together/session.js";
import { joined } from "../public/lib/together/moves.js";
import { FakeChannel, deferred, settle } from "./together-fixture";

const NOW = 1_791_500_000_000;
const A: Member = { screen: "tab-a", label: "Browser" };
const B: Member = { screen: "tab-b", label: "Browser" };
const TV: Member = { screen: "tv-1", label: "TV" };
const FILM = { set: "film-1", playing: true, pos: 10, rate: 1 };

const foreign = (over: Partial<Session> = {}): Session => ({
  session: "s-tv", ...FILM, at: NOW, by: TV.screen, members: [TV], ended: false, ...over,
});

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

  test("two starts from this server at once: the second meets the first", async () => {
    const h = harness();
    const [first, second] = await Promise.all([h.hub.start(A, FILM, h.durationOf), h.hub.start(B, FILM, h.durationOf)]);
    expect(first.conflict).toBe(false);
    expect(second).toEqual({ live: first.live, conflict: true });
    expect(h.channel.sent).toHaveLength(1);
  });

  test("a read or a join while an edit is on its way builds on that edit, never on the text before it", async () => {
    const h = harness();
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    const paused = h.hub.act(id, A.screen, { playing: false, pos: 42 })!;
    expect((await h.hub.live(h.durationOf))?.session).toEqual(paused);
    const live = await h.hub.join(id, B);
    expect(live?.session).toEqual({ ...paused, by: B.screen, members: [A, B] });
    held.resolve("ok");
    await settle();
    expect(h.channel.edits).toEqual([paused, live!.session]);
    expect(h.channel.reads).toEqual([]);
  });

  test("a join and a move queued behind it both land", async () => {
    const h = harness();
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    h.hub.act(id, A.screen, { playing: false });
    await h.hub.join(id, B);
    const sought = h.hub.act(id, B.screen, { pos: 90 })!;
    held.resolve("ok");
    await settle();
    expect(h.channel.edits).toHaveLength(2);
    expect(h.channel.edits[1]).toEqual(sought);
    expect(sought).toMatchObject({ playing: false, pos: 90, by: B.screen, members: [A, B] });
  });

  test("a move made while an edit is refused goes after it, so the newest wins", async () => {
    const h = harness();
    const id = await h.start();
    const refused = deferred<EditOutcome>();
    h.channel.outcomes.push(refused.promise);
    h.hub.act(id, A.screen, { playing: false, pos: 42 });
    const sought = h.hub.act(id, A.screen, { pos: 60 })!;
    refused.resolve({ wait: 5 });
    await settle();
    expect(h.slept).toEqual([5000]);
    expect(h.channel.edits).toHaveLength(2);
    expect(h.channel.edits[1]).toEqual(sought);
    expect(sought).toMatchObject({ playing: false, pos: 60 });
  });

  test("a late echo of this server's earlier edit does not undo the one on its way", async () => {
    const h = harness();
    const id = await h.start();
    const first = h.hub.act(id, A.screen, { playing: false, pos: 20 })!;
    await settle();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    const second = h.hub.act(id, B.screen, { pos: 70 })!;
    h.hub.heard(id, writeSession(first));
    const live = await h.hub.join(id, TV);
    expect(live?.session).toEqual({ ...second, by: TV.screen, members: [A, TV] });
    held.resolve("ok");
    await settle();
    expect(h.channel.edits.at(-1)).toMatchObject({ pos: 70, members: [A, TV] });
  });

  test("a recheck read that predates an edit on its way is set aside", async () => {
    const h = harness();
    const id = await h.start();
    const tvPlay = foreign({ session: parseSession(h.channel.messages.get(id)!)!.session, members: [A, TV] });
    h.channel.messages.set(id, writeSession(tvPlay));
    const gate = deferred<void>();
    h.channel.readGate = gate.promise;
    const checking = h.hub.recheck();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    const paused = h.hub.act(id, A.screen, { playing: false, pos: 42 })!;
    gate.resolve();
    await checking;
    expect(h.announced.at(-1)).toEqual([id, paused]);
    held.resolve("ok");
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
    // The tabs keep their own state through the wait, and see who joined.
    expect(h.announced.at(-1)).toEqual([id, { ...paused, members: [A, TV] }]);
    wait.resolve();
    await settle();
    expect(h.channel.edits).toHaveLength(2);
    expect(h.channel.edits[1]).toEqual({ ...paused, members: [A, TV] });
  });

  test("another screen's edit heard while an edit is on its way survives that edit being refused", async () => {
    const h = harness();
    const id = await h.start();
    const refused = deferred<EditOutcome>();
    h.channel.outcomes.push(refused.promise);
    const paused = h.hub.act(id, A.screen, { playing: false, pos: 42 })!;
    h.hub.heard(id, writeSession(joined(parseSession(h.channel.messages.get(id)!)!, TV)));
    refused.resolve({ wait: 5 });
    await settle();
    expect(h.channel.edits[1]).toEqual({ ...paused, members: [A, TV] });
  });

  test("a join held by a flood wait survives another screen's edit meanwhile", async () => {
    const h = harness();
    const id = await h.start();
    const wait = h.holdSleep();
    h.channel.outcomes.push({ wait: 5 });
    await h.hub.join(id, B);
    await settle();
    // The TV pauses the message as it still stands: without the refused join.
    const tvPause = { ...parseSession(h.channel.messages.get(id)!)!, playing: false, by: TV.screen, members: [A, TV] };
    h.hub.heard(id, writeSession(tvPause));
    wait.resolve();
    await settle();
    expect(h.channel.edits[1]).toEqual({ ...tvPause, by: B.screen, members: [A, TV, B] });
  });

  test("a last leave held by a flood wait, raced by a joiner, leaves the session to them", async () => {
    const h = harness();
    const id = await h.start();
    const wait = h.holdSleep();
    h.channel.outcomes.push({ wait: 5 });
    h.hub.leave(id, A.screen);
    await settle();
    expect(h.announced.at(-1)).toEqual([id, null]);
    const tvJoin = joined(parseSession(h.channel.messages.get(id)!)!, TV);
    h.hub.heard(id, writeSession(tvJoin));
    wait.resolve();
    await settle();
    expect(h.channel.edits[1]).toEqual({ ...tvJoin, by: A.screen, members: [TV] });
    expect(h.channel.removed).toEqual([]);
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
    expect(h.announced.at(-1)).toEqual([id, { ...second, members: [A, TV] }]);
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
    h.hub.leave(id, A.screen);
    await settle();
    expect(h.channel.edits).toEqual([expect.objectContaining({ members: [], ended: true, by: A.screen })]);
    expect(h.channel.removed).toEqual([id]);
    expect(h.announced.at(-1)).toEqual([id, null]);
  });

  test.each(["leave", "end"] as const)("a last %s refused by a flood wait is written and deleted once the wait ends", async (op) => {
    const h = harness();
    const id = await h.start();
    h.channel.outcomes.push({ wait: 5 });
    h.hub[op](id, A.screen);
    await settle();
    expect(h.slept).toEqual([5000]);
    expect(h.channel.edits).toEqual([
      expect.objectContaining({ ended: true }),
      expect.objectContaining({ ended: true }),
    ]);
    expect(h.channel.removed).toEqual([id]);
  });

  test.each(["leave", "end"] as const)("a read during a last %s on its way leaves the message to the sender", async (op) => {
    const h = harness();
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    h.hub[op](id, A.screen);
    expect(await h.hub.live(h.durationOf)).toBeNull();
    expect(h.channel.removed).toEqual([]);
    held.resolve("ok");
    await settle();
    expect(h.channel.edits).toEqual([expect.objectContaining({ ended: true })]);
    expect(h.channel.removed).toEqual([id]);
  });

  test("a last leave on its way, raced by a joiner heard meanwhile, does not delete the message", async () => {
    const h = harness();
    const id = await h.start();
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    h.hub.leave(id, A.screen);
    h.hub.heard(id, writeSession(joined(parseSession(h.channel.messages.get(id)!)!, TV)));
    held.resolve("ok");
    await settle();
    expect(h.channel.removed).toEqual([]);
  });

  test("the end of the last title ends it for everyone still in it", async () => {
    const h = harness();
    const id = await h.start();
    h.hub.heard(id, writeSession(joined(parseSession(h.channel.messages.get(id)!)!, TV)));
    h.hub.end(id, A.screen);
    await settle();
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

  test("a remembered id that is no session, as after a library switch, is forgotten after one read", async () => {
    const h = harness();
    h.hub.heard(800, writeSession(foreign()));
    h.channel.messages.set(800, "an ordinary post");
    h.channel.old.add(800);
    await h.hub.live(h.durationOf);
    await h.hub.live(h.durationOf);
    expect(h.channel.reads).toEqual([800]);
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
