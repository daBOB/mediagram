/**
 * The one choke point every state mutation passes through on its way to
 * `onWrite` — proved at the router, not over a socket, since nothing here
 * needs a listening port.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { createRouter } from "../src/routes";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";
import type { PlayerRequest } from "../src/http/contracts";

const SET = "01SET0000000000000000001";
let root: string;
let state: WatchState;
let me: string;
let writes: number;

const request = (
  path: string,
  method: string,
  body?: unknown,
  extra: Partial<PlayerRequest> = {},
): PlayerRequest => ({
  method,
  path,
  range: null,
  ...(body === undefined ? {} : { body: JSON.stringify(body), contentType: "application/json" }),
  ...extra,
});

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-write-sync-"));
  state = new WatchState(join(root, "state.db"));
  me = state.createProfile("André")!.id;
  writes = 0;
});
afterEach(async () => {
  state.close();
  await rm(root, { recursive: true, force: true });
});

function db() {
  const index = emptyIndex();
  index.run(
    `INSERT INTO sets(set_id, kind, title, container, duration, total, part_count,
                      status, created_at, spec_version)
     VALUES (?, 'movie', 'A Film', 'mp4', 2400, 10, 1, 'complete', 1700000000, 3)`,
    [SET],
  );
  index.run(
    `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
     VALUES (?, 0, 0, 10, -1001, 100, 'done')`,
    [SET],
  );
  return index;
}

function router() {
  return createRouter({
    db: db(),
    source: { stream: () => new ReadableStream({ start: (c) => c.close() }) },
    state,
    onWrite: () => { writes++; },
  });
}

describe("onWrite", () => {
  test("never fires for the periodic progress tick — a plain PUT", async () => {
    // player.js's ten-second `setProgress` tick lands here; a round every
    // ten seconds for the length of playback is the flood limit this
    // project has already hit once.
    const route = router();
    const response = await route(request(`/api/profiles/${me}/progress/${SET}`, "PUT", { at: 10 }));
    expect(response.status).toBe(204);
    expect(writes).toBe(0);
  });

  test("never fires for a POST that is not marked final — the method alone must not decide", async () => {
    // An older browser could plausibly reach this some other way; the
    // marker, not the verb, is what `writeWorthSyncing` trusts.
    const route = router();
    const response = await route(request(`/api/profiles/${me}/progress/${SET}`, "POST", { at: 10 }));
    expect(response.status).toBe(204);
    expect(writes).toBe(0);
  });

  test("fires for the final flush sent as sendBeacon does — a POST marked final", async () => {
    const route = router();
    const response = await route(
      request(`/api/profiles/${me}/progress/${SET}`, "POST", { at: 10 }, { final: "1" }),
    );
    expect(response.status).toBe(204);
    expect(writes).toBe(1);
  });

  test("fires for the final flush's fallback — a PUT marked final, for a browser that refused sendBeacon", async () => {
    const route = router();
    const response = await route(
      request(`/api/profiles/${me}/progress/${SET}`, "PUT", { at: 10 }, { final: "1" }),
    );
    expect(response.status).toBe(204);
    expect(writes).toBe(1);
  });

  test("fires for a progress DELETE — always deliberate, no marker needed", async () => {
    const route = router();
    await route(request(`/api/profiles/${me}/progress/${SET}`, "PUT", { at: 10 }, { final: "1" }));
    const response = await route(request(`/api/profiles/${me}/progress/${SET}`, "DELETE"));
    expect(response.status).toBe(204);
    expect(writes).toBe(2);
  });

  test("never fires for a preference — per-device, unsynced", async () => {
    const route = router();
    const response = await route(
      request(`/api/profiles/${me}/preferences`, "PUT", { scope: "s", name: "audio", value: "en" }),
    );
    expect(response.status).toBe(204);
    expect(writes).toBe(0);
  });

  test("never fires for a read", async () => {
    const route = router();
    await route(request(`/api/profiles/${me}/state`, "GET"));
    expect(writes).toBe(0);
  });

  test("never fires for a write that was refused", async () => {
    const route = router();
    // No body declared as JSON: `refuseUnsafeBrowserWrite` answers 415.
    const response = await route({ method: "PUT", path: `/api/profiles/${me}/watched/${SET}`, range: null, body: "{}" });
    expect(response.status).toBe(415);
    expect(writes).toBe(0);
  });

  test("never fires for a 404", async () => {
    const route = router();
    const response = await route(request("/api/profiles/nobody/watched/" + SET, "PUT", { at: 1 }));
    expect(response.status).toBe(404);
    expect(writes).toBe(0);
  });

  test("fires for un-marking watched, the write defect 1 exists for", async () => {
    const route = router();
    await route(request(`/api/profiles/${me}/watched/${SET}`, "PUT", {}));
    const response = await route(request(`/api/profiles/${me}/watched/${SET}`, "DELETE"));
    expect(response.status).toBe(204);
    expect(writes).toBe(2);
  });

  test("fires for the watchlist, Kids and a profile write", async () => {
    const route = router();
    expect((await route(request(`/api/profiles/${me}/watchlist/${SET}`, "PUT", {}))).status).toBe(204);
    expect((await route(request(`/api/kids/${SET}`, "PUT", {}))).status).toBe(204);
    expect((await route(request(`/api/profiles/${me}`, "PATCH", { name: "Renamed" }))).status).toBe(204);
    expect(writes).toBe(3);
  });

  test("is optional: a router built without one never throws on a write", async () => {
    const route = createRouter({ db: db(), source: { stream: () => new ReadableStream({ start: (c) => c.close() }) }, state });
    const response = await route(request(`/api/profiles/${me}/watched/${SET}`, "PUT", {}));
    expect(response.status).toBe(204);
  });
});
