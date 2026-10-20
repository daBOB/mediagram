/**
 * Managing profiles inside each household: who a new profile lands beside,
 * what one household's admin cannot touch in another, who may claim a
 * household nobody runs, the owner's actions on households, and joining one.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { householdRows, type Household } from "../src/state/households";
import { profileRows, type Profile } from "../src/state/profiles";
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
  const state = new WatchState(path);
  paths.set(state, path);
  return state;
}

const paths = new WeakMap<WatchState, string>();

/** What `state` has stored, read through a second connection. */
function stored<T>(state: WatchState, read: (db: Database) => T): T {
  const db = new Database(paths.get(state)!);
  try {
    return read(db);
  } finally {
    db.close();
  }
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

/** A kid of `parent`, stored in `household`. */
const kid = (id: string, parent: string, household: string) =>
  `INSERT INTO profiles(id, name, created_at, kids, kids_age, parent_id, household_id)
     VALUES ('${id}', '${id}', 9, 1, 12, '${parent}', '${household}')`;
/** A grown-up from before PINs, in `household`. */
const pinless = (id: string, household: string) =>
  `INSERT INTO profiles(id, name, created_at, household_id) VALUES ('${id}', '${id}', 5, '${household}')`;
const rowOf = (state: WatchState, id: string) => stored(state, profileRows).find((one) => one.id === id)!;
const householdOf = (state: WatchState, id: string) => stored(state, householdRows).find((one) => one.id === id);

describe("the owner's actions", () => {
  test("the owner creates a household with a new grown-up, who runs it", () => {
    const state = storeWith([grownUp("owner", "home", true)]);
    const made = state.manage().createHousehold("owner", PIN, " Flat ", { name: "Maja", newPin: "5678" }) as Household;
    const maja = state.profiles().find((one) => one.name === "Maja")!;
    expect(made).toEqual({ id: expect.any(String), name: "Flat", adminId: maja.id });
    expect(maja).toMatchObject({ household: made.id, admin: true, hasPin: true });
    expect(state.manage().unlock(maja.id, "5678")).toBeNull();
    expect(state.households().map((one) => one.name)).toEqual(["Flat", "Home"]);
  });

  test("the owner creates one with an existing grown-up, who moves in with their kids", () => {
    const state = storeWith([grownUp("owner", "home", true), grownUp("paul", "home", false, 2), kid("mia", "paul", "home")]);
    const made = state.manage().createHousehold("owner", PIN, "Loft", { id: "paul" }) as Household;
    expect(made).toMatchObject({ name: "Loft", adminId: "paul" });
    expect(placed(state, "paul")).toMatchObject({ household: made.id, admin: true });
    expect(placed(state, "mia")?.household).toBe(made.id);
    for (const id of ["paul", "mia"]) {
      expect(rowOf(state, id)).toMatchObject({ householdId: made.id, householdUpdatedAt: expect.any(Number) });
      expect(rowOf(state, id).householdUpdatedAt).toBeGreaterThan(0);
    }
  });

  test("a household made here is said with its name", () => {
    const state = storeWith([grownUp("owner", "home", true)]);
    const made = state.manage().createHousehold("owner", PIN, "Flat", { name: "Maja", newPin: "5678" }) as Household;
    const said = state.exportRecord("laptop").households?.find((one) => one.id === made.id);
    expect(said?.name?.text).toBe("Flat");
    expect(said?.name?.updatedAt).toBeGreaterThan(0);
    expect(said?.admin?.name).toBe("Maja");
    expect(said?.admin?.updatedAt).toBeGreaterThan(0);
  });

  test("a household's name is unique among the live ones", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", null),
      "INSERT INTO households(id, name, removed) VALUES ('gone', 'Loft', 1)"]);
    const manage = state.manage();
    expect(manage.createHousehold("owner", PIN, " FLAT", { name: "Maja", newPin: "5678" })).toEqual({ reason: "name-taken" });
    expect(manage.renameHousehold("owner", PIN, "home", "flat")).toEqual({ reason: "name-taken" });
    expect(manage.renameHousehold("owner", PIN, "flat", "Flat")).toBeNull();
    // A deleted household's name is free again.
    expect(manage.createHousehold("owner", PIN, "Loft", { name: "Maja", newPin: "5678" })).toMatchObject({ name: "Loft" });
    expect(manage.renameHousehold("owner", PIN, "home", "  ")).toEqual({ reason: "invalid" });
    expect(manage.renameHousehold("owner", PIN, "home", "Borck")).toBeNull();
    expect(householdOf(state, "home")).toMatchObject({ name: "Borck" });
    expect(householdOf(state, "home")!.nameUpdatedAt).toBeGreaterThan(0);
  });

  test("a new grown-up's name is unique on the whole install", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "lena"), grownUp("lena", "flat", false, 2)]);
    expect(state.manage().createHousehold("owner", PIN, "Loft", { name: " LENA ", newPin: "5678" }))
      .toEqual({ reason: "name-taken" });
    expect(state.households().map((one) => one.name)).toEqual(["flat", "Home"]);
  });

  test("an admin without a PIN is given one, and refused without", () => {
    const state = storeWith([grownUp("owner", "home", true), pinless("paul", "home"), household("flat", null), pinless("lena", "flat")]);
    const manage = state.manage();
    expect(manage.createHousehold("owner", PIN, "Loft", { name: "Maja" })).toEqual({ reason: "no-pin" });
    expect(manage.createHousehold("owner", PIN, "Loft", { name: "Maja", newPin: "12" })).toEqual({ reason: "invalid" });
    expect(manage.createHousehold("owner", PIN, "Loft", { id: "paul" })).toEqual({ reason: "no-pin" });
    expect(manage.createHousehold("owner", PIN, "Loft", { id: "paul", newPin: "5678" })).toMatchObject({ adminId: "paul" });
    expect(manage.unlock("paul", "5678")).toBeNull();
    expect(manage.setHouseholdAdmin("owner", PIN, "flat", "lena")).toEqual({ reason: "no-pin" });
    expect(manage.setHouseholdAdmin("owner", PIN, "flat", "lena", "x")).toEqual({ reason: "invalid" });
    expect(placed(state, "lena")?.admin).toBe(false);
    expect(manage.setHouseholdAdmin("owner", PIN, "flat", "lena", "4321")).toBeNull();
    expect(placed(state, "lena")?.admin).toBe(true);
    expect(manage.unlock("lena", "4321")).toBeNull();
    expect(householdOf(state, "flat")!.adminUpdatedAt).toBeGreaterThan(1);
  });

  test("the owner moves a grown-up and their kids, each dated", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2),
      grownUp("paul", "home", false, 3), kid("mia", "paul", "home")]);
    expect(state.manage().moveToHousehold("owner", PIN, "flat", "paul")).toBeNull();
    for (const id of ["paul", "mia"]) {
      expect(placed(state, id)?.household).toBe("flat");
      expect(rowOf(state, id).householdUpdatedAt).toBeGreaterThan(0);
    }
    // The household already had someone running it.
    expect(placed(state, "maja")?.admin).toBe(true);
    expect(placed(state, "paul")?.admin).toBe(false);
    expect(state.manage().moveToHousehold("owner", PIN, "flat", "paul")).toEqual({ reason: "not-allowed" });
    expect(state.manage().moveToHousehold("owner", PIN, "flat", "mia")).toEqual({ reason: "not-allowed" });
  });

  test("a grown-up moved into a household nobody runs runs it", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", null), grownUp("paul", "home", false, 3),
      pinless("lena", "home")]);
    expect(state.manage().moveToHousehold("owner", PIN, "flat", "lena")).toEqual({ reason: "no-pin" });
    expect(state.manage().moveToHousehold("owner", PIN, "flat", "paul")).toBeNull();
    expect(placed(state, "paul")).toMatchObject({ household: "flat", admin: true });
    expect(householdOf(state, "flat")).toMatchObject({ adminId: "paul" });
    expect(householdOf(state, "flat")!.adminUpdatedAt).toBeGreaterThan(1);
  });

  test("a household's last grown-up moves out, and the emptied household can be deleted", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2),
      grownUp("lena", "flat", false, 3)]);
    const manage = state.manage();
    // Its admin stays while others remain; the owner changes the admin first.
    expect(manage.moveToHousehold("owner", PIN, "home", "maja")).toEqual({ reason: "not-allowed" });
    expect(manage.moveToHousehold("owner", PIN, "home", "lena")).toBeNull();
    expect(manage.deleteHousehold("owner", PIN, "flat")).toEqual({ reason: "not-allowed" });
    expect(manage.moveToHousehold("owner", PIN, "home", "maja")).toBeNull();
    expect(placed(state, "maja")).toMatchObject({ household: "home", admin: false });
    expect(manage.deleteHousehold("owner", PIN, "flat")).toBeNull();
    expect(state.households().map((one) => one.id)).toEqual(["home"]);
  });

  test("the owner deletes an empty household, never Home", () => {
    const state = storeWith([household("flat", "owner"), grownUp("owner", "flat", true), household("loft", null)]);
    const manage = state.manage();
    expect(manage.deleteHousehold("owner", PIN, "home")).toEqual({ reason: "not-allowed" });
    expect(manage.deleteHousehold("owner", PIN, "loft")).toBeNull();
    expect(householdOf(state, "loft")?.removed).toBe(1);
    expect(manage.deleteHousehold("owner", PIN, "loft")).toEqual({ reason: "not-found" });
    expect(manage.renameHousehold("owner", PIN, "loft", "Again")).toEqual({ reason: "not-found" });
    expect(state.households().map((one) => one.id)).toEqual(["flat", "home"]);
  });

  test("only the owner acts on households", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2),
      kid("mia", "maja", "flat")]);
    const manage = state.manage();
    expect(manage.createHousehold("maja", PIN, "Loft", { name: "Paul", newPin: "5678" })).toEqual({ reason: "not-allowed" });
    expect(manage.renameHousehold("maja", PIN, "flat", "Mine")).toEqual({ reason: "not-allowed" });
    expect(manage.renameHousehold("mia", PIN, "flat", "Mine")).toEqual({ reason: "not-allowed" });
    expect(manage.renameHousehold("owner", "0000", "flat", "Mine")).toEqual({ reason: "wrong-pin" });
    expect(manage.renameHousehold("nobody", PIN, "flat", "Mine")).toEqual({ reason: "not-found" });
    expect(manage.setHouseholdAdmin("owner", PIN, "flat", "nobody", "5678")).toEqual({ reason: "not-found" });
    // The owner is never moved, and never made another household's admin.
    expect(manage.moveToHousehold("owner", PIN, "flat", "owner")).toEqual({ reason: "not-allowed" });
    expect(manage.createHousehold("owner", PIN, "Loft", { id: "owner" })).toEqual({ reason: "not-allowed" });
    expect(manage.setHouseholdAdmin("owner", PIN, "home", "owner")).toEqual({ reason: "not-allowed" });
    expect(state.households().map((one) => one.name)).toEqual(["flat", "Home"]);
  });
});

describe("joining", () => {
  test("joining takes the household admin's PIN or the owner's", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2),
      grownUp("lena", "flat", false, 3), household("loft", null), "INSERT INTO households(id, name, removed) VALUES ('gone', 'Gone', 1)"]);
    const manage = state.manage();
    expect(manage.setPin("maja", PIN, "maja", "5678")).toBeNull();
    expect(manage.setPin("lena", PIN, "lena", "4321")).toBeNull();
    expect(manage.join("flat", "5678")).toBeNull();
    expect(manage.join("flat", PIN)).toBeNull();
    expect(manage.join("flat", "4321")).toEqual({ reason: "wrong-pin" });
    expect(manage.join("loft", PIN)).toBeNull();
    expect(manage.join("home", "5678")).toEqual({ reason: "wrong-pin" });
    expect(manage.join("gone", PIN)).toEqual({ reason: "not-found" });
    expect(manage.join("nowhere", PIN)).toEqual({ reason: "not-found" });
  });

  test("a household nobody with a PIN runs cannot be joined", () => {
    const state = storeWith([pinless("andre", "home"), household("loft", null)]);
    expect(state.manage().join("loft", PIN)).toEqual({ reason: "no-pin" });
  });

  test("wrong join PINs wait per household, not per profile", () => {
    const state = storeWith([grownUp("owner", "home", true), household("flat", "maja"), grownUp("maja", "flat", false, 2)]);
    const manage = state.manage();
    for (let wrong = 0; wrong < 5; wrong++) expect(manage.join("flat", "0000")).toEqual({ reason: "wrong-pin" });
    expect(manage.join("flat", PIN)).toEqual({ reason: "wait", retryAfter: 60 });
    // Another household's door, and the profiles themselves, are not held.
    expect(manage.join("home", PIN)).toBeNull();
    expect(manage.unlock("owner", PIN)).toBeNull();
    expect(manage.unlock("maja", PIN)).toBeNull();
  });
});
