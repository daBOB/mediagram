/**
 * A profile's role in the store and on the sync record: what the page is told
 * about it, what a device writes, and what it takes in from the merge.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates, type MergedProfile } from "../src/state/merge";
import { WatchState } from "../src/state/store";
import { normalName, parseRecord, type SyncRecord } from "../src/state/sync-record";

const HASH = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const SALT = "00112233445566778899aabbccddeeff";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** A store, and the path a second connection reaches its file by. */
function store() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-roles-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  return { state: new WatchState(path), path };
}

/** Sets columns no method here writes yet, through a second connection. */
function sql(path: string, write: (db: Database) => void): void {
  const db = new Database(path);
  try {
    write(db);
  } finally {
    db.close();
  }
}

/** André as admin with a PIN, and Mia as his kid at FSK 6 — the household most tests start from. */
function household(path: string, me: string, mia: string): void {
  sql(path, (db) => {
    db.query("UPDATE profiles SET admin_claimed_at = 500, pin_hash = ?2, pin_salt = ?3, pin_updated_at = 600 WHERE id = ?1")
      .run(me, HASH, SALT);
    db.query("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 700, parent_id = ?2 WHERE id = ?1").run(mia, me);
  });
}

describe("a profile as the page sees it", () => {
  test("a grown-up and a kid, as made", () => {
    const { state } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    expect(me).toEqual({
      id: me.id, name: "André", createdAt: me.createdAt,
      kids: false, kidsAge: null, parentId: null, admin: false, hasPin: false,
    });
    expect(mia).toEqual({
      id: mia.id, name: "Mia", createdAt: mia.createdAt,
      kids: true, kidsAge: 12, parentId: null, admin: false, hasPin: false,
    });
    expect(state.profiles()).toEqual([me, mia]);
    state.close();
  });

  test("says who is admin, who has a PIN, a kid's limit and parent — never the PIN itself", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    household(path, me.id, mia.id);
    const byId = new Map(state.profiles().map((profile) => [profile.id, profile]));
    expect(byId.get(me.id)).toMatchObject({ admin: true, hasPin: true, kidsAge: null });
    expect(byId.get(mia.id)).toMatchObject({ kids: true, kidsAge: 6, parentId: me.id, admin: false, hasPin: false });
    const text = JSON.stringify(state.profiles());
    expect(text).not.toContain(HASH);
    expect(text).not.toContain(SALT);
    state.close();
  });

  test("a kid is never the admin and never has a PIN, whatever a column left from its grown-up days says", () => {
    const { state, path } = store();
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => db.query("UPDATE profiles SET admin_claimed_at = 1, pin_hash = ?2, pin_salt = ?3, pin_updated_at = 1 WHERE id = ?1")
      .run(mia.id, HASH, SALT));
    expect(state.profiles()[0]).toMatchObject({ kids: true, admin: false, hasPin: false });
    state.close();
  });
});

describe("the record a device writes", () => {
  test("says who is admin, a kid's limit and parent, and a grown-up's PIN", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    household(path, me.id, mia.id);
    const byName = new Map(state.exportRecord("laptop").profiles.map((profile) => [profile.name, profile]));
    expect(byName.get("André")).toMatchObject({ admin: { claimedAt: 500 }, pin: { hash: HASH, salt: SALT, updatedAt: 600 } });
    expect(byName.get("Mia")).toMatchObject({ kids: true, kidsAge: { age: 6, updatedAt: 700 }, parent: "André" });
    for (const key of ["kids", "kidsAge", "parent"]) expect(key in byName.get("André")!).toBe(false);
    for (const key of ["admin", "pin"]) expect(key in byName.get("Mia")!).toBe(false);
    state.close();
  });

  test("a kid from before limits is FSK 12 at time 0, and a parent not here is not named", () => {
    const { state, path } = store();
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => db.query("UPDATE profiles SET kids_age = NULL, parent_id = 'gone' WHERE id = ?1").run(mia.id));
    const [written] = state.exportRecord("laptop").profiles;
    expect(written!.kidsAge).toEqual({ age: 12, updatedAt: 0 });
    expect("parent" in written!).toBe(false);
    state.close();
  });
});

/** A merged viewer with nothing but what a test says. */
const viewer = (name: string, extra: Partial<MergedProfile> = {}): MergedProfile =>
  ({ name: normalName(name)!, displayName: name, progress: [], watched: [], ...extra });

describe("taking in what the devices agreed on", () => {
  test("a newer limit or PIN is applied, an older one is not", () => {
    const { state } = store();
    const mia = state.createProfile("Mia", true)!;
    const me = state.createProfile("André")!;
    expect(state.importMerged({ profiles: [
      viewer("Mia", { kids: true, kidsAge: { age: 6, updatedAt: 1000 } }),
      viewer("André", { pin: { hash: HASH, salt: SALT, updatedAt: 1000 } }),
    ] })).toBe(2);
    expect(state.importMerged({ profiles: [
      viewer("Mia", { kids: true, kidsAge: { age: 12, updatedAt: 999 } }),
      viewer("André", { pin: { hash: "0".repeat(64), salt: SALT, updatedAt: 999 } }),
    ] })).toBe(0);
    const byId = new Map(state.profiles().map((profile) => [profile.id, profile]));
    expect(byId.get(mia.id)!.kidsAge).toBe(6);
    expect(byId.get(me.id)!.hasPin).toBe(true);
    expect(state.exportRecord("x").profiles.find((profile) => profile.name === "André")!.pin!.hash).toBe(HASH);
    state.close();
  });

  test("an equal time with a different value takes the merge's winner, once", () => {
    const { state } = store();
    state.createProfile("Mia", true);
    const at = (age: 6 | 12) => ({ profiles: [viewer("Mia", { kids: true, kidsAge: { age, updatedAt: 1000 } })] });
    expect(state.importMerged(at(6))).toBe(1);
    expect(state.importMerged(at(12))).toBe(1);
    expect(state.importMerged(at(12))).toBe(0);
    expect(state.profiles()[0]!.kidsAge).toBe(12);
    state.close();
  });

  test("a grown-up another device calls a kid becomes one at FSK 12, then takes the merged limit", () => {
    const { state } = store();
    state.createProfile("Mia");
    state.importMerged({ profiles: [viewer("Mia", { kids: true, kidsAge: { age: 12, updatedAt: 0 } })] });
    expect(state.profiles()[0]).toMatchObject({ kids: true, kidsAge: 12 });
    state.importMerged({ profiles: [viewer("Mia", { kids: true, kidsAge: { age: 6, updatedAt: 5 } })] });
    expect(state.profiles()[0]!.kidsAge).toBe(6);
    state.close();
  });

  test("a kid's parent is set once, to the local profile of that name, and never moved", () => {
    const { state } = store();
    const maja = state.createProfile("Maja")!;
    state.createProfile("André");
    const mia = state.createProfile("Mia", true)!;
    expect(state.importMerged({ profiles: [viewer("Mia", { kids: true, parent: "maja" })] })).toBe(1);
    expect(state.importMerged({ profiles: [viewer("Mia", { kids: true, parent: "andré" })] })).toBe(0);
    expect(state.profiles().find((profile) => profile.id === mia.id)!.parentId).toBe(maja.id);
    state.close();
  });

  test("a parent met in the same import as its kid is still found", () => {
    const { state } = store();
    state.importMerged({ profiles: [viewer("Mia", { kids: true, parent: "maja" }), viewer("Maja")] });
    const byName = new Map(state.profiles().map((profile) => [profile.name, profile]));
    expect(byName.get("Mia")!.parentId).toBe(byName.get("Maja")!.id);
    state.close();
  });

  test("the merge's admin becomes the only admin here, claimed when the merge says", () => {
    const { state, path } = store();
    state.createProfile("André");
    const maja = state.createProfile("Maja")!;
    sql(path, (db) => db.query("UPDATE profiles SET admin_claimed_at = 2000 WHERE id = ?1").run(maja.id));
    expect(state.importMerged({ profiles: [viewer("André", { admin: { claimedAt: 1000 } }), viewer("Maja")] })).toBe(2);
    expect(state.profiles().filter((profile) => profile.admin).map((profile) => profile.name)).toEqual(["André"]);
    expect(state.exportRecord("x").profiles.find((profile) => profile.name === "André")!.admin).toEqual({ claimedAt: 1000 });
    state.close();
  });

  test("a merge that names no admin changes nothing", () => {
    const { state, path } = store();
    const maja = state.createProfile("Maja")!;
    sql(path, (db) => db.query("UPDATE profiles SET admin_claimed_at = 2000 WHERE id = ?1").run(maja.id));
    expect(state.importMerged({ profiles: [viewer("Maja")] })).toBe(0);
    expect(state.profiles()[0]!.admin).toBe(true);
    state.close();
  });

  test("an older build's document, saying only kids, does not undo a limit a parent set", () => {
    const { state, path } = store();
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => db.query("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 700 WHERE id = ?1").run(mia.id));
    const old: SyncRecord = {
      format: 1, device: "old-tv", writtenAt: 0,
      profiles: [{ name: "Mia", kids: true, progress: [], watched: [] }],
    };
    expect(state.importMerged(mergeStates([state.exportRecord("laptop"), old]))).toBe(0);
    expect(state.profiles()[0]!.kidsAge).toBe(6);
    state.close();
  });

  test("two devices agree on a limit, an admin, a parent and a PIN, and a second round changes nothing", () => {
    const laptop = store();
    const tv = store();
    const me = laptop.state.createProfile("André")!;
    const mia = laptop.state.createProfile("Mia", true)!;
    household(laptop.path, me.id, mia.id);
    const wire = (state: WatchState, device: string) => parseRecord(JSON.stringify(state.exportRecord(device)))!;
    const round = () => tv.state.importMerged(mergeStates([wire(tv.state, "tv"), wire(laptop.state, "laptop")]));
    expect(round()).toBeGreaterThan(0);
    const byName = new Map(tv.state.profiles().map((profile) => [profile.name, profile]));
    expect(byName.get("André")).toMatchObject({ admin: true, hasPin: true, kids: false });
    expect(byName.get("Mia")).toMatchObject({ kids: true, kidsAge: 6, parentId: byName.get("André")!.id });
    expect(round()).toBe(0);
    laptop.state.close();
    tv.state.close();
  });

  test("importing its own document changes nothing", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    household(path, me.id, mia.id);
    expect(state.importMerged(mergeStates([state.exportRecord("self")]))).toBe(0);
    state.close();
  });
});
