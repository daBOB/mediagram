/**
 * The household routes at the router: the owner's actions and their codes,
 * joining, which starts no sync round, and the write guard every one of them
 * passes first. Proved at the router, not over a socket, as `onWrite` is.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { PlayerRequest, PlayerResponse } from "../src/http/contracts";
import { createRouter } from "../src/routes";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";

let root: string;
let state: WatchState;
let owner: string;
let writes: number;

const request = (path: string, method: string, body?: unknown, extra: Partial<PlayerRequest> = {}): PlayerRequest => ({
  method,
  path,
  range: null,
  ...(body === undefined ? {} : { body: JSON.stringify(body), contentType: "application/json" }),
  ...extra,
});

const read = (response: PlayerResponse) => JSON.parse(new TextDecoder().decode(response.body as Uint8Array));

function router() {
  return createRouter({
    db: emptyIndex(),
    source: { stream: () => new ReadableStream({ start: (c) => c.close() }) },
    state,
    onWrite: () => { writes++; },
  });
}

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-households-http-"));
  state = new WatchState(join(root, "state.db"));
  owner = state.createProfile("André")!.id;
  // The first claim makes the owner, with this first PIN.
  expect(state.manage().claimAdmin(owner, "1111")).toBeNull();
  writes = 0;
});
afterEach(async () => {
  state.close();
  await rm(root, { recursive: true, force: true });
});

const create = (name: string, adminName: string, extra: Record<string, unknown> = {}) =>
  router()(request("/api/households", "POST", { actorId: owner, pin: "1111", name, adminName, newPin: "5678", ...extra }));

describe("the household routes", () => {
  test("POST /api/households answers 201 with the household", async () => {
    const made = await create("Flat", "Maja");
    expect(made.status).toBe(201);
    const maja = state.profiles().find((one) => one.name === "Maja")!;
    expect(read(made)).toEqual({ id: expect.any(String), name: "Flat", adminId: maja.id });
    expect(writes).toBe(1);
  });

  test("each owner route answers 204, and a refusal its reason and code", async () => {
    const route = router();
    const flat = read(await create("Flat", "Maja")).id as string;
    const paul = state.manage().createGrownUp(owner, "1111", "Paul", "2468") as { id: string };
    const as = (body: Record<string, unknown>) => ({ actorId: owner, pin: "1111", ...body });

    expect((await route(request(`/api/households/${flat}/name`, "PUT", as({ name: "Loft" })))).status).toBe(204);
    expect((await route(request(`/api/households/${flat}/move`, "POST", as({ profileId: paul.id })))).status).toBe(204);
    expect((await route(request(`/api/households/${flat}/admin`, "PUT", as({ adminId: paul.id })))).status).toBe(204);
    expect(state.households().find((one) => one.id === flat)).toEqual({ id: flat, name: "Loft", adminId: paul.id });

    const refused = async (path: string, method: string, body: Record<string, unknown>, status: number, reason: string) => {
      const response = await route(request(path, method, body));
      expect(response.status).toBe(status);
      expect(read(response)).toEqual({ reason });
    };
    await refused(`/api/households/${flat}/name`, "PUT", as({ name: " " }), 400, "invalid");
    await refused(`/api/households/home/name`, "PUT", as({ name: "loft" }), 409, "name-taken");
    await refused(`/api/households/nowhere/name`, "PUT", as({ name: "Oma" }), 404, "not-found");
    await refused(`/api/households/${flat}`, "DELETE", as({}), 403, "not-allowed");
    await refused(`/api/households/${flat}/name`, "PUT", as({ pin: "0000", name: "Oma" }), 403, "wrong-pin");
    await refused("/api/households", "POST", as({ name: "Oma", adminName: "Oma" }), 409, "no-pin");
    for (let wrong = 0; wrong < 4; wrong++) await route(request(`/api/households/${flat}/join`, "POST", { pin: "0000" }));
    await refused(`/api/households/${flat}/join`, "POST", { pin: "0000" }, 403, "wrong-pin");
    const held = await route(request(`/api/households/${flat}/join`, "POST", { pin: "1111" }));
    expect(held.status).toBe(429);
    expect(held.headers["retry-after"]).toBe("60");

    // Empty it, then delete it.
    for (const id of [state.profiles().find((one) => one.name === "Maja")!.id, paul.id]) {
      await route(request(`/api/households/home/move`, "POST", as({ profileId: id })));
    }
    // A grown-up moved into the emptied household runs it, with the first PIN the move gave.
    const lena = state.createProfile("Lena")!.id;
    expect((await route(request(`/api/households/${flat}/move`, "POST", as({ profileId: lena, newPin: "4321" })))).status)
      .toBe(204);
    expect(state.manage().unlock(lena, "4321")).toBeNull();
    await route(request(`/api/households/home/move`, "POST", as({ profileId: lena })));
    expect((await route(request(`/api/households/${flat}`, "DELETE", as({})))).status).toBe(204);
    expect(state.households().map((one) => one.id)).toEqual(["home"]);

    expect((await route(request("/api/households", "GET"))).status).toBe(405);
    expect((await route(request(`/api/households/${flat}`, "PATCH", as({ name: "x" })))).status).toBe(405);
    expect((await route(request(`/api/households/${flat}/name`, "POST", as({ name: "x" })))).status).toBe(405);
    expect((await route(request(`/api/households/${flat}/join`, "GET"))).status).toBe(405);
  });

  test("join answers 204 and starts no sync round", async () => {
    const flat = read(await create("Flat", "Maja")).id as string;
    writes = 0;
    const route = router();
    expect((await route(request(`/api/households/${flat}/join`, "POST", { pin: "5678" }))).status).toBe(204);
    expect((await route(request(`/api/households/${flat}/join`, "POST", { pin: "1111" }))).status).toBe(204);
    expect(writes).toBe(0);
    expect((await route(request(`/api/households/${flat}/name`, "PUT", { actorId: owner, pin: "1111", name: "Loft" }))).status)
      .toBe(204);
    expect(writes).toBe(1);
  });

  test("a household write from another origin is refused", async () => {
    const foreign = await router()(request("/api/households", "POST",
      { actorId: owner, pin: "1111", name: "Flat", adminName: "Maja", newPin: "5678" },
      { origin: "https://elsewhere.example", host: "localhost:8770" }));
    expect(foreign.status).toBe(403);
    expect(state.households().map((one) => one.name)).toEqual(["Home"]);
    expect(writes).toBe(0);
  });

  test("GET /api/profiles lists the live households by name", async () => {
    await create("oma", "Oma");
    const flat = read(await create("Flat", "Maja")).id as string;
    await create("Zoo", "Zed");
    const maja = state.profiles().find((one) => one.name === "Maja")!.id;
    await router()(request(`/api/households/home/move`, "POST", { actorId: owner, pin: "1111", profileId: maja }));
    await router()(request(`/api/households/${flat}`, "DELETE", { actorId: owner, pin: "1111" }));
    const said = read(await router()(request("/api/profiles", "GET")));
    expect(said.households.map((one: { name: string }) => one.name)).toEqual(["Home", "oma", "Zoo"]);
    expect(said.households[0]).toEqual({ id: "home", name: "Home", adminId: owner });
  });
});
