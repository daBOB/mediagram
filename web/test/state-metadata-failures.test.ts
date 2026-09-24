import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { Database, type SQLQueryBindings } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { WatchState } from "../src/state/store";
import { GROUPS } from "../src/state/schema";

let directory: string;
beforeEach(() => { directory = mkdtempSync(join(tmpdir(), "state-metadata-")); });
afterEach(() => { rmSync(directory, { recursive: true, force: true }); });

/** Inject only a transient SQLite read failure; all successful queries use the real database. */
function failNextRead(sql: string, failure: Error) {
  const query = Database.prototype.query;
  let pending = true;
  return spyOn(Database.prototype, "query").mockImplementation(function <Result, Params extends SQLQueryBindings | SQLQueryBindings[]>(this: Database, text: string) {
    if (text === sql && pending) {
      pending = false;
      throw failure;
    }
    return query.bind(this)<Result, Params>(text);
  });
}

test("a failed device identity read cannot replace the stored identity", () => {
  const state = new WatchState(join(directory, "state.db"));
  const identity = state.deviceId();
  const failure = Object.assign(new Error("fixture read failed"), { code: "SQLITE_IOERR" });
  const read = failNextRead("SELECT value FROM state_meta WHERE key = ?1", failure);
  try {
    expect(() => state.deviceId()).toThrow(failure);
    expect(state.deviceId()).toBe(identity);
  } finally { read.mockRestore(); state.close(); }
});

test.each(["SQLITE_IOERR", "SQLITE_ERROR"])("a %s version read failure cannot replay migrations", (code) => {
  const path = join(directory, "state.db");
  const original = new WatchState(path);
  const identity = original.deviceId();
  const profile = original.createProfile("Viewer")!;
  original.setProgress(profile.id, "SET", 12, 100);
  const snapshot = original.snapshot(profile.id);
  original.close();
  const failure = Object.assign(new Error("fixture metadata read failed"), { code });
  const read = failNextRead("SELECT value FROM state_meta WHERE key = 'schema_version'", failure);
  const warnings = spyOn(console, "warn").mockImplementation(() => {});
  let unavailable: WatchState | undefined;
  try {
    unavailable = new WatchState(path);
    expect(unavailable.remembers).toBe(false);
    expect(warnings.mock.calls).toEqual([["state: not remembering anything (fixture metadata read failed)"]]);
    const db = new Database(path);
    try {
      expect(db.query("SELECT value FROM state_meta WHERE key = 'schema_version'").get())
        .toEqual({ value: String(GROUPS.length) });
    } finally { db.close(); }
    const recovered = new WatchState(path);
    try {
      expect(recovered.remembers).toBe(true);
      expect(recovered.deviceId()).toBe(identity);
      expect(recovered.profiles()).toEqual([profile]);
      expect(recovered.snapshot(profile.id)).toEqual(snapshot);
    } finally { recovered.close(); }
  } finally { unavailable?.close(); read.mockRestore(); warnings.mockRestore(); }
});
