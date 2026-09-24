/**
 * Folding text so a search finds what a person meant.
 *
 * This library is German. Someone hunting "Überblick" types `uberblick` as
 * often as not, and a search that answers "no results" to that is a search
 * nobody uses twice. Case and diacritics are therefore folded on both sides,
 * and `ß` is folded to `ss` because that is how people type it.
 */

import { describe, expect, test } from "bun:test";

import { fold, spellOut, terms, variants } from "../src/search/normalize";

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

  test("a diacritic that is not a combining mark is dropped like any other", () => {
    // U+00B4, a standalone acute accent rather than one NFD ever produces —
    // dropping only NFD's combining marks would leave it behind as a
    // spurious word break ("geht s").
    expect(fold("Geht´s")).toBe("gehts");
    // U+02BC, the apostrophe-shaped modifier letter Diacritic=Yes also
    // covers, despite reading as a letter rather than a mark.
    expect(fold("ʼn")).toBe("n");
  });

  test("a symbol that only looks like a letter is not one", () => {
    // "Ⓐ" is General_Category=So (Symbol), not Letter — `\p{Letter}` does
    // not count it, so it collapses to nothing (a lone space, trimmed away),
    // the same as any other punctuation.
    expect(fold("Ⓐ")).toBe("");
  });

  test("a combining mark with Diacritic=No still splits the word it sits in", () => {
    // U+0363, used in medieval manuscript abbreviations: General_Category
    // is Mark, so it is not Letter or Number either, and — because it is
    // one of the marks `\p{Diacritic}` does not cover — it survives the
    // first pass and is only removed by the second, as a word-splitting
    // space. A faithful port reproduces this rather than smoothing it over.
    expect(fold("eͣx")).toBe("e x");
  });
});

describe("spelling a string out", () => {
  test("umlauts become the two letters people type instead", () => {
    // The other half of the same problem `fold` solves. A keyboard without
    // umlauts offers two conventions, and Germans use both: drop the dots, or
    // spell them out. "ueberblick" is as common as "uberblick".
    expect(spellOut("Überblick")).toBe("ueberblick");
    expect(spellOut("Qualität")).toBe("qualitaet");
    expect(spellOut("Börse")).toBe("boerse");
  });

  test("the sharp s spells out the same way it folds", () => {
    expect(spellOut("Straße")).toBe("strasse");
  });

  test("it agrees with fold on text that has nothing to spell out", () => {
    // Worth stating: the index skips storing a second copy when the two
    // agree, so this equality is what keeps that optimisation honest.
    expect(spellOut("Broker Vergleich")).toBe(fold("Broker Vergleich"));
    expect(spellOut("Produkte ⁄ Instrumente")).toBe("produkte instrumente");
  });

  test("a diacritic that is not a German umlaut folds rather than spelling out", () => {
    // "Café" has no two-letter convention; nobody types "cafee".
    expect(spellOut("Café")).toBe("cafe");
    expect(spellOut("Señor")).toBe("senor");
  });

  test("an empty or absent string spells out to nothing", () => {
    expect(spellOut("")).toBe("");
    expect(spellOut(null)).toBe("");
    expect(spellOut(undefined)).toBe("");
  });
});

describe("the forms a string can be searched as", () => {
  test("text with an umlaut can be typed two ways", () => {
    expect(variants("Überblick")).toEqual(["uberblick", "ueberblick"]);
  });

  test("text without one has a single form, not a duplicate pair", () => {
    // What keeps the index from storing a second identical copy of every
    // English title and every folder called "Start".
    expect(variants("Broker Vergleich")).toEqual(["broker vergleich"]);
    expect(variants("Straße")).toEqual(["strasse"]);
  });

  test("nothing at all has one empty form", () => {
    expect(variants(null)).toEqual([""]);
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
