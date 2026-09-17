/**
 * Folding text so a search finds what a person meant.
 *
 * This library is German. Someone hunting "Überblick" types `uberblick` as
 * often as not, and a search that answers "no results" to that is a search
 * nobody uses twice. Case and diacritics are therefore folded on both sides,
 * and `ß` is folded to `ss` because that is how people type it.
 */

import { describe, expect, test } from "bun:test";

import { fold, terms } from "../src/search/normalize";

describe("folding a string", () => {
  test("case goes", () => {
    expect(fold("Überblick")).toBe(fold("überblick"));
    expect(fold("BROKER")).toBe("broker");
  });

  test("German diacritics fold to their base letters", () => {
    expect(fold("Überblick")).toBe("uberblick");
    expect(fold("Qualität")).toBe("qualitat");
    expect(fold("Glaubenssätze")).toBe("glaubenssatze");
    expect(fold("Börse")).toBe("borse");
  });

  test("the sharp s folds the way it is typed", () => {
    // Someone with no `ß` on their keyboard writes `ss`, and both should find
    // the same lesson.
    expect(fold("Straße")).toBe("strasse");
    expect(fold("Strasse")).toBe(fold("Straße"));
  });

  test("other alphabets fold too, since a library is not only German", () => {
    expect(fold("Café")).toBe("cafe");
    expect(fold("naïve")).toBe("naive");
    expect(fold("Señor")).toBe("senor");
  });

  test("punctuation and spacing are flattened, not dropped silently", () => {
    // "Produkte ⁄ Instrumente" uses a fraction slash, and "Zeitebenen – Teil 1"
    // an en dash. Neither is on a keyboard.
    expect(fold("Produkte ⁄ Instrumente")).toBe("produkte instrumente");
    expect(fold("Zeitebenen – Teil 1")).toBe("zeitebenen teil 1");
    expect(fold("  spaced   out  ")).toBe("spaced out");
  });

  test("an empty or absent string folds to nothing", () => {
    expect(fold("")).toBe("");
    expect(fold(null)).toBe("");
    expect(fold(undefined)).toBe("");
  });
});

describe("splitting a query into terms", () => {
  test("words become terms", () => {
    expect(terms("grundlagen borse")).toEqual(["grundlagen", "borse"]);
  });

  test("terms are folded like everything else", () => {
    expect(terms("Grundlagen Börse")).toEqual(["grundlagen", "borse"]);
  });

  test("extra spacing and punctuation do not make empty terms", () => {
    expect(terms("  trading ,  journal ")).toEqual(["trading", "journal"]);
  });

  test("an empty query has no terms, which callers treat as no search", () => {
    expect(terms("")).toEqual([]);
    expect(terms("   ")).toEqual([]);
    expect(terms("!!!")).toEqual([]);
  });
});
