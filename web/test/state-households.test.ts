/**
 * Households on the server: which one each profile is listed in, who runs
 * each, and the live households the profiles page is told about.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { insertProfile } from "../src/state/profiles-insert";
import { GROUPS } from "../src/state/schema";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** A store holding what `rows` writes straight into its file. */
function storeWith(rows: string[]): WatchState {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-households-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  new WatchState(path).close();
  const db = new Database(path);
  for (const row of rows) db.exec(row);
  db.close();
  return new WatchState(path);
}

const placed = (state: WatchState) =>
  Object.fromEntries(state.profiles().map(({ id, household, admin }) => [id, { household, admin }]));

describe("which household a profile is listed in", () => {
  test("a kid is listed in its parent's household", () => {
    const state = storeWith([
      "INSERT INTO households(id, name) VALUES ('flat', 'Flat')",
      "INSERT INTO profiles(id, name, created_at, household_id) VALUES ('p1', 'Maja', 1, 'flat')",
      // Its own field still says Home: moved with its parent on a device that has not written it yet.
      "INSERT INTO profiles(id, name, created_at, kids, kids_age, parent_id) VALUES ('k1', 'Mia', 2, 1, 6, 'p1')",
    ]);
    expect(placed(state).k1).toEqual({ household: "flat", admin: false });
    state.close();
  });

  test("a profile whose household is gone is listed in Home", () => {
    const state = storeWith([
      "INSERT INTO households(id, name, removed) VALUES ('flat', 'Flat', 1)",
      "INSERT INTO profiles(id, name, created_at, household_id) VALUES ('p1', 'Maja', 1, 'flat')",
      "INSERT INTO profiles(id, name, created_at, household_id) VALUES ('p2', 'Tim', 2, 'never-heard-of')",
    ]);
    expect(placed(state)).toEqual({ p1: { household: "home", admin: false }, p2: { household: "home", admin: false } });
    state.close();
  });

  test("the owner runs the household they are in", () => {
    const state = storeWith([
      "INSERT INTO profiles(id, name, created_at, admin_claimed_at) VALUES ('p1', 'André', 1, 5)",
      "INSERT INTO profiles(id, name, created_at) VALUES ('p2', 'Maja', 2)",
      "INSERT INTO profiles(id, name, created_at, household_id) VALUES ('p3', 'Tim', 3, 'flat')",
      // Home's record names Maja, but the owner is in Home; the flat's names Tim, who is there.
      "UPDATE households SET admin_id = 'p2', admin_updated_at = 7 WHERE id = 'home'",
      "INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('flat', 'Flat', 'p3', 7)",
    ]);
    expect(state.profiles().map(({ id, owner, admin }) => ({ id, owner, admin }))).toEqual([
      { id: "p1", owner: true, admin: true },
      { id: "p2", owner: false, admin: false },
      { id: "p3", owner: false, admin: true },
    ]);
    state.close();
  });
});

describe("listing households", () => {
  test("households are listed live, by name, each with who runs it", () => {
    const state = storeWith([
      "INSERT INTO profiles(id, name, created_at, admin_claimed_at) VALUES ('p1', 'André', 1, 5)",
      "INSERT INTO profiles(id, name, created_at, household_id) VALUES ('p2', 'Tim', 2, 'zebra')",
      "INSERT INTO households(id, name, admin_id) VALUES ('zebra', 'zebra flat', 'p2')",
      // Names someone in another household, so nobody runs it.
      "INSERT INTO households(id, name, admin_id) VALUES ('doctors', 'Ärzte', 'p1')",
      "INSERT INTO households(id, name) VALUES ('attic', 'attic')",
      "INSERT INTO households(id, name, removed) VALUES ('cellar', 'Cellar', 1)",
    ]);
    // By normalised name in code-point order: "attic" before "home", and "ärzte" after "zebra flat".
    expect(state.households()).toEqual([
      { id: "attic", name: "attic", adminId: null },
      { id: "home", name: "Home", adminId: "p1" },
      { id: "zebra", name: "zebra flat", adminId: "p2" },
      { id: "doctors", name: "Ärzte", adminId: null },
    ]);
    state.close();
  });
});

describe("making a profile", () => {
  test("one made in a household is dated then, and one made without is in Home with nothing dated", () => {
    const db = new Database(":memory:");
    for (const statement of GROUPS.flat()) db.exec(statement);
    db.exec("INSERT INTO households(id, name) VALUES ('flat', 'Flat')");

    const owner = insertProfile(db, "André", { admin: true });
    const maja = insertProfile(db, "Maja", { household: "flat" });
    expect(owner).toMatchObject({ household: "home", owner: true, admin: true });
    expect(maja).toMatchObject({ household: "flat", owner: false, admin: false });
    expect(db.query("SELECT household_id AS household, household_updated_at AS at FROM profiles ORDER BY created_at, name").all())
      .toEqual([{ household: "home", at: 0 }, { household: "flat", at: maja!.createdAt }]);
    db.close();
  });
});
