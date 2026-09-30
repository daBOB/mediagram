/**
 * Subtitle choices on the sync record: the store around `mergeStates` — what
 * a write stamps, what an export carries, and what an import will overwrite.
 * The merge and parse rules themselves are the shared fixtures' job.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import { WatchState } from "../src/state/store";
import { parseRecord } from "../src/state/sync-record";

const dirs: string[] = [];
const open = () => {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-prefs-sync-"));
  dirs.push(dir);
  return new WatchState(join(dir, "state.db"));
};
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const stored = (state: WatchState, id: string) =>
  state.snapshot(id).preferences.map((row) => `${row.scope}/${row.name}=${row.value}`).sort();

describe("exporting preferences", () => {
  test("carries the synced names and leaves per-device ones at home", () => {
    const state = open();
    const id = state.createProfile("André")!.id;
    state.setPreference(id, "profile", "subtitle", "en");
    state.setPreference(id, "show:x", "cue-size", "125");
    state.setPreference(id, "show:x", "audio", "eng");
    const sent = state.exportRecord("tablet").profiles[0]!.preferences!;
    expect(sent.map((row) => `${row.scope}/${row.name}`).sort()).toEqual(["profile/subtitle", "show:x/cue-size"]);
    state.close();
  });
});

describe("stamping a write", () => {
  test("never goes backwards, even when the stored row is ahead of the clock", () => {
    const state = open();
    const id = state.createProfile("André")!.id;
    const ahead = Date.now() + 60_000;
    state.importMerged({ profiles: [{ name: "andré", displayName: "André", progress: [], watched: [],
      preferences: [{ scope: "profile", name: "subtitle", value: "de", updatedAt: ahead }] }] });
    state.setPreference(id, "profile", "subtitle", "en");
    const row = state.exportRecord("tablet").profiles[0]!.preferences![0]!;
    expect(row.value).toBe("en");
    expect(row.updatedAt).toBe(ahead + 1);
    state.close();
  });
});

describe("importing preferences", () => {
  const merged = (value: string, updatedAt: number) => ({
    profiles: [{ name: "andré", displayName: "André", progress: [], watched: [],
      preferences: [{ scope: "profile", name: "subtitle", value, updatedAt }] }],
  });

  test("takes a newer row, ignores an older one, and counts only real changes", () => {
    const state = open();
    const id = state.createProfile("André")!.id;
    expect(state.importMerged(merged("en", 1000))).toBe(1);
    expect(state.importMerged(merged("en", 1000))).toBe(0);
    expect(state.importMerged(merged("de", 500))).toBe(0);
    expect(stored(state, id)).toEqual(["profile/subtitle=en"]);
    expect(state.importMerged(merged("de", 2000))).toBe(1);
    expect(stored(state, id)).toEqual(["profile/subtitle=de"]);
    state.close();
  });

  test("an equal-time row with another value is the merge's tie-break winner and is applied", () => {
    const state = open();
    const id = state.createProfile("André")!.id;
    state.importMerged(merged("en", 1000));
    expect(state.importMerged(merged("de", 1000))).toBe(1);
    expect(stored(state, id)).toEqual(["profile/subtitle=de"]);
    state.close();
  });

  test("ignores a name that does not travel, however a document claims it", () => {
    const state = open();
    const id = state.createProfile("André")!.id;
    state.importMerged({ profiles: [{ name: "andré", displayName: "André", progress: [], watched: [],
      preferences: [{ scope: "s", name: "audio", value: "eng", updatedAt: 1000 }] }] });
    expect(stored(state, id)).toEqual([]);
    state.close();
  });
});

describe("two devices", () => {
  test("a choice made on one arrives on the other through a real document", () => {
    const tablet = open();
    const tv = open();
    tablet.setPreference(tablet.createProfile("André")!.id, "profile", "subtitle", "en");
    const wire = parseRecord(JSON.stringify(tablet.exportRecord("tablet")))!;
    tv.importMerged(mergeStates([tv.exportRecord("tv"), wire]));
    const id = tv.profiles()[0]!.id;
    expect(stored(tv, id)).toEqual(["profile/subtitle=en"]);
    tablet.close();
    tv.close();
  });
});
