/** Import the equal-time winner selected by mergeStates without rewriting no-ops. */

import { afterEach, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import { WatchState } from "../src/state/store";
import type { CollectionRow, ListRow, ProgressRow, SyncRecord } from "../src/state/sync-record";

const machines: { state: WatchState; dir: string }[] = [];
afterEach(() => {
  for (const { state, dir } of machines.splice(0)) {
    state.close();
    rmSync(dir, { recursive: true, force: true });
  }
});

type Rows = { progress?: ProgressRow[]; kids?: ListRow[]; watchlist?: ListRow[]; collections?: CollectionRow[] };
function record(device: string, rows: Rows): SyncRecord {
  return {
    format: 1, device, writtenAt: 100, kids: rows.kids ?? [],
    profiles: [{ name: "André", progress: rows.progress ?? [], watched: [],
      watchlist: rows.watchlist ?? [], collections: rows.collections ?? [] }],
  };
}
function machine(seed: SyncRecord) {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-sync-tie-"));
  const state = new WatchState(join(dir, "state.db"));
  machines.push({ state, dir });
  state.importMerged(mergeStates([seed]));
  return state;
}

const progress = { setId: "01A", at: 10, duration: 100, updatedAt: 100 };
const live = { setId: "01A", updatedAt: 100 };
const removed = { ...live, removed: true as const };
const collection = { id: "list", name: "Sunday", items: ["01A", "01B"], updatedAt: 100 };
const cases: [string, Rows, Rows][] = [
  ["progress position", { progress: [progress] }, { progress: [{ ...progress, at: 42 }] }],
  ["progress duration", { progress: [progress] }, { progress: [{ ...progress, duration: null }] }],
  ["Kids removal", { kids: [live] }, { kids: [removed] }],
  ["Kids revival", { kids: [removed] }, { kids: [live] }],
  ["watchlist removal", { watchlist: [live] }, { watchlist: [removed] }],
  ["watchlist revival", { watchlist: [removed] }, { watchlist: [live] }],
  ["collection name", { collections: [collection] }, { collections: [{ ...collection, name: "Friday" }] }],
  ["collection order", { collections: [collection] }, { collections: [{ ...collection, items: ["01B", "01A"] }] }],
  ["collection removal", { collections: [collection] }, { collections: [{ ...collection, removed: true }] }],
  ["collection revival", { collections: [{ ...collection, removed: true }] }, { collections: [collection] }],
];

test.each(cases)("equal-time %s converges to the selected device's row", (_name, before, after) => {
  const lower = machine(record("a", before));
  const higher = machine(record("z", after));
  const selected = mergeStates([higher.exportRecord("z"), lower.exportRecord("a")]);

  expect(lower.importMerged(selected)).toBe(1);
  expect(higher.importMerged(selected)).toBe(0);
  expect(mergeStates([lower.exportRecord("a")])).toEqual(selected);
  expect(lower.importMerged(selected)).toBe(0);
});

test.each(cases)("a strictly newer local %s survives importing an older selection", (_name, before, after) => {
  const newer = structuredClone(record("a", before));
  const profile = newer.profiles[0]!;
  for (const row of [...profile.progress, ...profile.watchlist!, ...profile.collections!, ...newer.kids!]) {
    row.updatedAt = 200;
  }
  const local = machine(newer);
  const original = mergeStates([local.exportRecord("a")]);

  expect(local.importMerged(mergeStates([record("z", after)]))).toBe(0);
  expect(mergeStates([local.exportRecord("a")])).toEqual(original);
});
