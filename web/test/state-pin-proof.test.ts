/**
 * A first PIN — set where its grown-up had none — never replaces a PIN set
 * earlier on another device. Only someone who knew the PIN, or the admin's
 * reset, replaces it: such a PIN goes out proven, and a proven PIN beats any
 * first one. The merge's half is pinned by `profile-roles-merge.json`; this
 * is what writes the flag and what takes a merged PIN in.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import type { MergedProfile } from "../src/state/merged";
import type { Profile } from "../src/state/profiles";
import { WatchState } from "../src/state/store";
import { normalName } from "../src/state/sync-record";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store(): WatchState {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-proof-"));
  dirs.push(dir);
  return new WatchState(join(dir, "state.db"));
}

/** Whether `id`'s PIN goes out proven. */
function proven(state: WatchState, id: string): boolean {
  const name = state.profiles().find((profile) => profile.id === id)!.name;
  return state.exportRecord("here").profiles.find((profile) => profile.name === name)!.pin!.proven === true;
}

describe("a proven PIN", () => {
  test("is one changed by someone who knew it, or reset by the admin, and nothing else", () => {
    const state = store();
    const andre = (state.manage().createFirst("André", "1111") as Profile).id;
    const maja = (state.manage().createGrownUp(andre, "1111", "Maja", "2222") as Profile).id;
    expect(proven(state, andre)).toBe(false);
    expect(proven(state, maja)).toBe(false);
    const sam = state.createProfile("Sam")!.id;
    expect(state.manage().setPin(sam, "", sam, "4444")).toBeNull();
    expect(proven(state, sam)).toBe(false);
    expect(state.manage().setPin(maja, "2222", maja, "3333")).toBeNull();
    expect(proven(state, maja)).toBe(true);
    expect(state.manage().setPin(andre, "1111", sam, "5555")).toBeNull();
    expect(proven(state, sam)).toBe(true);
  });

  test("is not one a claim takes as its first", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    expect(state.manage().claimAdmin(sam, "4444")).toBeNull();
    expect(proven(state, sam)).toBe(false);
  });
});

/** A merged André with nothing but `pin`. */
const andreWith = (pin: MergedProfile["pin"]): MergedProfile =>
  ({ name: normalName("André")!, displayName: "André", progress: [], watched: [], pin });
const hash = (digit: string) => digit.repeat(64);
const SALT = "b".repeat(32);

describe("taking in a merged PIN", () => {
  test("a first PIN only when older, and never over a proven one", () => {
    const state = store();
    state.createProfile("André");
    const take = (pin: MergedProfile["pin"]) => state.importMerged({ profiles: [andreWith(pin)] });
    expect(take({ hash: hash("c"), salt: SALT, updatedAt: 5 })).toBe(1);
    expect(take({ hash: hash("a"), salt: SALT, updatedAt: 6 })).toBe(0);
    expect(take({ hash: hash("a"), salt: SALT, updatedAt: 4 })).toBe(1);
    expect(take({ hash: hash("d"), salt: SALT, updatedAt: 1, proven: true })).toBe(1);
    expect(take({ hash: hash("e"), salt: SALT, updatedAt: 0.5 })).toBe(0);
    expect(state.exportRecord("here").profiles[0]!.pin).toEqual({ hash: hash("d"), salt: SALT, updatedAt: 1, proven: true });
  });

  /**
   * A kid's tablet last synced before André set his PIN, so there André has
   * none, and it is offline. The kid taps his tile, chooses 0000 and is in.
   * Back online, André's own PIN stands on both, and 0000 opens nothing.
   */
  test("a first PIN set on a copy that never heard of his never replaces it", () => {
    const phone = store();
    const there = (phone.manage().createFirst("André", "1111") as Profile).id;
    const tablet = store();
    const here = tablet.createProfile("André")!.id;
    const realNow = Date.now;
    Date.now = () => realNow() + 60_000;
    try {
      expect(tablet.manage().setPin(here, "", here, "0000")).toBeNull();
    } finally {
      Date.now = realNow;
    }
    const merged = mergeStates([phone.exportRecord("phone"), tablet.exportRecord("tablet")]);
    phone.importMerged(merged);
    tablet.importMerged(merged);
    expect(tablet.manage().unlock(here, "0000")).toEqual({ reason: "wrong-pin" });
    expect(tablet.manage().unlock(here, "1111")).toBeNull();
    expect(phone.manage().unlock(there, "1111")).toBeNull();
  });
});
