/**
 * Migrating the player's own database.
 *
 * The case worth pinning is a second open. Applying every migration on every
 * open survives a schema built from `CREATE TABLE IF NOT EXISTS` and destroys
 * one that rebuilds a table — the replay copies the live rows into a fresh
 * table, drops the original and renames the copy over it. It reads as working
 * right up until someone notices whose rows they now are.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { GROUPS } from "../src/state/schema";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
const tempPath = () => {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-migrate-"));
  dirs.push(dir);
  return join(dir, "state.db");
};

afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** A database at version 1, as a player that ran before profiles left one. */
function v1With(path: string, rows: () => string[]): void {
  const db = new Database(path, { create: true });
  for (const statement of GROUPS[0]!) db.exec(statement);
  db.query("INSERT INTO state_meta(key, value) VALUES ('schema_version', '1')").run();
  for (const statement of rows()) db.exec(statement);
  db.close();
}

describe("v1 to v2", () => {
  test("what was recorded before profiles becomes somebody's", () => {
    const path = tempPath();
    v1With(path, () => [
      "INSERT INTO progress(set_id, at_seconds, duration, updated_at) VALUES ('01SET', 640, 2400, 1)",
      "INSERT INTO watchlist(set_id, added_at) VALUES ('01OTHER', 1)",
      "INSERT INTO collections(id, name, created_at) VALUES ('c1', 'Alt', 1)",
      "INSERT INTO collection_items(collection_id, set_id, position) VALUES ('c1', '01SET', 0)",
    ]);

    const state = new WatchState(path);
    const [adopted] = state.profiles();
    expect(adopted?.name).toBe("Everyone");

    const held = state.snapshot(adopted!.id);
    expect(held.progress[0]).toMatchObject({ setId: "01SET", at: 640 });
    expect(held.watchlist).toEqual(["01OTHER"]);
    expect(held.collections[0]).toMatchObject({ name: "Alt", items: ["01SET"] });
    state.close();
  });

  test("a database that recorded nothing gains no one to adopt it", () => {
    const path = tempPath();
    v1With(path, () => []);

    const state = new WatchState(path);
    // Nobody watched anything, so there is nobody to invent — the page asks.
    expect(state.profiles()).toEqual([]);
    state.close();
  });

  test("opening again changes nothing at all", () => {
    const path = tempPath();
    v1With(path, () => [
      "INSERT INTO progress(set_id, at_seconds, duration, updated_at) VALUES ('01SET', 640, 2400, 1)",
    ]);

    const first = new WatchState(path);
    const adopted = first.profiles()[0]!.id;
    const before = first.snapshot(adopted);
    first.close();

    // The replay bug lands here: a second run of the v2 group would invent a
    // second profile and rebuild `progress` around it.
    const second = new WatchState(path);
    expect(second.profiles()).toHaveLength(1);
    expect(second.snapshot(adopted)).toEqual(before);
    second.close();
  });

  test("a fresh database opened three times holds one of everything", () => {
    const path = tempPath();

    const first = new WatchState(path);
    const me = first.createProfile("André")!.id;
    first.setProgress(me, "01SET", 100, 2400);
    first.close();

    for (let open = 0; open < 3; open++) {
      const again = new WatchState(path);
      expect(again.profiles()).toHaveLength(1);
      expect(again.snapshot(me).progress).toHaveLength(1);
      again.close();
    }
  });
});

describe("v2 to v3", () => {
  /** A database at version 2: profiles, but nothing marked for children. */
  function v2With(path: string, rows: () => string[]): void {
    const db = new Database(path, { create: true });
    for (const statement of [...GROUPS[0]!, ...GROUPS[1]!]) db.exec(statement);
    db.query("INSERT OR REPLACE INTO state_meta(key, value) VALUES ('schema_version', '2')").run();
    for (const statement of rows()) db.exec(statement);
    db.close();
  }

  test("carries an existing player's profiles and lists across untouched", () => {
    const path = tempPath();
    v2With(path, () => [
      `INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)`,
      `INSERT INTO watchlist(profile_id, set_id, added_at) VALUES ('p1', 'aset', 1)`,
    ]);

    const state = new WatchState(path);
    expect(state.profiles().map((p) => p.name)).toEqual(["André"]);
    expect(state.snapshot("p1").watchlist).toEqual(["aset"]);
    // And the new shelf starts empty rather than absent.
    expect(state.kids()).toEqual([]);
    state.close();
  });

  test("gives a marked title no owner, so it outlives every profile", () => {
    const path = tempPath();
    v2With(path, () => [`INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'A', 1)`]);

    const state = new WatchState(path);
    state.setKids("aset", true);
    state.deleteProfile("p1");
    expect(state.kids()).toEqual(["aset"]);
    state.close();
  });

  test("opening again changes nothing at all", () => {
    const path = tempPath();
    v2With(path, () => [`INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'A', 1)`]);

    const first = new WatchState(path);
    first.setKids("aset", true);
    first.close();

    // The replay hazard this file exists for: a migration that rebuilt the
    // table would lose the mark on the second open.
    const second = new WatchState(path);
    expect(second.kids()).toEqual(["aset"]);
    second.close();
  });
});

describe("v3 to v4", () => {
  /** A database at version 3: kids marked, nothing recorded as finished. */
  function v3With(path: string, rows: () => string[]): void {
    const db = new Database(path, { create: true });
    for (const statement of [...GROUPS[0]!, ...GROUPS[1]!, ...GROUPS[2]!]) db.exec(statement);
    db.query("INSERT OR REPLACE INTO state_meta(key, value) VALUES ('schema_version', '3')").run();
    for (const statement of rows()) db.exec(statement);
    db.close();
  }

  test("carries profiles, lists and kids marks across untouched", () => {
    const path = tempPath();
    v3With(path, () => [
      `INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)`,
      `INSERT INTO watchlist(profile_id, set_id, added_at) VALUES ('p1', 'aset', 1)`,
      `INSERT INTO kids(set_id, marked_at) VALUES ('kset', 1)`,
    ]);

    const state = new WatchState(path);
    expect(state.profiles().map((p) => p.name)).toEqual(["André"]);
    expect(state.snapshot("p1").watchlist).toEqual(["aset"]);
    expect(state.kids()).toEqual(["kset"]);
    // Starting from nothing was the decision: no history is invented for
    // titles finished before there was anywhere to record it.
    expect(state.snapshot("p1").watched).toEqual([]);
    state.close();
  });

  test("gives what was finished to the profile that finished it", () => {
    const path = tempPath();
    v3With(path, () => [
      `INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'A', 1)`,
      `INSERT INTO profiles(id, name, created_at) VALUES ('p2', 'B', 2)`,
    ]);

    const state = new WatchState(path);
    state.setWatched("p1", "aset", true);
    expect(state.snapshot("p1").watched).toEqual(["aset"]);
    expect(state.snapshot("p2").watched).toEqual([]);
    state.close();
  });

  test("opening again changes nothing at all", () => {
    const path = tempPath();
    v3With(path, () => [`INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'A', 1)`]);

    const first = new WatchState(path);
    first.setWatched("p1", "aset", true);
    first.close();

    const second = new WatchState(path);
    expect(second.snapshot("p1").watched).toEqual(["aset"]);
    second.close();
  });
});
