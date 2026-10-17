/** `/api/report`, at the router, over a sender that records instead of sending. */

import { describe, expect, test } from "bun:test";

import manifest from "../package.json";
import { reportText } from "../public/lib/report/report-text.js";
import { createRouter } from "../src/routes";
import type { ReportSender } from "../src/telegram/report-sender";
import type { PlayerRequest } from "../src/http/contracts";
import { emptyIndex } from "./index-fixture";

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

class FakeSender implements ReportSender {
  available_ = true;
  fail = false;
  sent: string[] = [];
  available() { return this.available_; }
  async send(text: string) {
    if (this.fail) throw new Error("x");
    this.sent.push(text);
  }
}

const source = { stream: () => new ReadableStream<Uint8Array>({ start: (c) => c.close() }) };
const valid = { set: "film-1", reason: "wont-play", title: "A Film", note: "stutters", position: 12.5, played: "direct", held: false, error: null };

function harness(withSender = true) {
  const sender = new FakeSender();
  const route = createRouter({ db: db(), source, report: withSender ? sender : undefined });
  const call = async (method: string, body?: unknown) => {
    const request: PlayerRequest = {
      method, path: "/api/report", range: null,
      ...(body === undefined ? {} : { body: JSON.stringify(body), contentType: "application/json" }),
    };
    return (await route(request)).status;
  };
  return { sender, call };
}

describe("/api/report", () => {
  test("a valid report is sent as the browser's", async () => {
    const h = harness();
    expect(await h.call("POST", valid)).toBe(204);
    expect(h.sender.sent).toEqual([
      reportText({
        reason: "wont-play", title: "A Film", note: "stutters", profile: null, screen: "Browser", device: null,
        app: `Web ${manifest.version}`, position: 12.5, played: "direct", held: false, error: null, setId: "film-1",
      }),
    ]);
  });

  test.each([
    ["an unknown set", { set: "nope" }],
    ["an unknown reason", { reason: "other" }],
    ["an empty title", { title: "" }],
    ["a note over 2000 characters", { note: "x".repeat(2001) }],
    ["a position that is not a number", { position: "NaN" }],
    ["a negative position", { position: -1 }],
    ["an unknown way of playing", { played: "magic" }],
    ["a held that is not a boolean", { held: "yes" }],
    ["an error over 300 characters", { error: "x".repeat(301) }],
    ["a profile id that is not a string", { profileId: 4 }],
  ])("%s is refused and nothing is sent", async (_name, patch) => {
    const h = harness();
    expect(await h.call("POST", { ...valid, ...patch })).toBe(400);
    expect(h.sender.sent).toEqual([]);
  });

  test("an unreachable Telegram is 503", async () => {
    const h = harness();
    h.sender.available_ = false;
    expect(await h.call("POST", valid)).toBe(503);
    expect(h.sender.sent).toEqual([]);
  });

  test("a failed send is 503", async () => {
    const h = harness();
    h.sender.fail = true;
    expect(await h.call("POST", valid)).toBe(503);
  });

  test("GET is 405, and without a sender the route is 404", async () => {
    expect(await harness().call("GET")).toBe(405);
    expect(await harness(false).call("POST", valid)).toBe(404);
  });
});
