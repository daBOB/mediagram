/** Reading a set's subtitle tracks: v13 rows, or the legacy inline fallback. */

import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";
import { languageLabel } from "../public/lib/language-label";
import { bundleRef, legacyBody, subtitleTracksBySet } from "../src/catalog/subtitle-tracks";
import { emptyIndex } from "./index-fixture";

const SET = "01SET0000000000000000001";
const OTHER = "01SET0000000000000000002";

function putTrack(db: Database, setId: string, track: number, lang: string, forced = 0, sdh = 0) {
  db.run("INSERT INTO subtitle_tracks(set_id, track, lang, forced, sdh, label) VALUES (?, ?, ?, ?, ?, ?)", [
    setId, track, lang, forced, sdh, `${lang}${forced ? " forced" : ""}`,
  ]);
}
function putBundle(db: Database, setId: string, sha = "a".repeat(64)) {
  db.run("INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at) VALUES (?, -1001, 5, 1000, ?, 1700000000)", [setId, sha]);
}
function putInline(db: Database, setId: string, lang: string, body: string) {
  db.run("INSERT INTO assets(set_id, kind, lang, body) VALUES (?, 'subtitle', ?, ?)", [setId, lang, body]);
}

describe("subtitleTracksBySet", () => {
  test("v13 rows, in track order", () => {
    const db = emptyIndex();
    putTrack(db, SET, 1, "en", 0, 1);
    putTrack(db, SET, 0, "de", 1, 0);

    expect(subtitleTracksBySet(db).get(SET)).toEqual([
      { track: 0, lang: "de", forced: true, sdh: false, label: "de forced" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "en" },
    ]);
  });

  test("falls back to inline assets, numbered by ORDER BY lang, for a set with no v13 row", () => {
    const db = emptyIndex();
    putInline(db, SET, "en", "WEBVTT\n\nhi");
    putInline(db, SET, "de", "WEBVTT\n\nhallo");

    expect(subtitleTracksBySet(db).get(SET)).toEqual([
      { track: 0, lang: "de", forced: false, sdh: false, label: languageLabel("de", "de") },
      { track: 1, lang: "en", forced: false, sdh: false, label: languageLabel("en", "en") },
    ]);
  });

  test("a v13 set is never mixed with its own stale inline rows", () => {
    const db = emptyIndex();
    putTrack(db, SET, 0, "de");
    putInline(db, SET, "fr", "stale");

    expect(subtitleTracksBySet(db).get(SET)).toEqual([{ track: 0, lang: "de", forced: false, sdh: false, label: "de" }]);
  });

  test("a v12 index with neither table reports nothing rather than throwing", () => {
    const db = new Database(":memory:");
    expect(subtitleTracksBySet(db)).toEqual(new Map());
  });

  test("keeps two sets apart", () => {
    const db = emptyIndex();
    putTrack(db, SET, 0, "de");
    putInline(db, OTHER, "en", "hi");

    const bySet = subtitleTracksBySet(db);
    expect(bySet.get(SET)).toHaveLength(1);
    expect(bySet.get(OTHER)).toHaveLength(1);
  });
});

describe("bundleRef", () => {
  test("a set with a bundle", () => {
    const db = emptyIndex();
    putBundle(db, SET, "b".repeat(64));

    expect(bundleRef(db, SET)).toEqual({ messageId: 5, bytes: 1000, sha256: "b".repeat(64) });
  });

  test("a set with none", () => {
    expect(bundleRef(emptyIndex(), SET)).toBeNull();
  });

  test("an index with no subtitle_files table reports none rather than throwing", () => {
    expect(bundleRef(new Database(":memory:"), SET)).toBeNull();
  });
});

describe("legacyBody", () => {
  test("the n-th track by language order", () => {
    const db = emptyIndex();
    putInline(db, SET, "en", "english");
    putInline(db, SET, "de", "deutsch");

    expect(legacyBody(db, SET, 0)).toBe("deutsch");
    expect(legacyBody(db, SET, 1)).toBe("english");
  });

  test("a track number past what the set has", () => {
    const db = emptyIndex();
    putInline(db, SET, "de", "deutsch");

    expect(legacyBody(db, SET, 1)).toBeNull();
  });

  test("no assets table reports nothing rather than throwing", () => {
    expect(legacyBody(new Database(":memory:"), SET, 0)).toBeNull();
  });
});
