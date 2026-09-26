import { describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { GROUPS } from "../src/state/schema";
import { MAX_CACHE_BUDGET_BYTES, MIN_CACHE_BUDGET_BYTES, Settings } from "../src/state/settings";

function freshDb(): Database {
  const db = new Database(":memory:");
  for (const group of GROUPS) for (const statement of group) db.exec(statement);
  return db;
}

describe("Settings", () => {
  test("without a database, reads answer null and writes do nothing", () => {
    const settings = new Settings(null);
    expect(settings.cacheMaxBytes()).toBeNull();
    expect(() => settings.setCacheMaxBytes(1024 ** 3)).not.toThrow();
  });

  test("a value round-trips", () => {
    const db = freshDb();
    const settings = new Settings(db);
    expect(settings.cacheMaxBytes()).toBeNull();
    settings.setCacheMaxBytes(2 * 1024 ** 3);
    expect(settings.cacheMaxBytes()).toBe(2 * 1024 ** 3);
    db.close();
  });

  test("setting again replaces the value rather than adding a row", () => {
    const db = freshDb();
    const settings = new Settings(db);
    settings.setCacheMaxBytes(MIN_CACHE_BUDGET_BYTES);
    settings.setCacheMaxBytes(MIN_CACHE_BUDGET_BYTES * 2);
    expect(settings.cacheMaxBytes()).toBe(MIN_CACHE_BUDGET_BYTES * 2);
    expect(db.query("SELECT count(*) AS n FROM settings").get()).toEqual({ n: 1 });
    db.close();
  });

  test("a hostile row is treated as absent, not handed back", () => {
    const db = freshDb();
    db.query("INSERT INTO settings(name, value, updated_at) VALUES ('cache_max_bytes', 'not-a-number', 0)").run();
    expect(new Settings(db).cacheMaxBytes()).toBeNull();

    db.query("UPDATE settings SET value = ?1 WHERE name = 'cache_max_bytes'").run(String(MIN_CACHE_BUDGET_BYTES - 1));
    expect(new Settings(db).cacheMaxBytes()).toBeNull();

    db.query("UPDATE settings SET value = ?1 WHERE name = 'cache_max_bytes'").run(String(MAX_CACHE_BUDGET_BYTES + 1));
    expect(new Settings(db).cacheMaxBytes()).toBeNull();
    db.close();
  });
});
