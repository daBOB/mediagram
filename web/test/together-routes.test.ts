/** `/api/together`, at the router, over a hub whose channel is a fake. */

import { describe, expect, test } from "bun:test";

import type { EditOutcome } from "../src/together/channel";
import { deferred, harness, settle } from "./together-fixture";

const start = { op: "start", screen: "tab-a", set: "film-1", playing: true, pos: 12.5, rate: 1 };

describe("/api/together", () => {
  test("GET says why while Telegram is unreachable", async () => {
    const h = harness();
    h.channel.up = false;
    const { status, json } = await h.call("GET");
    expect(status).toBe(200);
    expect(json).toMatchObject({ available: false, reason: "Telegram isn't reachable", live: null });
  });

  test("GET says why when the channel cannot be read", async () => {
    const h = harness();
    h.channel.failReads = true;
    expect((await h.call("GET")).json).toMatchObject({ available: false, reason: "Telegram isn't reachable", live: null });
  });

  test("start writes a Browser member, and a second start meets the first", async () => {
    const h = harness();
    const first = await h.call("POST", start);
    expect(first.status).toBe(200);
    expect(first.json.session.members).toEqual([{ screen: "tab-a", label: "Browser", remote: false }]);
    expect(first.json.session).toMatchObject({ set: "film-1", playing: true, pos: 12.5, rate: 1 });
    const second = await h.call("POST", { ...start, screen: "tab-b" });
    expect(second.status).toBe(409);
    expect(second.json.id).toBe(first.json.id);
    expect((await h.call("GET")).json).toMatchObject({ available: true, reason: null, live: { id: first.json.id } });
  });

  test.each([
    ["an unknown set", { ...start, set: "nope" }],
    ["a stopped rate", { ...start, rate: 0 }],
    ["an unknown op", { ...start, op: "nope" }],
    ["a screen that is not an id", { ...start, screen: "a b" }],
  ])("refuses %s", async (_, body) => {
    expect((await harness().call("POST", body)).status).toBe(400);
  });

  test("a session that is not there cannot be acted on or joined", async () => {
    const h = harness();
    expect((await h.call("POST", { op: "act", id: 999, screen: "tab-a", change: { playing: false } })).status).toBe(404);
    expect((await h.call("POST", { op: "join", id: 999, screen: "tab-a" })).status).toBe(404);
  });

  test.each(["leave", "end"])("%s is answered once queued, before its edit lands", async (op) => {
    const h = harness();
    const { json } = await h.call("POST", start);
    const held = deferred<EditOutcome>();
    h.channel.outcomes.push(held.promise);
    expect((await h.call("POST", { op, id: json.id, screen: "tab-a" })).status).toBe(204);
    expect(h.channel.removed).toEqual([]);
    held.resolve("ok");
    await settle();
    expect(h.channel.removed).toEqual([json.id]);
  });

  test("a start or a join while Telegram is unreachable says why", async () => {
    const h = harness();
    h.channel.up = false;
    const unreachable = { status: 503, json: { reason: "Telegram isn't reachable" } };
    expect(await h.call("POST", start)).toEqual(unreachable);
    expect(await h.call("POST", { op: "join", id: 950, screen: "tab-a" })).toEqual(unreachable);
  });

  test("an act, a leave or an end while Telegram is unreachable is queued, and an act says why", async () => {
    const h = harness();
    const { json } = await h.call("POST", start);
    h.channel.up = false;
    const act = await h.call("POST", { op: "act", id: json.id, screen: "tab-a", change: { playing: false } });
    expect(act.status).toBe(200);
    expect(act.json).toMatchObject({ session: { playing: false }, reason: "Telegram isn't reachable" });
    expect((await h.call("POST", { op: "end", id: json.id, screen: "tab-a" })).status).toBe(204);
    await settle();
    expect(h.channel.edits.at(-1)).toMatchObject({ playing: false, ended: true });
    // A session it can't tell is over or not is no reason to end one: the tab plays on alone.
    expect(await h.call("POST", { op: "act", id: 999, screen: "tab-a", change: { playing: true } }))
      .toEqual({ status: 503, json: { reason: "Telegram isn't reachable" } });
  });

  test("is not there without a hub", async () => {
    expect((await harness(false).call("GET")).status).toBe(404);
  });
});
