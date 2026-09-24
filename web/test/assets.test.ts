/**
 * Text that belongs to a set: a summary, and subtitles per language.
 *
 * Served as their own routes rather than folded into the catalog: a `<track>`
 * element needs a URL it can fetch, and a catalog carrying every subtitle of
 * every set would be megabytes before it listed anything.
 */

import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";
import { subtitle, subtitleLanguages, summary } from "../src/catalog/assets";
import { emptyIndex as fixture } from "./index-fixture";

const SET = "01SET0000000000000000001";

function put(db: Database, kind: string, lang: string, body: string) {
  db.run("INSERT INTO assets(set_id, kind, lang, body) VALUES (?, ?, ?, ?)", [
    SET,
    kind,
    lang,
    body,
  ]);
}

describe("summaries", () => {
  test("a stored summary comes back", () => {
    const db = fixture();
    put(db, "summary", "", "Worum es geht.");

    expect(summary(db, SET)).toBe("Worum es geht.");
  });

  test("a set without one reports nothing, rather than an empty string", () => {
    expect(summary(fixture(), SET)).toBeNull();
  });
});

describe("subtitles", () => {
  test("come back by language", () => {
    const db = fixture();
    put(db, "subtitle", "deu", "WEBVTT\n\ndeutsch");

    expect(subtitle(db, SET, "deu")).toBe("WEBVTT\n\ndeutsch");
  });

  test("are listed so the page knows what to offer", () => {
    const db = fixture();
    put(db, "subtitle", "deu", "a");
    put(db, "subtitle", "eng", "b");

    expect(subtitleLanguages(db, SET)).toEqual(["deu", "eng"]);
  });

  test("a language that is not there is not invented", () => {
    const db = fixture();
    put(db, "subtitle", "deu", "a");

    expect(subtitle(db, SET, "eng")).toBeNull();
  });

  test("a summary is not served as a subtitle", () => {
    const db = fixture();
    put(db, "summary", "", "prose, not captions");

    expect(subtitleLanguages(db, SET)).toEqual([]);
    expect(subtitle(db, SET, "")).toBeNull();
  });
});

describe("an index without the table", () => {
  /**
   * The player refuses an index older than it understands, but a database
   * mid-migration or hand-made should still degrade to "no assets" rather
   * than taking the catalog down.
   */
  test("reports no assets instead of throwing", () => {
    const empty = new Database(":memory:");

    expect(summary(empty, SET)).toBeNull();
    expect(subtitleLanguages(empty, SET)).toEqual([]);
    expect(subtitle(empty, SET, "deu")).toBeNull();
  });
});
