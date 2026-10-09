/** `/api/together`, at the router, over a hub whose channel is a fake. */

import { describe, expect, test } from "bun:test";

import { createRouter } from "../src/routes";
import { TogetherHub } from "../src/together/hub";
import type { PlayerRequest } from "../src/http/contracts";
import { emptyIndex } from "./index-fixture";
import { FakeChannel } from "./together-fixture";

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

function harness(withHub = true) {
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
  return { channel, call };
}

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
    expect(first.json.session.members).toEqual([{ screen: "tab-a", label: "Browser" }]);
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

  test("any write while Telegram is unreachable says why", async () => {
    const h = harness();
    h.channel.up = false;
    expect(await h.call("POST", start)).toEqual({ status: 503, json: { reason: "Telegram isn't reachable" } });
  });

  test("is not there without a hub", async () => {
    expect((await harness(false).call("GET")).status).toBe(404);
  });
});
