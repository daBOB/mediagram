/**
 * A rename between two devices: the laptop renames Oma to Omi, and the TV
 * takes it in on its next round. Then what a build from before renames does
 * with the same documents, which drops the two keys on reading.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import { WatchState } from "../src/state/store";
import { parseRecord, type SyncRecord } from "../src/state/sync-record";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

type Device = { state: WatchState; path: string; device: string };

function store(device: string): Device {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-rename-sync-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  return { state: new WatchState(path), path, device };
}

/** Runs what no method here does, through a second connection; each statement's rows. */
function sql(path: string, ...statements: string[]): unknown[][] {
  const db = new Database(path);
  try {
    return statements.map((statement) => db.query(statement).all());
  } finally {
    db.close();
  }
}

/** Every row of every table, to show an import left them all as they were. */
function everything(path: string): unknown[][] {
  const [tables] = sql(path, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") as { name: string }[][];
  return sql(path, ...tables!.map(({ name }) => `SELECT * FROM "${name}"`));
}

/** A device's document as the channel carries it. */
const wire = ({ state, device }: Device): SyncRecord => parseRecord(JSON.stringify(state.exportRecord(device)))!;

/** One round on `on`: its own document and `from`'s, merged and taken back in. */
const round = (on: Device, from: Device): number => on.state.importMerged(mergeStates([wire(on), wire(from)]));

/** A document as a build from before renames reads it: without the two keys. */
const older = (record: SyncRecord): SyncRecord =>
  ({ ...record, profiles: record.profiles.map(({ renamedAt: _at, formerNames: _names, ...rest }) => rest) });

/**
 * The laptop and the TV, both knowing Oma — her position and her list, made
 * on the laptop and taken in by the TV — and then the laptop renames her Omi.
 */
function renamedOnTheLaptop() {
  const laptop = store("laptop");
  const tv = store("tv");
  const oma = laptop.state.createProfile("Oma")!.id;
  laptop.state.setProgress(oma, "01A", 60, 1200);
  laptop.state.addToCollection(oma, laptop.state.createCollection(oma, "Favs")!.id, "01A");
  expect(round(tv, laptop)).toBeGreaterThan(0);
  const tvOma = tv.state.profiles()[0]!.id;
  sql(laptop.path, `UPDATE profiles SET name = 'Omi', renamed_at = 2000, former_names = '["oma"]' WHERE id = '${oma}'`);
  return { laptop, tv, oma, tvOma };
}

const idsAndNames = (state: WatchState) => state.profiles().map(({ id, name }) => ({ id, name }));

describe("a rename between two devices", () => {
  test("two devices agree on a rename, and a second round changes nothing", () => {
    const { laptop, tv, tvOma } = renamedOnTheLaptop();
    expect(round(tv, laptop)).toBeGreaterThan(0);
    expect(idsAndNames(tv.state)).toEqual([{ id: tvOma, name: "Omi" }]);
    const kept = tv.state.snapshot(tvOma);
    expect(kept.progress.map(({ setId, at }) => ({ setId, at }))).toEqual([{ setId: "01A", at: 60 }]);
    expect(kept.collections.map(({ name, items }) => ({ name, items }))).toEqual([{ name: "Favs", items: ["01A"] }]);
    expect(tv.state.exportRecord("tv").profiles[0]).toMatchObject({ name: "Omi", renamedAt: 2000, formerNames: ["oma"] });
    expect(round(tv, laptop)).toBe(0);
    expect(round(laptop, tv)).toBe(0);
    laptop.state.close();
    tv.state.close();
  });
});

describe("what a build from before renames does", () => {
  test("an older build's document under the old name is filed under the new one, and makes no profile", () => {
    const { laptop, tv, oma, tvOma } = renamedOnTheLaptop();
    // The TV never took the rename in, and goes on watching as Oma.
    tv.state.setProgress(tvOma, "01B", 30, 1200);
    expect(laptop.state.importMerged(mergeStates([wire(laptop), older(wire(tv))]))).toBeGreaterThan(0);
    expect(idsAndNames(laptop.state)).toEqual([{ id: oma, name: "Omi" }]);
    expect(laptop.state.snapshot(oma).progress.map((row) => row.setId).sort()).toEqual(["01A", "01B"]);
    expect(laptop.state.exportRecord("laptop").profiles[0]).toMatchObject({ renamedAt: 2000, formerNames: ["oma"] });
    laptop.state.close();
    tv.state.close();
  });

  test("an older build's own merge shows both names", () => {
    const { laptop, tv } = renamedOnTheLaptop();
    const merged = mergeStates([older(wire(tv)), older(wire(laptop))]);
    expect(merged.profiles.map((one) => one.name).sort()).toEqual(["oma", "omi"]);
    laptop.state.close();
    tv.state.close();
  });

  test("an older build that holds the person's list cannot take the new name in, and keeps every row", () => {
    // Why the merge and import ship, and reach every device, before anything
    // can rename: the new name's row would take in a list id the old one holds.
    const { laptop, tv } = renamedOnTheLaptop();
    const before = everything(tv.path);
    expect(() => tv.state.importMerged(mergeStates([older(wire(tv)), older(wire(laptop))])))
      .toThrow(/UNIQUE constraint failed: collections\.id/);
    expect(everything(tv.path)).toEqual(before);
    expect(tv.state.profiles().map((one) => one.name)).toEqual(["Oma"]);
    laptop.state.close();
    tv.state.close();
  });
});
