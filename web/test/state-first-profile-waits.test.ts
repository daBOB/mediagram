/**
 * A first profile waits until this player has heard its household: a sync
 * round of the channel it follows now, taken in. Before that, a first profile
 * made blind under a member's name would merge with that member by name and
 * hand its newer PIN to them on every device. A player that syncs with nobody
 * has nothing to wait for.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { PlayerRequest } from "../src/http/contracts";
import { profileRoute } from "../src/state/profiles-routes";
import { WatchState } from "../src/state/store";
import { StateSync, type StateChannel } from "../src/state/sync";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store(): WatchState {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-heard-"));
  dirs.push(dir);
  return new WatchState(join(dir, "state.db"));
}

/** An empty channel that says which library it reads — `null` while it can reach none. */
function channel(library: () => string | null, list: () => Promise<[]> = async () => []): StateChannel {
  return { list, put: async () => 1, library };
}

describe("creating the first profile", () => {
  test("a player that syncs with nobody has nothing to wait for", () => {
    expect(store().manage().createFirst("André", "1111")).toMatchObject({ admin: true });
  });

  test("before a round of the channel it follows, a player that syncs makes nobody", () => {
    const state = store();
    new StateSync(state, channel(() => "-1001"), "laptop");
    expect(state.manage().createFirst("André", "1111")).toEqual({ reason: "not-synced" });
    expect(state.profiles()).toEqual([]);
    // What cannot be used is still said first.
    expect(state.manage().createFirst("André", "11")).toEqual({ reason: "invalid" });
  });

  test("a round that found nobody lets a new household begin", async () => {
    const state = store();
    await new StateSync(state, channel(() => "-1001"), "laptop").once();
    expect(state.manage().createFirst("André", "1111")).toMatchObject({ admin: true });
  });
});

describe("what counts as a round", () => {
  test("not one whose listing failed", async () => {
    const state = store();
    const failing = channel(() => "-1001", async () => { throw new Error("no network"); });
    await new StateSync(state, failing, "laptop").once();
    expect(state.household.heard()).toBe(false);
  });

  test("not one with no channel to reach: signed out, a list reads as empty", async () => {
    const state = store();
    await new StateSync(state, channel(() => null), "laptop").once();
    expect(state.household.heard()).toBe(false);
  });

  test("not one whose channel was switched while it ran", async () => {
    const state = store();
    let library = "-1001";
    const switching = channel(() => library, async () => { library = "-1002"; return []; });
    await new StateSync(state, switching, "laptop").once();
    expect(state.household.heard()).toBe(false);
  });

  test("a round of the last library is no round of the one followed now", async () => {
    const state = store();
    let library = "-1001";
    const sync = new StateSync(state, channel(() => library), "laptop");
    await sync.once();
    expect(state.household.heard()).toBe(true);
    library = "-1002";
    expect(state.household.heard()).toBe(false);
    expect(state.manage().createFirst("André", "1111")).toEqual({ reason: "not-synced" });
    await sync.once();
    expect(state.household.heard()).toBe(true);
  });
});

describe("over HTTP", () => {
  const request = (method: string, body?: unknown): PlayerRequest => ({
    method,
    path: "/api/profiles",
    range: null,
    contentType: "application/json",
    body: body === undefined ? null : JSON.stringify(body),
  });
  const read = (body: unknown) => JSON.parse(new TextDecoder().decode(body as Uint8Array));

  test("the list says whether the household has been heard, and a first profile waits with 409", () => {
    const state = store();
    new StateSync(state, channel(() => "-1001"), "laptop");
    expect(read(profileRoute(request("GET"), state)!.body)).toMatchObject({ heard: false, profiles: [] });
    const refused = profileRoute(request("POST", { name: "André", newPin: "1111" }), state)!;
    expect(refused.status).toBe(409);
    expect(read(refused.body)).toEqual({ reason: "not-synced" });
  });

  test("a player that syncs with nobody says it has heard all there is", () => {
    expect(read(profileRoute(request("GET"), store())!.body)).toMatchObject({ heard: true });
  });
});
