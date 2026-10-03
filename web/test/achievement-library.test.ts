import { describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { achievementLibrary } from "../src/state/achievement-library";
import { emptyIndex } from "./index-fixture";

const SHOWS = `CREATE TABLE shows(
    source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL,
    lang TEXT NOT NULL DEFAULT '',
    overview TEXT, tagline TEXT, genres TEXT, rating REAL,
    network TEXT, status TEXT, first_air TEXT, last_air TEXT,
    total_seasons INTEGER, total_episodes INTEGER,
    PRIMARY KEY(source, kind, id))`;

/** A set with no parts, which `PLAYABLE_SQL` plays: zero parts done of zero, zero bytes of zero. */
function add(db: Database, setId: string, kind: string, show: string | null = null, tmdb: number | null = null, status = "complete") {
  db.run(
    `INSERT INTO sets(set_id, kind, title, show, tmdb, container, total, part_count, status, created_at, spec_version)
     VALUES (?1, ?2, ?1, ?3, ?4, 'mkv', 0, 0, ?5, 1, 3)`,
    [setId, kind, show, tmdb, status],
  );
}

function described(db: Database, kind: "movie" | "tv", id: number, genres: string) {
  db.run("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', ?1, ?2, ?3)", [kind, id, genres]);
}

const sorted = (facts: ReturnType<typeof achievementLibrary>) => ({
  library: [...facts.library].sort((a, b) => a.setId.localeCompare(b.setId)),
  collections: facts.collections.map((c) => ({ ...c, setIds: [...c.setIds].sort() })).sort((a, b) => a.id.localeCompare(b.id)),
});

describe("the library the achievements count", () => {
  test("a film carries its own genres and an episode its show's", () => {
    const db = emptyIndex();
    db.run(SHOWS);
    add(db, "heat", "movie", null, 949);
    add(db, "bb1", "ep", "Breaking Bad", 1396);
    described(db, "movie", 949, "Crime, Drama");
    described(db, "tv", 1396, "Drama");
    expect(sorted(achievementLibrary(db)).library).toEqual([
      { setId: "bb1", kind: "ep", genres: ["Drama"], collection: "ep:Breaking Bad" },
      { setId: "heat", kind: "movie", genres: ["Crime", "Drama"], collection: null },
    ]);
  });

  test("episodes group by show and lessons by course; documents, documentaries and a set with no show join none", () => {
    const db = emptyIndex();
    add(db, "e1", "ep", "Dark");
    add(db, "e2", "ep", "Dark");
    add(db, "lone", "ep");
    add(db, "l1", "tut", "Rust");
    add(db, "handout", "doc", "Rust");
    add(db, "terra", "docu", "Terra X");
    const facts = sorted(achievementLibrary(db));
    expect(facts.collections).toEqual([{ id: "ep:Dark", setIds: ["e1", "e2"] }, { id: "tut:Rust", setIds: ["l1"] }]);
    expect(facts.library.filter((title) => title.collection === null).map((title) => title.setId)).toEqual(["handout", "lone", "terra"]);
  });

  test("a set the catalog will not play is not in the library", () => {
    const db = emptyIndex();
    add(db, "ready", "movie");
    add(db, "uploading", "movie", null, null, "pending");
    expect(achievementLibrary(db).library.map((title) => title.setId)).toEqual(["ready"]);
  });

  test("an index with no provider table still lists its titles, without genres", () => {
    const db = emptyIndex();
    add(db, "heat", "movie", null, 949);
    expect(achievementLibrary(db).library).toEqual([{ setId: "heat", kind: "movie", genres: [], collection: null }]);
  });
});
