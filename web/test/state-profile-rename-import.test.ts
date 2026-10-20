/**
 * A renamed profile on this device: what it writes about its renames, and how
 * it takes a merged rename in — the same row renamed, or two rows of one
 * person folded into one.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import type { MergedProfile, MergedState } from "../src/state/merged";
import { WatchState } from "../src/state/store";
import { normalName, parseRecord, type SyncRecord } from "../src/state/sync-record";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-rename-import-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  return { state: new WatchState(path), path };
}

/** Runs what no method here does, through a second connection; the last statement's first row. */
function sql<T>(path: string, ...statements: string[]): T {
  const db = new Database(path);
  try {
    return statements.map((statement) => db.query(statement).get()).at(-1) as T;
  } finally {
    db.close();
  }
}

const viewer = (name: string, extra: Partial<MergedProfile> = {}): MergedProfile =>
  ({ name: normalName(name)!, displayName: name, progress: [], watched: [], ...extra });

const merged = (...profiles: MergedProfile[]): MergedState => ({ profiles });

const wire = (state: WatchState, device: string): SyncRecord =>
  parseRecord(JSON.stringify(state.exportRecord(device)))!;

/** Another device's document: Celine renamed to Celi there. */
const renamedThere: SyncRecord = {
  format: 1, device: "tv", writtenAt: 0,
  profiles: [{ name: "Celi", renamedAt: 5000, formerNames: ["celine"], progress: [], watched: [] }],
};

/** "Celine", the older row, and "Celi", a second row an older build made for the same person; then `then`. */
function twoRows(...then: string[]) {
  const { state, path } = store();
  const older = state.createProfile("Celine")!.id;
  const newer = state.createProfile("Celi")!.id;
  sql(path, `UPDATE profiles SET created_at = 1 WHERE id = '${older}'`,
    `UPDATE profiles SET created_at = 2 WHERE id = '${newer}'`, ...then);
  return { state, path, older, newer };
}

describe("what a device says about renames", () => {
  test("a store nobody renamed exports neither key", () => {
    const { state } = store();
    state.createProfile("André");
    state.createProfile("Mia", true);
    const text = JSON.stringify(state.exportRecord("laptop"));
    expect(text).not.toContain("renamedAt");
    expect(text).not.toContain("formerNames");
    state.close();
  });
});

describe("taking a rename in", () => {
  test("a rename taken in renames the row in place: the same id, its history, the new stamp and former names", () => {
    const { state } = store();
    const celine = state.createProfile("Celine")!.id;
    state.setProgress(celine, "01A", 60, 1200);
    state.setWatchlisted(celine, "01B", true);
    expect(state.importMerged(merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] })))).toBe(1);
    expect(state.profiles().map(({ id, name }) => ({ id, name }))).toEqual([{ id: celine, name: "Celi" }]);
    expect(state.snapshot(celine).progress.map((row) => row.setId)).toEqual(["01A"]);
    expect(state.snapshot(celine).watchlist).toEqual(["01B"]);
    expect(state.exportRecord("laptop").profiles[0]).toMatchObject(
      { name: "Celi", localId: celine, renamedAt: 5000, formerNames: ["celine"] });
    state.close();
  });

  test("a second import of the same rename changes nothing", () => {
    const { state } = store();
    state.createProfile("Celine");
    const rename = merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] }));
    expect(state.importMerged(rename)).toBe(1);
    expect(state.importMerged(rename)).toBe(0);
    // Nor does a round through the wire with the document that carried it.
    expect(state.importMerged(mergeStates([wire(state, "laptop"), renamedThere]))).toBe(0);
    state.close();
  });

  test("a case-only rename takes the new spelling", () => {
    const { state } = store();
    const celi = state.createProfile("celi")!.id;
    expect(state.importMerged(merged(viewer("Celi", { renamedAt: 5000 })))).toBe(1);
    expect(state.profiles().map(({ id, name }) => ({ id, name }))).toEqual([{ id: celi, name: "Celi" }]);
    state.close();
  });

  test("without a rename, this device keeps its own spelling of a name", () => {
    const { state } = store();
    state.createProfile("andré");
    expect(state.importMerged(merged(viewer("André")))).toBe(0);
    expect(state.profiles().map((one) => one.name)).toEqual(["andré"]);
    state.close();
  });

  test("a viewer this device never met is made with the merged stamp and former names", () => {
    const { state } = store();
    expect(state.importMerged(merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] })))).toBe(1);
    expect(state.profiles().map((one) => one.name)).toEqual(["Celi"]);
    expect(state.exportRecord("laptop").profiles[0]).toMatchObject({ name: "Celi", renamedAt: 5000, formerNames: ["celine"] });
    state.close();
  });
});

describe("folding two rows of one person", () => {
  test("two rows of one person fold into the older: its id, and the newest of each row", () => {
    const { state, path, older, newer } = twoRows();
    sql(path,
      `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at) VALUES ('${older}', '01A', 60, 1200, 1000)`,
      `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at) VALUES ('${older}', '01B', 30, 1200, 1000)`,
      `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at) VALUES ('${newer}', '01A', 300, 1200, 2000)`,
      `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at) VALUES ('${newer}', '01C', 90, 1200, 2000)`);
    expect(state.importMerged(mergeStates([wire(state, "laptop"), renamedThere]))).toBeGreaterThan(0);
    expect(state.profiles().map(({ id, name }) => ({ id, name }))).toEqual([{ id: older, name: "Celi" }]);
    expect(state.snapshot(older).progress.map(({ setId, at }) => ({ setId, at })).sort((a, b) => a.setId.localeCompare(b.setId)))
      .toEqual([{ setId: "01A", at: 300 }, { setId: "01B", at: 30 }, { setId: "01C", at: 90 }]);
    expect(state.importMerged(mergeStates([wire(state, "laptop"), renamedThere]))).toBe(0);
    state.close();
  });

  test("the older row is kept though it holds the merged name, and of two made at once the smaller id", () => {
    const flipped = twoRows("UPDATE profiles SET created_at = 0 WHERE name = 'Celi'");
    const tied = twoRows("UPDATE profiles SET id = 'b', created_at = 1 WHERE name = 'Celi'", "UPDATE profiles SET id = 'a' WHERE name = 'Celine'");
    for (const [state, kept] of [[flipped.state, flipped.newer], [tied.state, "a"]] as const) {
      state.importMerged(merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] })));
      expect(state.profiles().map(({ id, name }) => ({ id, name }))).toEqual([{ id: kept, name: "Celi" }]);
      state.close();
    }
  });

  test("a fold moves the folded row's lists, so their ids never clash", () => {
    const { state, older, newer } = twoRows();
    const list = state.createCollection(newer, "Favs")!;
    state.addToCollection(newer, list.id, "01A");
    state.setWatchlisted(newer, "01B", true);
    // A merge that says nothing of the list: what the fold moved is all there is.
    expect(state.importMerged(merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] })))).toBe(2);
    const kept = state.snapshot(older);
    expect(kept.collections.map(({ id, name, items }) => ({ id, name, items }))).toEqual([{ id: list.id, name: "Favs", items: ["01A"] }]);
    expect(kept.watchlist).toEqual(["01B"]);
    // The same list arriving under the kept row is the row it already has.
    expect(state.importMerged(mergeStates([wire(state, "laptop"), renamedThere]))).toBe(0);
    state.close();
  });

  test("a fold points a kid's parent and a household's admin at the kept row", () => {
    const { state, path, older, newer } = twoRows();
    const mia = state.createProfile("Mia", true)!.id;
    sql(path, `UPDATE profiles SET parent_id = '${newer}' WHERE id = '${mia}'`,
      `INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Flat', 1000, '${newer}', 1000)`);
    state.importMerged(merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] })));
    expect(state.profiles().find((one) => one.id === mia)?.parentId).toBe(older);
    expect(sql<{ adminId: string }>(path, "SELECT admin_id AS adminId FROM households WHERE id = 'b'").adminId).toBe(older);
    state.close();
  });

  test("a fold leaves the roles to the merge, not to the folded row", () => {
    const { state, path, older, newer } = twoRows();
    sql(path, `UPDATE profiles SET hidden_sections = '["anime"]', hidden_sections_updated_at = 1000 WHERE id = '${newer}'`);
    // A merge that says nothing of them: nothing is copied from the folded row.
    state.importMerged(merged(viewer("Celi", { renamedAt: 5000, formerNames: ["celine"] })));
    expect(state.profiles().find((one) => one.id === older)?.hiddenSections).toEqual([]);

    const again = twoRows();
    sql(again.path, `UPDATE profiles SET hidden_sections = '["anime"]', hidden_sections_updated_at = 1000 WHERE id = '${again.newer}'`);
    // This device's own document was part of the merge, so the merge carries them.
    again.state.importMerged(mergeStates([wire(again.state, "laptop"), renamedThere]));
    expect(again.state.profiles().map(({ id, hiddenSections }) => ({ id, hiddenSections })))
      .toEqual([{ id: again.older, hiddenSections: ["anime"] }]);
    expect(sql<{ at: number }>(again.path, "SELECT hidden_sections_updated_at AS at FROM profiles").at).toBe(1000);
    state.close();
    again.state.close();
  });
});
