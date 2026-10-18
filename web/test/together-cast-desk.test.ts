/**
 * The web server's cast desk: an offer sent, and the TV's answer waited for,
 * against a channel that is a map of message ids to texts. The tests write
 * the TV's side. Nothing here reaches Telegram.
 */

import { describe, expect, test } from "bun:test";

import { TogetherHub } from "../src/together/hub";
import { parseSession, writeSession, type Member, type Session } from "../public/lib/together/session.js";
import { accepted, busied, tracked } from "../public/lib/together/cast.js";
import { FakeChannel, deferred, settle } from "./together-fixture";

const NOW = 1_791_500_000_000;
const ME: Member = { screen: "tab-1", label: "Browser", remote: false };
const TV: Member = { screen: "tv-1", label: "TV", remote: false };
const FILM = { set: "film-1", playing: true, pos: 10, rate: 1 };

function harness() {
  const channel = new FakeChannel();
  const announced: [number, Session | null][] = [];
  const clock = { now: NOW };
  // Every wait ends together, when the test says the five seconds are up.
  const deadline = deferred<void>();
  const hub = new TogetherHub(channel, (id, session) => announced.push([id, session]), () => clock.now, () => deadline.promise);
  return {
    channel, hub, announced, clock,
    timeOut: () => deadline.resolve(),
    /** The TV reads the newest message and writes its answer; `heard` says whether the push reaches the hub. */
    async tv(answer: (s: Session) => Session, heard = true): Promise<number> {
      await settle();
      const id = Math.max(...channel.messages.keys());
      const text = writeSession(answer(parseSession(channel.messages.get(id)!)!));
      channel.messages.set(id, text);
      if (heard) hub.heard(id, text);
      await settle();
      return id;
    },
  };
}

const accept = (s: Session) => accepted(s, TV, NOW + 1_000)!;

describe("CastDesk", () => {
  test("an idle TV's accept answers the offer", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    const id = await h.tv(accept);
    const { answer, live, note } = await offer;
    expect(answer).toBe("on");
    expect(live?.id).toBe(id);
    expect(live?.session.cast?.tv).toBe("tv-1");
    expect(note).toBe("");
  });

  test("a busy TV answers with its title", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    await h.tv((s) => busied(s, "tv-1", "Dune"));
    expect(await offer).toEqual({ answer: "busy", live: null, note: "Dune" });
  });

  test("no answer in five seconds withdraws the offer", async () => {
    const h = harness();
    h.timeOut();
    expect(await h.hub.cast.offer(ME, FILM, "kid")).toEqual({ answer: "none", live: null, note: "" });
    const [id] = h.channel.removed;
    expect(h.channel.reads).toEqual([id!]);
    expect(h.channel.messages.size).toBe(0);
    expect(h.announced.at(-1)).toEqual([id!, null]);
    expect(await h.hub.live(() => null)).toBeNull();
  });

  test("an answer missed by push is read at the deadline", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    const id = await h.tv(accept, false);
    h.timeOut();
    const { answer, live } = await offer;
    expect(answer).toBe("on");
    expect(live?.session.cast?.tv).toBe("tv-1");
    expect(h.channel.messages.has(id)).toBe(true);
    // Kept as if heard: the remote's next move builds on the cast, not on the offer.
    expect(h.announced.at(-1)?.[1]?.cast?.state).toBe("on");
    expect(h.hub.act(id, ME.screen, { playing: true })?.cast).toMatchObject({ state: "on", tv: "tv-1" });
  });

  test("an answer heard before the offer's send returns is not overwritten by the offer", async () => {
    const h = harness();
    const sent = deferred<void>();
    const send = h.channel.send.bind(h.channel);
    h.channel.send = async (text) => { const id = await send(text); await sent.promise; return id; };
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    await h.tv(accept);
    sent.resolve();
    await settle();
    expect(h.announced.map(([, s]) => s?.cast?.state)).toEqual(["on"]);
    expect((await offer).answer).toBe("on");
  });

  test("a failed read at the deadline still withdraws the offer", async () => {
    const h = harness();
    h.channel.failReads = true;
    h.timeOut();
    expect(await h.hub.cast.offer(ME, FILM, "kid")).toEqual({ answer: "none", live: null, note: "" });
    expect(h.channel.removed).toHaveLength(1);
    expect(h.channel.messages.size).toBe(0);
  });

  test("replace after the offer went stale offers anew", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    const id = await h.tv((s) => busied(s, "tv-1", "Dune"));
    await offer;
    h.clock.now += 61_000;
    const again = h.hub.cast.replace(id, ME, { ...FILM, pos: 40 }, "kid");
    await settle();
    expect(h.channel.sent).toHaveLength(2);
    expect(parseSession(h.channel.sent[1]!)?.cast?.state).toBe("offer");
    expect(h.channel.edits).toEqual([]);
    h.timeOut();
    expect((await again).answer).toBe("none");
  });

  test("replace on a live busy request asks the TV to switch", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    const id = await h.tv((s) => busied(s, "tv-1", "Dune"));
    await offer;
    h.clock.now += 2_000;
    const replace = h.hub.cast.replace(id, ME, { ...FILM, pos: 40 }, "kid");
    await settle();
    expect(h.channel.edits).toHaveLength(1);
    expect(h.channel.edits[0]).toMatchObject({ pos: 40, at: NOW + 2_000, by: "tab-1", cast: { state: "replace", note: "" } });
    await h.tv(accept);
    const { answer, live } = await replace;
    expect(answer).toBe("on");
    expect(live?.id).toBe(id);
    expect(h.channel.sent).toHaveLength(1);
  });

  test("cancel deletes the message", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    await settle();
    const [id] = h.channel.messages.keys();
    h.hub.cast.cancel(id!);
    await settle();
    expect(h.channel.removed).toEqual([id!]);
    expect(h.channel.messages.size).toBe(0);
    h.timeOut();
    expect((await offer).answer).toBe("none");
  });

  test("a remote's track choice is queued", async () => {
    const h = harness();
    const offer = h.hub.cast.offer(ME, FILM, "kid");
    const id = await h.tv(accept);
    await offer;
    const tracks = { audio: [{ id: "a1", label: "English" }], text: [{ id: "de", label: "Deutsch" }] };
    await h.tv((s) => tracked(s, "tv-1", tracks, "a1", ""));
    const session = h.hub.cast.tracks(id, "tab-1", { text: "de" });
    expect(session?.cast).toMatchObject({ audio: "a1", text: "de" });
    await settle();
    expect(h.channel.edits.at(-1)?.cast).toMatchObject({ audio: "a1", text: "de" });
  });
});
