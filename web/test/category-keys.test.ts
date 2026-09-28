/**
 * `categoryKey` against the fixture shared with the Rust core's own test
 * (`crates/mlib-spec/tests/shared_category_keys.rs`), and `categoryNames`/
 * `categoryOf` against the table shapes an index can actually carry: no
 * table at all, `NULL` rows kept back to uncategorised, and a named row.
 */

import { Database } from "bun:sqlite";
import { expect, test } from "bun:test";
import { categoryKey, categoryNames, categoryOf } from "../src/catalog/categories";
import cases from "./fixtures/categories/keys.json";

for (const { name, kind, show, title, key } of cases) {
  test(`categoryKey: ${name}`, () => {
    expect(categoryKey(kind, show, title)).toEqual(key as [string, string] | null);
  });
}

test("categoryNames reads nothing from an index older than the table", () => {
  const db = new Database(":memory:");
  try {
    expect(categoryNames(db).size).toBe(0);
  } finally { db.close(); }
});

test("categoryNames drops NULL rows and keeps named ones", () => {
  const db = new Database(":memory:");
  try {
    db.run("CREATE TABLE categories(department TEXT, item_key TEXT, category TEXT, set_at INTEGER)");
    db.run("INSERT INTO categories VALUES ('tutorials', 'title-rust-course', 'Programming', 100)");
    db.run("INSERT INTO categories VALUES ('documentaries', 'title-terra-x', NULL, 100)");
    const names = categoryNames(db);
    expect(names.get("tutorials/title-rust-course")).toBe("Programming");
    expect(names.has("documentaries/title-terra-x")).toBe(false);
    expect(names.size).toBe(1);
  } finally { db.close(); }
});

test("categoryOf answers null for a film, which has no unit to file under", () => {
  const names = new Map([["tutorials/title-rust-course", "Programming"]]);
  expect(categoryOf(names, "movie", null, "A Film")).toBeNull();
});

test("categoryOf finds a course's filed category by its show name", () => {
  const names = new Map([["tutorials/title-rust-course", "Programming"]]);
  expect(categoryOf(names, "tut", "Rust Course", null)).toBe("Programming");
});
