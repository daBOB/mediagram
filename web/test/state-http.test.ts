/**
 * The write half of the API, over a real socket.
 *
 * This is the one surface of this project that changes something, and the one
 * with no authentication in front of it, so the refusals matter as much as
 * the successes: a page on another origin must not be able to delete a
 * collection, and a form post must not be able to reach any of it.
 */

import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { startServer, type RunningServer } from "../src/server";
import type { ByteSource } from "../src/routes";
import type { PartLocation } from "../src/catalog";
import type { Step } from "../src/range";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";
import { rawRequest } from "./raw-http";

const SET = "01SET0000000000000000009";
const OTHER = "01SET0000000000000000010";

class NoSource implements ByteSource {
  stream(_l: PartLocation[], _s: Step[], _id: string): ReadableStream<Uint8Array> {
    return new ReadableStream({ start: (c) => c.close() });
  }
}

function index(): Database {
  const db = emptyIndex();
  for (const id of [SET, OTHER]) {
    db.run(
      `INSERT INTO sets(set_id, kind, title, container, duration, total, part_count,
                        status, created_at, spec_version)
       VALUES (?, 'movie', 'A Film', 'mp4', 2400, 10, 1, 'complete', 1700000000, 3)`,
      [id],
    );
    db.run(
      `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
       VALUES (?, 0, 0, 10, -1001, 100, 'done')`,
      [id],
    );
  }
  return db;
}

let server: RunningServer;
let state: WatchState;
let dir: string;
let me: string;

const JSON_HEAD = { "content-type": "application/json" };
const send = (path: string, method: string, body?: unknown, headers: Record<string, string> = {}) =>
  rawRequest(server.port, path, {
    method,
    headers: { ...(body === undefined ? {} : JSON_HEAD), ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

const snapshot = async (profile = me) =>
  JSON.parse(
    new TextDecoder().decode((await rawRequest(server.port, `/api/profiles/${profile}/state`)).body),
  );

/** Every state path is under the profile it belongs to. */
const mine = (rest: string, profile = me) => `/api/profiles/${profile}${rest}`;

beforeAll(async () => {
  dir = mkdtempSync(join(tmpdir(), "mediagram-state-http-"));
  state = new WatchState(join(dir, "state.db"));
  server = await startServer({ db: index(), source: new NoSource(), state });
  me = state.createProfile("André")!.id;
});

afterAll(async () => {
  await server.close();
  state.close();
  rmSync(dir, { recursive: true, force: true });
});

describe("reading it back", () => {
  test("an empty player still answers the shape the page expects", async () => {
    const body = await snapshot();
    expect(body).toMatchObject({ remembers: true });
    expect(Array.isArray(body.progress)).toBe(true);
    expect(Array.isArray(body.watchlist)).toBe(true);
    expect(Array.isArray(body.collections)).toBe(true);
  });
});

describe("positions", () => {
  test("a position round-trips, which is what resuming on another device is", async () => {
    expect((await send(mine(`/progress/${SET}`), "PUT", { at: 2043, duration: 2400 })).status).toBe(204);

    // A second device is just a second request to the same player.
    const { progress } = await snapshot();
    expect(progress[0]).toMatchObject({ setId: SET, at: 2043, duration: 2400 });
  });

  test("and can be forgotten", async () => {
    await send(mine(`/progress/${OTHER}`), "PUT", { at: 10, duration: 100 });
    expect((await send(mine(`/progress/${OTHER}`), "DELETE")).status).toBe(204);

    const { progress } = await snapshot();
    expect(progress.map((p: { setId: string }) => p.setId)).not.toContain(OTHER);
  });

  test("a position that is not a number is refused rather than stored", async () => {
    expect((await send(mine(`/progress/${SET}`), "PUT", { at: "soon" })).status).toBe(400);
    expect((await send(mine(`/progress/${SET}`), "PUT", {})).status).toBe(400);
  });

  test("a set the catalog will not play cannot have a position", async () => {
    expect((await send(mine("/progress/01NOSUCH"), "PUT", { at: 5 })).status).toBe(404);
  });
});

describe("the watchlist and collections", () => {
  test("a title goes on the list and comes off it", async () => {
    expect((await send(mine(`/watchlist/${SET}`), "PUT", {})).status).toBe(204);
    expect((await snapshot()).watchlist).toContain(SET);

    expect((await send(mine(`/watchlist/${SET}`), "DELETE")).status).toBe(204);
    expect((await snapshot()).watchlist).not.toContain(SET);
  });

  test("a list is created, filled, renamed and deleted", async () => {
    const made = await send(mine("/collections"), "POST", { name: "Sonntagabend" });
    expect(made.status).toBe(201);
    const { id } = JSON.parse(new TextDecoder().decode(made.body));

    expect((await send(mine(`/collections/${id}/items/${SET}`), "PUT", {})).status).toBe(204);
    expect((await send(mine(`/collections/${id}/items/${OTHER}`), "PUT", {})).status).toBe(204);
    expect((await send(mine(`/collections/${id}`), "PATCH", { name: "Montagabend" })).status).toBe(204);

    const [list] = (await snapshot()).collections;
    expect(list).toMatchObject({ name: "Montagabend", items: [SET, OTHER] });

    expect((await send(mine(`/collections/${id}/items/${SET}`), "DELETE")).status).toBe(204);
    expect((await snapshot()).collections[0].items).toEqual([OTHER]);

    expect((await send(mine(`/collections/${id}`), "DELETE")).status).toBe(204);
    expect((await snapshot()).collections).toEqual([]);
  });

  test("a name of nothing is refused, and a list that is gone is a 404", async () => {
    expect((await send(mine("/collections"), "POST", { name: "   " })).status).toBe(400);
    expect((await send(mine("/collections/nope"), "PATCH", { name: "x" })).status).toBe(404);
    expect((await send(mine("/collections/nope"), "DELETE")).status).toBe(404);
  });
});

/**
 * Neither check is authentication, and neither pretends to be. What they stop
 * is a page on another origin quietly deleting things through a browser that
 * was happy to send the request.
 */
describe("refusing a write nobody on this page made", () => {
  test("a cross-origin write is refused", async () => {
    const response = await send(mine(`/progress/${SET}`), "PUT", { at: 1 }, {
      Origin: "https://elsewhere.example",
    });
    expect(response.status).toBe(403);
  });

  test("the page's own origin is fine", async () => {
    const response = await send(mine(`/progress/${SET}`), "PUT", { at: 1 }, {
      Origin: `http://127.0.0.1:${server.port}`,
    });
    expect(response.status).toBe(204);
  });

  test("a form post cannot reach it, whatever it puts in the body", async () => {
    for (const type of [
      "application/x-www-form-urlencoded",
      "multipart/form-data",
      "text/plain",
    ]) {
      const response = await rawRequest(server.port, mine(`/progress/${SET}`), {
        method: "PUT",
        headers: { "content-type": type },
        body: '{"at":1}',
      });
      expect(response.status).toBe(415);
    }
  });

  test("reading state is still a plain GET, and writing by GET is not a thing", async () => {
    expect((await rawRequest(server.port, mine("/state"))).status).toBe(200);
    expect((await rawRequest(server.port, mine(`/progress/${SET}`))).status).toBe(405);
  });
});

describe("a player that remembers nothing", () => {
  test("says so, has nobody, and refuses a write for a profile that is not there", async () => {
    const forgetful = await startServer({
      db: index(),
      source: new NoSource(),
      state: new WatchState(null),
    });
    try {
      const listed = JSON.parse(
        new TextDecoder().decode((await rawRequest(forgetful.port, "/api/profiles")).body),
      );
      expect(listed).toEqual({ remembers: false, profiles: [] });

      // A profile cannot be made, so nothing can be written under one — which
      // is a 404 about the profile rather than a pretence that it worked.
      const made = await rawRequest(forgetful.port, "/api/profiles", {
        method: "POST",
        headers: JSON_HEAD,
        body: JSON.stringify({ name: "André" }),
      });
      expect(made.status).toBe(400);

      const put = await rawRequest(forgetful.port, `/api/profiles/nobody/progress/${SET}`, {
        method: "PUT",
        headers: JSON_HEAD,
        body: JSON.stringify({ at: 10 }),
      });
      expect(put.status).toBe(404);
    } finally {
      await forgetful.close();
    }
  });
});

describe("profiles", () => {
  test("are made, listed, renamed and deleted", async () => {
    const made = await send("/api/profiles", "POST", { name: "Maja" });
    expect(made.status).toBe(201);
    const { id } = JSON.parse(new TextDecoder().decode(made.body));

    const listed = JSON.parse(
      new TextDecoder().decode((await rawRequest(server.port, "/api/profiles")).body),
    );
    expect(listed.profiles.map((p: { name: string }) => p.name)).toContain("Maja");

    expect((await send(`/api/profiles/${id}`, "PATCH", { name: "Maja B" })).status).toBe(204);
    expect((await send(`/api/profiles/${id}`, "DELETE")).status).toBe(204);
    expect((await send(`/api/profiles/${id}`, "DELETE")).status).toBe(404);
  });

  /**
   * The point of the whole change: one profile's shelves are not another's,
   * and a path is what says which. A profile that does not exist is a 404
   * rather than a write that lands somewhere plausible.
   */
  test("one profile's state is not another's", async () => {
    const made = await send("/api/profiles", "POST", { name: "Maja" });
    const { id: you } = JSON.parse(new TextDecoder().decode(made.body));

    await send(mine(`/progress/${SET}`), "PUT", { at: 1234, duration: 2400 });
    await send(mine(`/watchlist/${SET}`), "PUT", {});

    const theirs = await snapshot(you);
    expect(theirs.progress).toEqual([]);
    expect(theirs.watchlist).toEqual([]);

    // And mine is still mine.
    const ours = await snapshot();
    expect(ours.progress[0]).toMatchObject({ setId: SET, at: 1234 });

    await send(`/api/profiles/${you}`, "DELETE");
  });

  test("state for a profile that is not there is a 404, not an empty shelf", async () => {
    expect((await rawRequest(server.port, "/api/profiles/nobody/state")).status).toBe(404);
    expect((await send("/api/profiles/nobody/progress/" + SET, "PUT", { at: 5 })).status).toBe(404);
  });
});
