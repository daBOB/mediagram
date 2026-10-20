/**
 * Managing profiles inside each household: who a new profile lands beside,
 * what one household's admin cannot touch in another, and who may claim a
 * household nobody runs.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { Profile } from "../src/state/profiles";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** The PIN `pin-hash.json` gives for this salt is 1234. */
const PIN = "1234";
const KEY = `'f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924', '00112233445566778899aabbccddeeff'`;

/** A store holding what `rows` writes straight into its file. */
function storeWith(rows: string[]): WatchState {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-households-manage-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  new WatchState(path).close();
  const db = new Database(path);
  for (const row of rows) db.exec(row);
  db.close();
  return new WatchState(path);
}

/** A grown-up with PIN 1234, in `household`; `claimed` makes them the owner. */
const grownUp = (id: string, household: string, claimed = false, created = 1) =>
  `INSERT INTO profiles(id, name, created_at, pin_hash, pin_salt, pin_updated_at, household_id, admin_claimed_at)
     VALUES ('${id}', '${id}', ${created}, ${KEY}, 1, '${household}', ${claimed ? 1 : "NULL"})`;
const household = (id: string, admin: string | null) =>
  `INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('${id}', '${id}', ${admin ? `'${admin}'` : "NULL"}, 1)`;
const placed = (state: WatchState, id: string) => state.profiles().find((one) => one.id === id);

describe("managing inside a household", () => {
  test("a household's admin adds a grown-up into its own household", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2)]);
    const made = state.manage().createGrownUp("maja", PIN, "Lena", "5678") as Profile;
    expect(made).toMatchObject({ name: "Lena", household: "flat" });
    const home = state.manage().createGrownUp("owner", PIN, "Paul", "5678") as Profile;
    expect(home.household).toBe("home");
  });

  test("a kid is made in its parent's household", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2)]);
    const kid = state.manage().createKid("maja", PIN, "Mia", 6) as Profile;
    expect(kid).toMatchObject({ kids: true, household: "flat" });
  });

  test("the admin of one household cannot remove a grown-up of another", () => {
    const state = storeWith([
      grownUp("owner", "home", true),
      household("flat", "maja"),
      grownUp("maja", "flat", false, 2),
      household("loft", "lena"),
      grownUp("lena", "loft", false, 3),
      grownUp("paula", "loft", false, 4),
    ]);
    expect(state.manage().remove("maja", PIN, "paula")).toEqual({ reason: "not-allowed" });
    expect(placed(state, "paula")).toBeDefined();
    expect(state.manage().remove("lena", PIN, "paula")).toBeNull();
  });
});

describe("claiming", () => {
  test("with no owner yet, the claim makes the owner, who runs Home", () => {
    const state = storeWith([grownUp("andre", "home").replace(", 1)", ", NULL)")]);
    expect(state.manage().claimAdmin("andre", PIN)).toBeNull();
    expect(placed(state, "andre")).toMatchObject({ owner: true, admin: true, household: "home" });
  });

  test("a grown-up of a household nobody runs may claim it while the install has an owner", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", null), grownUp("maja", "flat", false, 2)]);
    expect(placed(state, "maja")?.admin).toBe(false);
    expect(state.manage().claimAdmin("maja", "0000")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().claimAdmin("maja", PIN)).toBeNull();
    expect(placed(state, "maja")).toMatchObject({ admin: true, owner: false });
    expect(placed(state, "owner")?.admin).toBe(true);
  });

  test("a grown-up with no PIN claims with the first one given", () => {
    const state = storeWith([
      grownUp("owner", "home", true),
      household("flat", null),
      "INSERT INTO profiles(id, name, created_at, household_id) VALUES ('maja', 'maja', 2, 'flat')",
    ]);
    expect(state.manage().claimAdmin("maja", null)).toEqual({ reason: "invalid" });
    expect(state.manage().claimAdmin("maja", "5678")).toBeNull();
    expect(state.manage().unlock("maja", "5678")).toBeNull();
    expect(placed(state, "maja")?.admin).toBe(true);
  });

  test("nobody claims a household somebody runs", () => {
    const state = storeWith([
      grownUp("owner", "home", true),
      household("flat", "maja"),
      grownUp("maja", "flat", false, 2),
      grownUp("lena", "flat", false, 3),
    ]);
    expect(state.manage().claimAdmin("lena", PIN)).toEqual({ reason: "not-allowed" });
    expect(state.manage().claimAdmin("owner", PIN)).toEqual({ reason: "not-allowed" });
    expect(placed(state, "maja")?.admin).toBe(true);
  });
});
