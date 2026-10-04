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
import type { ByteSource } from "../src/http/stream";
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
  const read = (response: { body: Uint8Array }) => JSON.parse(new TextDecoder().decode(response.body));
  const listed = async () =>
    read(await rawRequest(server.port, "/api/profiles")).profiles as Array<Record<string, unknown>>;
  const idOf = async (name: string) => (await listed()).find((profile) => profile.name === name)!.id as string;

  test("are listed with their role, and never a PIN", async () => {
    expect((await listed()).find((profile) => profile.id === me)).toEqual({
      id: me, name: "André", createdAt: expect.any(Number),
      kids: false, kidsAge: null, parentId: null, admin: false, hasPin: false,
    });
  });

  test("the first admin is claimed once, and the PIN given becomes theirs", async () => {
    expect((await send(`/api/profiles/${me}/claim-admin`, "POST", { pin: "1111" })).status).toBe(204);
    expect((await listed()).find((profile) => profile.id === me)).toMatchObject({ admin: true, hasPin: true });
    const again = await send(`/api/profiles/${me}/claim-admin`, "POST", { pin: "1111" });
    expect(again.status).toBe(403);
    expect(read(again)).toEqual({ reason: "not-allowed" });
    // Neither the hash nor the salt, under any spelling, nor any long hex.
    const text = new TextDecoder().decode((await rawRequest(server.port, "/api/profiles")).body);
    expect(text).not.toMatch(/pin_?hash|pin_?salt|[0-9a-f]{32}/i);
  });

  test("the admin adds a grown-up with a first PIN, and a grown-up adds its own kid", async () => {
    const maja = await send("/api/profiles", "POST", { actorId: me, pin: "1111", name: "Maja", kids: false, newPin: "2222" });
    expect(maja.status).toBe(201);
    const majaId = read(maja).id as string;
    expect(read(maja)).toMatchObject({ name: "Maja", kids: false, kidsAge: null, admin: false, hasPin: true });
    const mia = await send("/api/profiles", "POST", { actorId: majaId, pin: "2222", name: "Mia", kids: true, kidsAge: 6 });
    expect(mia.status).toBe(201);
    expect(read(mia)).toMatchObject({ name: "Mia", kids: true, kidsAge: 6, parentId: majaId, hasPin: false });
  });

  test("a refusal says why, with its own status", async () => {
    const majaId = await idOf("Maja");
    const cases: [Record<string, unknown>, number, string][] = [
      [{ actorId: me, pin: "1111", name: "Ben", kids: false }, 400, "invalid"],
      [{ actorId: me, pin: "1111", name: "Zoe", kids: true, kidsAge: 9 }, 400, "invalid"],
      [{ actorId: "nobody", pin: "1111", name: "Zoe", kids: true, kidsAge: 12 }, 404, "not-found"],
      [{ actorId: me, pin: "9999", name: "Zoe", kids: true, kidsAge: 12 }, 403, "wrong-pin"],
      [{ actorId: majaId, pin: "2222", name: "Ben", kids: false, newPin: "3333" }, 403, "not-allowed"],
      // Nobody asking is a first profile, and this player has grown-ups.
      [{ name: "Zoe", newPin: "3333" }, 403, "not-allowed"],
      // A name another profile already answers to, whoever asks.
      [{ actorId: me, pin: "1111", name: "maja", kids: true, kidsAge: 6 }, 409, "name-taken"],
    ];
    for (const [body, code, reason] of cases) {
      const answer = await send("/api/profiles", "POST", body);
      expect(answer.status).toBe(code);
      expect(read(answer)).toEqual({ reason });
    }
  });

  test("a grown-up from before PINs is told so, and sets its first with nothing to prove", async () => {
    const sam = state.createProfile("Sam")!.id;
    const locked = await send(`/api/profiles/${sam}/unlock`, "POST", { pin: "1234" });
    expect(locked.status).toBe(409);
    expect(read(locked)).toEqual({ reason: "no-pin" });
    expect((await send(`/api/profiles/${sam}/pin`, "PUT", { actorId: sam, pin: "", newPin: "4444" })).status).toBe(204);
    expect((await send(`/api/profiles/${sam}/unlock`, "POST", { pin: "4444" })).status).toBe(204);
    state.deleteProfile(sam);
  });

  test("a kid's profile opens without a PIN, a grown-up's with its own", async () => {
    expect((await send(`/api/profiles/${await idOf("Mia")}/unlock`, "POST", {})).status).toBe(204);
    expect((await send(`/api/profiles/${me}/unlock`, "POST", { pin: "1112" })).status).toBe(403);
    expect((await send(`/api/profiles/${me}/unlock`, "POST", { pin: "1111" })).status).toBe(204);
  });

  test("a parent changes its kid's limit", async () => {
    const [majaId, miaId] = [await idOf("Maja"), await idOf("Mia")];
    const changed = await send(`/api/profiles/${miaId}/kids-age`, "PUT", { actorId: majaId, pin: "2222", age: 12 });
    expect(changed.status).toBe(204);
    expect((await listed()).find((profile) => profile.id === miaId)).toMatchObject({ kidsAge: 12 });
  });

  test("an action a profile does not have, or the wrong method for one, is a 405", async () => {
    expect((await send(`/api/profiles/${me}/unlock`, "PUT", { pin: "1111" })).status).toBe(405);
    expect((await send("/api/profiles", "PUT", {})).status).toBe(405);
    expect((await rawRequest(server.port, `/api/profiles/${me}/pin`)).status).toBe(405);
  });

  test("the admin removes a grown-up — its kids go too — but never itself", async () => {
    const [majaId, miaId] = [await idOf("Maja"), await idOf("Mia")];
    expect((await send(`/api/profiles/${me}`, "DELETE", { actorId: me, pin: "1111" })).status).toBe(403);
    expect((await send(`/api/profiles/${majaId}`, "DELETE", { actorId: me, pin: "1111" })).status).toBe(204);
    const ids = (await listed()).map((profile) => profile.id);
    expect(ids).not.toContain(majaId);
    expect(ids).not.toContain(miaId);
  });

  test("a removal that does not say who is asking is refused", async () => {
    const removal = await send(`/api/profiles/${me}`, "DELETE");
    expect(removal.status).toBe(404);
    expect(read(removal)).toEqual({ reason: "not-found" });
  });

  test("a removal from another origin is refused, body and all", async () => {
    const response = await send(`/api/profiles/${me}`, "DELETE", { actorId: me, pin: "1111" }, {
      Origin: "https://elsewhere.example",
    });
    expect(response.status).toBe(403);
    expect((await listed()).some((profile) => profile.id === me)).toBe(true);
  });

  test("renaming is not a route", async () => {
    expect((await send(`/api/profiles/${me}`, "PATCH", { name: "Someone" })).status).toBe(405);
    expect((await listed()).find((profile) => profile.id === me)!.name).toBe("André");
  });

  /**
   * The point of the whole change: one profile's shelves are not another's,
   * and a path is what says which. A profile that does not exist is a 404
   * rather than a write that lands somewhere plausible.
   */
  test("one profile's state is not another's", async () => {
    const you = state.createProfile("Maja")!.id;

    await send(mine(`/progress/${SET}`), "PUT", { at: 1234, duration: 2400 });
    await send(mine(`/watchlist/${SET}`), "PUT", {});

    const theirs = await snapshot(you);
    expect(theirs.progress).toEqual([]);
    expect(theirs.watchlist).toEqual([]);

    // And mine is still mine.
    const ours = await snapshot();
    expect(ours.progress[0]).toMatchObject({ setId: SET, at: 1234 });

    state.deleteProfile(you);
  });

  test("state for a profile that is not there is a 404, not an empty shelf", async () => {
    expect((await rawRequest(server.port, "/api/profiles/nobody/state")).status).toBe(404);
    expect((await send("/api/profiles/nobody/progress/" + SET, "PUT", { at: 5 })).status).toBe(404);
  });

  test("a player with no grown-up makes its first profile, which runs the household", async () => {
    const fresh = new WatchState(join(dir, "fresh.db"));
    const other = await startServer({ db: index(), source: new NoSource(), state: fresh });
    const first = (name: string, newPin: string) => rawRequest(other.port, "/api/profiles", {
      method: "POST", headers: JSON_HEAD, body: JSON.stringify({ name, newPin }),
    });
    try {
      const made = await first("André", "1111");
      expect(made.status).toBe(201);
      expect(read(made)).toMatchObject({ name: "André", kids: false, admin: true, hasPin: true });
      const second = await first("Maja", "2222");
      expect(second.status).toBe(403);
      expect(read(second)).toEqual({ reason: "not-allowed" });
    } finally {
      await other.close();
      fresh.close();
    }
  });

  test("five wrong PINs for a profile make its PIN wait, answered 429 with Retry-After", async () => {
    // Its own store: the count is one per store, and this one must not
    // leave the shared server waiting for every test after it.
    const waiting = new WatchState(join(dir, "waiting.db"));
    const other = await startServer({ db: index(), source: new NoSource(), state: waiting });
    try {
      const andre = waiting.createProfile("André")!.id;
      waiting.manage().claimAdmin(andre, "1111");
      const unlock = (pin: string) => rawRequest(other.port, `/api/profiles/${andre}/unlock`, {
        method: "POST", headers: JSON_HEAD, body: JSON.stringify({ pin }),
      });
      for (let wrong = 0; wrong < 5; wrong++) expect((await unlock("0000")).status).toBe(403);
      const right = await unlock("1111");
      expect(right.status).toBe(429);
      const seconds = Number(right.headers.get("retry-after"));
      expect(seconds).toBeGreaterThan(0);
      expect(read(right)).toEqual({ reason: "wait", retryAfter: seconds });
    } finally {
      await other.close();
      waiting.close();
    }
  });
});

describe("the editor's choice", () => {
  const read = async () =>
    JSON.parse(new TextDecoder().decode((await rawRequest(server.port, "/api/editors-choice")).body));

  test("is pinned and unpinned per title, and read back as one pick", async () => {
    expect((await send(`/api/editors-choice/${SET}`, "PUT", {})).status).toBe(204);
    expect((await read()).setId).toBe(SET);
    expect((await send(`/api/editors-choice/${SET}`, "DELETE")).status).toBe(204);
    expect((await read()).setId).toBeNull();
  });

  test("refuses a title the catalog cannot play, and a write from elsewhere", async () => {
    expect((await send("/api/editors-choice/01SETNOTINTHELIBRARY001", "PUT", {})).status).toBe(404);
    const foreign = await send(`/api/editors-choice/${SET}`, "PUT", {}, { origin: "https://elsewhere.example" });
    expect(foreign.status).toBe(403);
  });
});

describe("marking a title as a child's", () => {
  const read = async () =>
    JSON.parse(new TextDecoder().decode((await rawRequest(server.port, "/api/kids")).body));

  test("is not under a profile, because the mark is not one", async () => {
    expect((await send(`/api/kids/${SET}`, "PUT", {})).status).toBe(204);
    expect((await read()).kids).toContain(SET);
    expect((await send(`/api/kids/${SET}`, "DELETE")).status).toBe(204);
    expect((await read()).kids).not.toContain(SET);
  });

  test("a mark is from 12 unless it says 6, and saying so again moves it", async () => {
    expect((await send(`/api/kids/${SET}`, "PUT", { age: 6 })).status).toBe(204);
    let marks = await read();
    expect(marks.kids).toContain(SET);
    expect(marks.fromSix).toContain(SET);

    expect((await send(`/api/kids/${SET}`, "PUT", {})).status).toBe(204);
    marks = await read();
    expect(marks.kids).toContain(SET);
    expect(marks.fromSix).not.toContain(SET);

    expect((await send(`/api/kids/${SET}`, "PUT", { age: 6 })).status).toBe(204);
    expect((await send(`/api/kids/${SET}`, "PUT", { age: 12 })).status).toBe(204);
    expect((await read()).fromSix).not.toContain(SET);

    for (const age of [7, "6", null]) {
      expect((await send(`/api/kids/${SET}`, "PUT", { age })).status).toBe(400);
    }
    expect((await send(`/api/kids/${SET}`, "DELETE")).status).toBe(204);
    expect((await read()).kids).not.toContain(SET);
  });

  test("refuses a title the catalog cannot play", async () => {
    // The same rule as everywhere else here: state may never accumulate rows
    // for titles that are not in the library.
    expect((await send("/api/kids/01SETNOTINTHELIBRARY001", "PUT", {})).status).toBe(404);
  });

  test("refuses a write from another origin", async () => {
    const response = await send(`/api/kids/${SET}`, "PUT", {}, {
      origin: "https://elsewhere.example",
    });
    expect(response.status).toBe(403);
  });

  test("refuses a write that did not declare itself JSON", async () => {
    const response = await rawRequest(server.port, `/api/kids/${SET}`, {
      method: "PUT",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: "{}",
    });
    expect(response.status).toBe(415);
  });

  test("reads with GET and refuses to be written by one", async () => {
    expect((await rawRequest(server.port, "/api/kids")).status).toBe(200);
    expect((await rawRequest(server.port, `/api/kids/${SET}`)).status).toBe(405);
    expect((await send("/api/kids", "PUT", {})).status).toBe(405);
  });
});

describe("recording that a title was watched to the end", () => {
  const watched = async (profile = me) =>
    ((await snapshot(profile)).watched as { setId: string; finishedAt: number }[]).map(
      (row) => row.setId,
    );

  test("is kept and can be taken back", async () => {
    expect((await send(mine(`/watched/${SET}`), "PUT", {})).status).toBe(204);
    expect(await watched()).toContain(SET);
    expect((await send(mine(`/watched/${SET}`), "DELETE")).status).toBe(204);
    expect(await watched()).not.toContain(SET);
  });

  test("says when, which is all a finished show has left to be ranked by", async () => {
    // Finishing an episode clears its position, so without this timestamp a
    // show watched to the end of an episode has no date anywhere the page
    // can see — and the start page orders shows by when they were touched.
    const before = Date.now();
    expect((await send(mine(`/watched/${SET}`), "PUT", {})).status).toBe(204);

    const rows = (await snapshot()).watched as { setId: string; finishedAt: number }[];
    const row = rows.find((entry) => entry.setId === SET);

    expect(row).toBeDefined();
    expect(row!.finishedAt).toBeGreaterThanOrEqual(before);
    // +2: the previous test marked this same set and took the mark back a
    // moment ago. Taking it back is clamped to at least one millisecond past
    // that mark, and this re-mark to at least one past the removal, so when
    // all three land in the same millisecond the stamp runs two ahead of the
    // clock that read it.
    expect(row!.finishedAt).toBeLessThanOrEqual(Date.now() + 2);
  });

  test("finishing clears the position in the same write; taking the mark back does not", async () => {
    // One write, one transaction: a position gone with no completion is what
    // lets another device's older copy of it bring a finished title back.
    const positions = async () =>
      ((await snapshot()).progress as { setId: string }[]).map((row) => row.setId);
    await send(mine(`/progress/${SET}`), "PUT", { at: 600, duration: 2400 });
    expect((await send(mine(`/watched/${SET}`), "PUT", {})).status).toBe(204);
    expect(await positions()).not.toContain(SET);

    await send(mine(`/progress/${SET}`), "PUT", { at: 30, duration: 2400 });
    expect((await send(mine(`/watched/${SET}`), "DELETE")).status).toBe(204);
    expect(await positions()).toContain(SET);
  });

  test("refuses a title the catalog cannot play", async () => {
    expect((await send(mine("/watched/01SETNOTINTHELIBRARY1"), "PUT", {})).status).toBe(404);
  });

  test("refuses a profile that is not there", async () => {
    const path = `/api/profiles/00000000-0000-0000-0000-000000000000/watched/${SET}`;
    expect((await send(path, "PUT", {})).status).toBe(404);
  });

  test("refuses a write from another origin", async () => {
    const response = await send(mine(`/watched/${SET}`), "PUT", {}, {
      origin: "https://elsewhere.example",
    });
    expect(response.status).toBe(403);
  });

  test("is not readable or writable by GET", async () => {
    expect((await rawRequest(server.port, mine(`/watched/${SET}`))).status).toBe(405);
  });
});

describe("a position sent as the tab closes", () => {
  test("is accepted, because that is the shape `sendBeacon` sends", async () => {
    // `navigator.sendBeacon` can only POST. Refusing POST meant the one write
    // that exists to survive the page going away was the one write that never
    // landed — and `sendBeacon` reports success on queueing, so the PUT
    // fallback beneath it never ran either.
    expect((await send(mine(`/progress/${SET}`), "POST", { at: 610, duration: 1800 })).status).toBe(
      204,
    );
    const held = (await snapshot()).progress.find((p: { setId: string }) => p.setId === SET);
    expect(held?.at).toBe(610);
  });

  test("is still refused from another origin", async () => {
    // POST is the one method a cross-site form can send, so the guard in front
    // of it has to keep holding.
    const response = await send(mine(`/progress/${SET}`), "POST", { at: 1 }, {
      origin: "https://elsewhere.example",
    });
    expect(response.status).toBe(403);
  });

  test("is still refused when it does not declare itself JSON", async () => {
    // What actually keeps a cross-site form out: a form can only send
    // urlencoded, multipart or text/plain, never application/json.
    const response = await rawRequest(server.port, mine(`/progress/${SET}`), {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: "at=1",
    });
    expect(response.status).toBe(415);
  });

  test("still refuses a method the route does not have", async () => {
    expect((await send(mine(`/progress/${SET}`), "PATCH", { at: 1 })).status).toBe(405);
  });
});
