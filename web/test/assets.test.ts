/**
 * A set's summary, the one thing `catalog/assets.ts` still serves directly.
 *
 * Subtitles moved to `subtitle-tracks.ts`/`subtitle-bundles.ts`; their tests
 * live in `subtitle-tracks.test.ts` and `subtitle-bundles.test.ts`.
 */

import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";
import { summary } from "../src/catalog/assets";
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

  test("a subtitle track is not served as a summary", () => {
    const db = fixture();
    put(db, "subtitle", "deu", "WEBVTT\n\ndeutsch");

    expect(summary(db, SET)).toBeNull();
  });
});

describe("an index without the table", () => {
  /**
   * The player refuses an index older than it understands, but a database
   * mid-migration or hand-made should still degrade to "no assets" rather
   * than taking the catalog down.
   */
  test("reports no summary instead of throwing", () => {
    const empty = new Database(":memory:");

    expect(summary(empty, SET)).toBeNull();
  });
});
