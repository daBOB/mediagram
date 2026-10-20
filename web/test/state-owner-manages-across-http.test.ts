/**
 * The owner manages every profile, at the router: in another household they
 * reset a PIN, hide sections, set a kid's limits and remove anyone there,
 * that household's admin too. A household's admin still manages only its own.
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
/** André owns the install and runs Home, with Paul and Lea; Celine runs Madani, with her Nadin. */
let andre: string;
let paul: string;
let lea: string;
let celine: string;
let nadin: string;

const send = (path: string, method: string, body: Record<string, unknown>) => createRouter({
  db: emptyIndex(),
  source: { stream: () => new ReadableStream({ start: (c) => c.close() }) },
  state,
})({ method, path, range: null, body: JSON.stringify(body), contentType: "application/json" });

const read = (response: PlayerResponse) => JSON.parse(new TextDecoder().decode(response.body as Uint8Array));
const profile = (id: string) => state.profiles().find((one) => one.id === id);
const idOf = (made: unknown) => (made as { id: string }).id;

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-owner-across-"));
  state = new WatchState(join(root, "state.db"));
  andre = state.createProfile("André")!.id;
  expect(state.manage().claimAdmin(andre, "1111")).toBeNull();
  const made = await send("/api/households", "POST", { actorId: andre, pin: "1111", name: "Madani", adminName: "Celine", newPin: "5678" });
  expect(made.status).toBe(201);
  celine = read(made).adminId;
  nadin = idOf(state.manage().createKid(celine, "5678", "Nadin", 6));
  paul = idOf(state.manage().createGrownUp(andre, "1111", "Paul", "2468"));
  lea = idOf(state.manage().createKid(andre, "1111", "Lea", 6));
  expect(profile(celine)).toMatchObject({ household: read(made).id, admin: true });
  expect(profile(nadin)?.household).toBe(read(made).id);
});
afterEach(async () => {
  state.close();
  await rm(root, { recursive: true, force: true });
});

/** A write as `actorId` with `pin`, and the status it was answered with. */
const as = (actorId: string, pin: string) => async (path: string, method: string, body: Record<string, unknown> = {}) =>
  (await send(path, method, { actorId, pin, ...body })).status;

describe("the owner, in another household", () => {
  test("resets the PIN of its admin", async () => {
    expect(await as(andre, "1111")(`/api/profiles/${celine}/pin`, "PUT", { newPin: "4321" })).toBe(204);
    expect(state.manage().unlock(celine, "4321")).toBeNull();
  });

  test("hides a section from its admin, and from a kid there", async () => {
    expect(await as(andre, "1111")(`/api/profiles/${celine}/hidden-sections`, "PUT", { sections: ["anime"] })).toBe(204);
    expect(await as(andre, "1111")(`/api/profiles/${nadin}/hidden-sections`, "PUT", { sections: ["movies"] })).toBe(204);
    expect(profile(celine)?.hiddenSections).toEqual(["anime"]);
    expect(profile(nadin)?.hiddenSections).toEqual(["movies"]);
  });

  test("sets a kid's age and daily limits there", async () => {
    expect(await as(andre, "1111")(`/api/profiles/${nadin}/kids-age`, "PUT", { age: 12 })).toBe(204);
    expect(await as(andre, "1111")(`/api/profiles/${nadin}/daily-limit`, "PUT", { minutes: 60 })).toBe(204);
    expect(profile(nadin)).toMatchObject({ kidsAge: 12, dailyLimit: 60 });
  });

  test("removes its admin, whose kid goes with her", async () => {
    expect(await as(andre, "1111")(`/api/profiles/${celine}`, "DELETE")).toBe(204);
    expect(profile(celine)).toBeUndefined();
    expect(profile(nadin)).toBeUndefined();
  });

  test("still proves the owner's PIN first", async () => {
    const wrong = await send(`/api/profiles/${celine}`, "DELETE", { actorId: andre, pin: "0000" });
    expect(wrong.status).toBe(403);
    expect(read(wrong)).toEqual({ reason: "wrong-pin" });
    expect(profile(celine)).toBeDefined();
  });
});

describe("a household's admin, in another household", () => {
  test("resets no PIN, hides no section, sets no limit and removes nobody — the owner included", async () => {
    const celineDoes = as(celine, "5678");
    for (const target of [paul, andre]) {
      expect(await celineDoes(`/api/profiles/${target}/pin`, "PUT", { newPin: "4321" })).toBe(403);
      expect(await celineDoes(`/api/profiles/${target}/hidden-sections`, "PUT", { sections: ["anime"] })).toBe(403);
      expect(await celineDoes(`/api/profiles/${target}`, "DELETE")).toBe(403);
    }
    expect(await celineDoes(`/api/profiles/${lea}/kids-age`, "PUT", { age: 12 })).toBe(403);
    expect(await celineDoes(`/api/profiles/${lea}/daily-limit`, "PUT", { minutes: 30 })).toBe(403);
    expect(await celineDoes(`/api/profiles/${lea}`, "DELETE")).toBe(403);
    expect(state.manage().unlock(paul, "2468")).toBeNull();
    expect(profile(paul)?.hiddenSections).toEqual([]);
    expect(profile(lea)).toMatchObject({ kidsAge: 6, dailyLimit: null });
  });

  test("still manages its own: a section hidden from her kid", async () => {
    expect(await as(celine, "5678")(`/api/profiles/${nadin}/hidden-sections`, "PUT", { sections: ["anime"] })).toBe(204);
  });
});
