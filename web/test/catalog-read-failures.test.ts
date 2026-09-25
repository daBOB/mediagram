import { Database } from "bun:sqlite";
import { expect, test } from "bun:test";
import { summary, subtitle, subtitleLanguages } from "../src/catalog/assets";
import { providerFactsByShow, showMeta } from "../src/catalog/shows";

const reads = {
  summary: (db: Database) => summary(db, "title"),
  subtitle: (db: Database) => subtitle(db, "title", "deu"),
  languages: (db: Database) => subtitleLanguages(db, "title"),
  description: (db: Database) => showMeta(db, "tmdb-movie-1"),
  facts: providerFactsByShow,
};

for (const [name, read] of Object.entries(reads)) {
  test(`${name} reports an unavailable database instead of missing content`, () => {
    const db = new Database(":memory:");
    db.close();
    expect(() => read(db)).toThrow();
  });
}

test("provider facts tolerate only the legacy table and certification omissions", () => {
  const db = new Database(":memory:");
  try {
    expect(providerFactsByShow(db).size).toBe(0);
    db.run("CREATE TABLE shows(source TEXT, kind TEXT, id INTEGER, genres TEXT)");
    db.run("INSERT INTO shows VALUES ('tmdb', 'movie', 1, 'Drama, Comedy')");
    expect(providerFactsByShow(db).get("tmdb-movie-1")).toEqual({ genres: ["Drama", "Comedy"], fsk: null });
    db.run("ALTER TABLE shows DROP COLUMN genres");
    expect(() => providerFactsByShow(db)).toThrow("no such column: genres");
  } finally { db.close(); }
});

test("an incomplete assets table reports its broken schema", () => {
  const db = new Database(":memory:");
  try {
    db.run("CREATE TABLE assets(set_id TEXT)");
    expect(() => summary(db, "title")).toThrow("no such column");
  } finally { db.close(); }
});
