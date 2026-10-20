/**
 * Households on the sync record: what a device writes about them and each
 * profile's household, and what it takes in from the merge.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { householdRows } from "../src/state/households";
import { mergeStates } from "../src/state/merge";
import type { MergedProfile, MergedState } from "../src/state/merged";
import { WatchState } from "../src/state/store";
import { normalName, parseRecord, type SyncRecord } from "../src/state/sync-record";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-households-sync-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  return { state: new WatchState(path), path };
}

/** Writes what no method here writes yet, through a second connection. */
function sql(path: string, ...statements: string[]): void {
  const db = new Database(path);
  try {
    for (const statement of statements) db.exec(statement);
  } finally {
    db.close();
  }
}

/** The stored households, removed ones too. */
function rows(path: string) {
  const db = new Database(path);
  try {
    return householdRows(db);
  } finally {
    db.close();
  }
}

const viewer = (name: string, extra: Partial<MergedProfile> = {}): MergedProfile =>
  ({ name: normalName(name)!, displayName: name, progress: [], watched: [], ...extra });

const merged = (households: MergedState["households"], ...profiles: MergedProfile[]): MergedState =>
  ({ profiles, households });

describe("what a device says about households", () => {
  test("a household nobody changed is not said", () => {
    const { state } = store();
    state.createProfile("André");
    state.createProfile("Mia", true);
    const record = state.exportRecord("laptop");
    expect("households" in record).toBe(false);
    for (const profile of record.profiles) expect("household" in profile).toBe(false);
    expect(JSON.stringify(record)).not.toContain("household");
    state.close();
  });

  test("a renamed Home is said with its name alone", () => {
    const { state, path } = store();
    state.createProfile("André");
    sql(path, "UPDATE households SET name = 'Borck', name_updated_at = 1000 WHERE id = 'home'");
    expect(state.exportRecord("laptop").households).toEqual([{ id: "home", name: { text: "Borck", updatedAt: 1000 } }]);
    state.close();
  });

  test("an admin whose profile is not here is not said", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    sql(path,
      "INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Oma', 1000, 'gone', 1100)",
      // Nothing left to say once the admin is not: the row is not written at all.
      "INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('c', 'Flat', 'gone', 1100)",
      `INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('d', 'Mine', '${me.id}', 1200)`,
      "INSERT INTO households(id, name, removed) VALUES ('e', 'Gone', 1)");
    expect(state.exportRecord("laptop").households).toEqual([
      { id: "b", name: { text: "Oma", updatedAt: 1000 } },
      { id: "d", admin: { name: "André", updatedAt: 1200 } },
      { id: "e", removed: true },
    ]);
    state.close();
  });

  test("a profile's household is said once it is dated", () => {
    const { state, path } = store();
    const maja = state.createProfile("Maja")!;
    state.createProfile("Tim");
    // As stored, a removed household too: only the view reads it as Home.
    sql(path,
      "INSERT INTO households(id, name, removed) VALUES ('b', 'Oma', 1)",
      `UPDATE profiles SET household_id = 'b', household_updated_at = 1500 WHERE id = '${maja.id}'`);
    const byName = new Map(state.exportRecord("laptop").profiles.map((profile) => [profile.name, profile]));
    expect(byName.get("Maja")!.household).toEqual({ id: "b", updatedAt: 1500 });
    expect("household" in byName.get("Tim")!).toBe(false);
    state.close();
  });
});

describe("taking in households from the merge", () => {
  test("a household heard from another device is made here, its admin found by name", () => {
    const { state, path } = store();
    const oma = state.createProfile("Oma")!;
    expect(state.importMerged(merged([
      { id: "b", name: { text: "Oma", updatedAt: 1000 }, admin: { name: "oma", updatedAt: 1100 } },
      // No name to make it with: not made.
      { id: "c", admin: { name: "oma", updatedAt: 1100 } },
    ], viewer("Oma", { household: { id: "b", updatedAt: 1200 } })))).toBe(3); // made, its admin, Oma moved in
    expect(rows(path).filter((row) => row.id !== "home")).toEqual([
      { id: "b", name: "Oma", nameUpdatedAt: 1000, adminId: oma.id, adminUpdatedAt: 1100, removed: 0 },
    ]);
    expect(state.households().find((one) => one.id === "b")?.adminId).toBe(oma.id);
    state.close();
  });

  test("a removal is taken, and never undone", () => {
    const { state, path } = store();
    sql(path, "INSERT INTO households(id, name, name_updated_at) VALUES ('b', 'Oma', 1000)");
    expect(state.importMerged(merged([{ id: "b", removed: true }]))).toBe(1);
    expect(state.importMerged(merged([{ id: "b", name: { text: "Oma", updatedAt: 1000 } }]))).toBe(0);
    expect(rows(path).find((row) => row.id === "b")?.removed).toBe(1);
    // Home is never removed, whatever a hand-built merge says.
    expect(state.importMerged(merged([{ id: "home", removed: true }]))).toBe(0);
    state.close();
  });

  test("a newer local rename survives an older merge", () => {
    const { state, path } = store();
    sql(path, "UPDATE households SET name = 'Borck', name_updated_at = 2000 WHERE id = 'home'");
    expect(state.importMerged(merged([{ id: "home", name: { text: "Old", updatedAt: 1000 } }]))).toBe(0);
    expect(rows(path).find((row) => row.id === "home")?.name).toBe("Borck");
    // As new and different is the merge's winner by device id.
    expect(state.importMerged(merged([{ id: "home", name: { text: "Tv", updatedAt: 2000 } }]))).toBe(1);
    expect(rows(path).find((row) => row.id === "home")?.name).toBe("Tv");
    state.close();
  });

  test("a profile's newer household is taken", () => {
    const { state, path } = store();
    const mia = state.createProfile("Mia")!;
    sql(path, `UPDATE profiles SET household_id = 'c', household_updated_at = 1000 WHERE id = '${mia.id}'`);
    const move = (id: string, updatedAt: number) =>
      state.importMerged(merged(undefined, viewer("Mia", { household: { id, updatedAt } })));
    expect(move("home", 900)).toBe(0);
    expect(move("b", 2000)).toBe(1);
    expect(move("b", 2000)).toBe(0);
    expect(state.exportRecord("x").profiles[0]!.household).toEqual({ id: "b", updatedAt: 2000 });
    state.close();
  });

  test("a document from an older build changes no household", () => {
    const { state, path } = store();
    const maja = state.createProfile("Maja")!;
    sql(path,
      `INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Oma', 1000, '${maja.id}', 1000)`,
      `UPDATE profiles SET household_id = 'b', household_updated_at = 1000 WHERE id = '${maja.id}'`);
    const before = rows(path);
    const old: SyncRecord = { format: 1, device: "old-tv", writtenAt: 0, profiles: [{ name: "Maja", progress: [], watched: [] }] };
    expect(state.importMerged(mergeStates([state.exportRecord("laptop"), old]))).toBe(0);
    expect(rows(path)).toEqual(before);
    expect(state.profiles()[0]!.household).toBe("b");
    state.close();
  });

  test("two devices agree on households, and a second round changes nothing", () => {
    const laptop = store();
    const tv = store();
    const oma = laptop.state.createProfile("Oma")!;
    sql(laptop.path,
      "UPDATE households SET name = 'Borck', name_updated_at = 900 WHERE id = 'home'",
      `INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Oma', 1000, '${oma.id}', 1000)`,
      "INSERT INTO households(id, name, name_updated_at, removed) VALUES ('c', 'Flat', 1000, 1)",
      `UPDATE profiles SET household_id = 'b', household_updated_at = 1000 WHERE id = '${oma.id}'`);
    const wire = (state: WatchState, device: string) => parseRecord(JSON.stringify(state.exportRecord(device)))!;
    const round = () => tv.state.importMerged(mergeStates([wire(tv.state, "tv"), wire(laptop.state, "laptop")]));
    expect(round()).toBeGreaterThan(0);
    expect(tv.state.households().map(({ id, name }) => ({ id, name }))).toEqual([
      { id: "home", name: "Borck" }, { id: "b", name: "Oma" },
    ]);
    expect(tv.state.profiles()[0]).toMatchObject({ name: "Oma", household: "b", admin: true });
    expect(round()).toBe(0);
    expect(laptop.state.importMerged(mergeStates([wire(laptop.state, "laptop")]))).toBe(0);
    laptop.state.close();
    tv.state.close();
  });
});
