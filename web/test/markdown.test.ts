/**
 * The markdown a lesson's notes are written in.
 *
 * The cases are taken from the real summaries in this library: ATX headings,
 * bullet lists whose items lead with a bold run-in, and ordered lists nested
 * four spaces under them.
 */

import { describe, expect, test } from "bun:test";
import { parseMarkdown, parseSpans } from "../public/lib/playback/markdown.js";

/** The text of a span tree, so a test can assert on words not structure. */
const textOf = (spans: any[]): string =>
  spans.map((s) => (s.kind === "text" || s.kind === "code" ? s.text : textOf(s.spans))).join("");

describe("blocks", () => {
  test("a heading keeps its level and its words", () => {
    const [block] = parseMarkdown("### 1. Die Psychologie des Geldes") as any[];
    expect(block.kind).toBe("heading");
    expect(block.level).toBe(3);
    expect(textOf(block.spans)).toBe("1. Die Psychologie des Geldes");
  });

  test("lines run together into one paragraph, blank lines separate them", () => {
    const blocks = parseMarkdown("one\ntwo\n\nthree") as any[];
    expect(blocks).toHaveLength(2);
    expect(textOf(blocks[0].spans)).toBe("one two");
    expect(textOf(blocks[1].spans)).toBe("three");
  });

  test("a bullet list, as these notes write one", () => {
    const [list] = parseMarkdown(
      "*   **Bargeld vs. Karte:** Studien zeigen\n*   **Disziplin:** Große Scheine",
    ) as any[];

    expect(list.kind).toBe("list");
    expect(list.ordered).toBe(false);
    expect(list.items).toHaveLength(2);
    expect(textOf(list.items[0].spans)).toBe("Bargeld vs. Karte: Studien zeigen");
  });

  test("a numbered list is ordered", () => {
    const [list] = parseMarkdown("1.  Eröffnungskurs\n2.  Schlusskurs") as any[];
    expect(list.ordered).toBe(true);
    expect(list.items).toHaveLength(2);
  });

  test("a list nested under an item belongs to that item", () => {
    const [list] = parseMarkdown(
      [
        "*   **Candlestick-Charts:** vier Informationen",
        "    1.  **Eröffnungskurs** (Open)",
        "    2.  **Schlusskurs** (Close)",
        "*   **Indikatoren:** Mathematische Hilfsmittel",
      ].join("\n"),
    ) as any[];

    expect(list.items).toHaveLength(2);
    const nested = list.items[0].blocks;
    expect(nested).toHaveLength(1);
    expect(nested[0].kind).toBe("list");
    expect(nested[0].ordered).toBe(true);
    expect(nested[0].items).toHaveLength(2);
    // The sibling is not swallowed by the item above it.
    expect(list.items[1].blocks).toHaveLength(0);
  });

  test("a bullet list nested under a numbered one, and back out again", () => {
    const [list] = parseMarkdown(
      ["1.  Erstens", "    *   Tiefer", "    *   Auch tiefer", "2.  Zweitens"].join("\n"),
    ) as any[];

    expect(list.ordered).toBe(true);
    expect(list.items).toHaveLength(2);
    expect(list.items[0].blocks[0].ordered).toBe(false);
    expect(list.items[0].blocks[0].items).toHaveLength(2);
  });

  test("a switch of marker starts a new list rather than continuing one", () => {
    const blocks = parseMarkdown("*   bullet\n1.  numbered") as any[];
    expect(blocks).toHaveLength(2);
    expect(blocks[0].ordered).toBe(false);
    expect(blocks[1].ordered).toBe(true);
  });

  test("a heading directly after a paragraph is still a heading", () => {
    const blocks = parseMarkdown("Ein Satz\n### Überschrift") as any[];
    expect(blocks.map((b) => b.kind)).toEqual(["paragraph", "heading"]);
  });

  test("fenced code keeps its text exactly, markers and all", () => {
    const [block] = parseMarkdown("```\n*not* a list\n```") as any[];
    expect(block.kind).toBe("code");
    expect(block.text).toBe("*not* a list");
  });

  test("quotes and rules", () => {
    expect((parseMarkdown("> zitiert") as any[])[0].kind).toBe("quote");
    expect((parseMarkdown("---") as any[])[0].kind).toBe("rule");
  });

  test("nothing at all is no blocks, not a crash", () => {
    expect(parseMarkdown("")).toEqual([]);
    expect(parseMarkdown("   \n  \n")).toEqual([]);
    expect(parseMarkdown(null as never)).toEqual([]);
  });
});

describe("inline", () => {
  test("bold, italic and code", () => {
    expect((parseSpans("**fett**") as any[])[0].kind).toBe("strong");
    expect((parseSpans("*kursiv*") as any[])[0].kind).toBe("em");
    expect((parseSpans("`code`") as any[])[0]).toEqual({ kind: "code", text: "code" });
  });

  test("bold wins over italic, or ** would open an emphasis that never shuts", () => {
    const spans = parseSpans("**fett** und *kursiv*") as any[];
    expect(spans.map((s) => s.kind)).toEqual(["strong", "text", "em"]);
  });

  test("a marker with no partner is just a character", () => {
    expect(textOf(parseSpans("2 * 3 = 6") as any[])).toBe("2 * 3 = 6");
    expect(textOf(parseSpans("**unclosed") as any[])).toBe("**unclosed");
  });

  test("a link keeps its href", () => {
    const [link] = parseSpans("[docs](https://example.com/a)") as any[];
    expect(link).toMatchObject({ kind: "link", href: "https://example.com/a" });
    expect(textOf(link.spans)).toBe("docs");
  });

  /**
   * The reason the parser returns a tree instead of a string of HTML. A
   * scheme this does not vouch for keeps its words and loses its link, so
   * nothing downstream has to remember to check.
   */
  test("a scheme that could run something is refused, and the words survive", () => {
    for (const bad of ["javascript:alert(1)", "data:text/html,<script>", "vbscript:x"]) {
      const [link] = parseSpans(`[klick](${bad})`) as any[];
      expect(link.href).toBeNull();
      expect(textOf(link.spans)).toBe("klick");
    }
  });

  test("relative and in-page links are allowed", () => {
    expect((parseSpans("[a](/local)") as any[])[0].href).toBe("/local");
    expect((parseSpans("[a](#top)") as any[])[0].href).toBe("#top");
    expect((parseSpans("[a](mailto:x@y.z)") as any[])[0].href).toBe("mailto:x@y.z");
  });

  test("code spans are literal, so a marker inside one is not a marker", () => {
    const spans = parseSpans("`**nicht fett**`") as any[];
    expect(spans).toHaveLength(1);
    expect(spans[0]).toEqual({ kind: "code", text: "**nicht fett**" });
  });
});
