/**
 * `subtitle-choice.js` against the fixture every surface is proved against.
 *
 * The rule itself is documented in `subtitle-choice.js`; this file only turns
 * each fixture case into an assertion.
 */

import { describe, expect, test } from "bun:test";
import {
  audioLanguage, chooseSubtitles, sameLanguage, toggleOn, trackKey, visibility,
} from "../public/lib/playback/subtitle-choice.js";
import cases from "./fixtures/subtitles/choice-cases.json";

describe("the shared playback rule", () => {
  for (const c of cases) {
    test(c.name, () => {
      const chosen = chooseSubtitles({
        tracks: c.tracks, remembered: c.remembered, preferred: c.preferred,
        audioTag: c.audioTag, alang: c.alang,
      });
      expect(chosen).toEqual({ audio: c.audio, regular: c.regular, forced: c.forced });
      expect(toggleOn(c.tracks, { last: c.last, preferred: c.preferred, audio: chosen.audio }))
        .toBe(c.toggleOn);
      expect(visibility(c.tracks)).toEqual({
        pickerRows: c.pickerRows, ccVisible: c.ccVisible, styleVisible: c.styleVisible,
      });
    });
  }
});

describe("trackKey", () => {
  test("a plain track keys on its language alone", () => {
    expect(trackKey({ lang: "de", forced: false, sdh: false, label: "German" })).toBe("de");
  });

  test("an SDH track keys with a suffix, so it never collides with the plain one", () => {
    expect(trackKey({ lang: "en", forced: false, sdh: true, label: "English (SDH)" })).toBe("en:sdh");
  });
});

describe("sameLanguage", () => {
  test("two equal, non-null tags", () => {
    expect(sameLanguage("de", "de")).toBe(true);
  });

  test("neither null nor a mismatch counts", () => {
    expect(sameLanguage(null, "de")).toBe(false);
    expect(sameLanguage("de", null)).toBe(false);
    expect(sameLanguage("de", "en")).toBe(false);
  });
});

describe("audioLanguage", () => {
  test("a bibliographic tag normalises to the two-letter code", () => {
    expect(audioLanguage("ger", [])).toBe("de");
    expect(audioLanguage("deu", [])).toBe("de");
    expect(audioLanguage("eng", [])).toBe("en");
  });

  test("an already-short tag passes through, lowercased", () => {
    expect(audioLanguage("DE", [])).toBe("de");
  });

  test("no tag falls back to the set's first alang entry", () => {
    expect(audioLanguage(null, ["fr", "de"])).toBe("fr");
  });

  test("neither a tag nor an alang entry is unknown", () => {
    expect(audioLanguage(null, [])).toBeNull();
  });
});
