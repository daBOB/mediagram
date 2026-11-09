// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { genitive, runs, wrap, type Run } from "../src/tv/grand-allee/grand-allee-intro-text";

// every character 1 wide, gold ones 2: the gold words are set heavier and run wider
const width = (r: Run) => r.text.length * (r.gold ? 2 : 1);
const text = (rows: Run[][]) => rows.map((row) => row.map((r) => (r.gold ? `[${r.text}]` : r.text)).join(""));

test("gold words are the ones between double stars", () => {
  expect(runs("Nach **56 Runden** gewinnt")).toEqual([
    { text: "Nach ", gold: false },
    { text: "56 Runden", gold: true },
    { text: " gewinnt", gold: false },
  ]);
  expect(runs("**Würfle** auf dem Handy")).toEqual([{ text: "Würfle", gold: true }, { text: " auf dem Handy", gold: false }]);
});

test("a line breaks only at spaces, the trailing space not counted, a keyword keeping its punctuation", () => {
  const line = runs("**Baustelle**? Bauen – der Genehmigungswürfel");
  expect(text(wrap(line, 100, width))).toEqual(["[Baustelle]? Bauen – der Genehmigungswürfel"]);
  // "[Baustelle]?" is 19 wide: it fits exactly, its space does not count
  expect(text(wrap(line, 19, width))).toEqual(["[Baustelle]? ", "Bauen – der ", "Genehmigungswürfel"]);
  // a word wider than the row stands on its own row rather than vanish
  expect(text(wrap(runs("ab **Genehmigungswürfel**"), 6, width))).toEqual(["ab ", "[Genehmigungswürfel]"]);
});

test("a gold phrase with a space may break inside", () => {
  expect(text(wrap(runs("**Geld, Grundstücke** und"), 14, width))).toEqual(["[Geld, ]", "[Grundstücke] ", "und"]);
});

test("whose phone skips: the genitive, with only an apostrophe after an s sound", () => {
  expect(genitive("Lena")).toBe("Lenas");
  expect(genitive("Oma Gerda")).toBe("Oma Gerdas");
  expect(genitive("Klaus")).toBe("Klaus’");
  expect(genitive("Max")).toBe("Max’");
  expect(genitive("Fritz")).toBe("Fritz’");
});
