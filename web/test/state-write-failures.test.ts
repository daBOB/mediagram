import { afterEach, beforeEach, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { WatchState } from "../src/state/store";

let directory: string;
let state: WatchState;
let database: Database;
let profile: string;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), "mediagram-write-refusal-"));
  const path = join(directory, "state.db");
  state = new WatchState(path);
  profile = state.createProfile("Viewer")!.id;
  database = new Database(path);
});

afterEach(() => {
  database.close();
  state.close();
  rmSync(directory, { recursive: true, force: true });
});

test("deleted profiles are an expected refusal, and preferences report that nothing was saved", () => {
  expect(() => state.setProgress("deleted", "title", 10, 100)).not.toThrow();
  expect(() => state.setWatchlisted("deleted", "title", true)).not.toThrow();
  expect(() => state.setWatched("deleted", "title", true)).not.toThrow();
  expect(state.setPreference("deleted", "title", "audio", "de")).toBe(false);
  expect(database.query("SELECT COUNT(*) AS n FROM preferences").get()).toEqual({ n: 0 });
});

test.each([
  ["progress", () => state.setProgress(profile, "title", 10, 100)],
  ["watchlist", () => state.setWatchlisted(profile, "title", true)],
  ["watched", () => state.setWatched(profile, "title", true)],
  ["kids", () => state.setKids("title", true)],
  ["preferences", () => state.setPreference(profile, "title", "audio", "de")],
] as const)("unexpected %s write refusals reach the request boundary", (table, write) => {
  database.exec(`CREATE TRIGGER refuse_write BEFORE INSERT ON ${table}
    BEGIN SELECT RAISE(FAIL, 'fixture storage refused'); END`);
  expect(write).toThrow("fixture storage refused");
  expect(database.query(`SELECT COUNT(*) AS n FROM ${table}`).get()).toEqual({ n: 0 });
});
