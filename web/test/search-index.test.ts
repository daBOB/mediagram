/**
 * Finding a title among a hundred and seventy.
 *
 * Ranking is the whole feature. A library of lessons called "Interpretation"
 * and "Mobile App" answers almost any query with a dozen hits, so the order
 * they arrive in is what decides whether the search was any use. A title
 * match beats a match on the folder it sits in, which beats a match buried in
 * a summary.
 */

import { describe, expect, test } from "bun:test";

import { SearchIndex, type Searchable } from "../src/search/index";

const set = (over: Partial<Searchable> = {}): Searchable => ({
  setId: "01SET" + Math.random().toString(36).slice(2, 8).toUpperCase(),
  kind: "tut",
  title: "Einführung",
  show: "Geldhochschule",
  chap: null,
  path: null,
  summary: null,
  ...over,
});

const titles = (hits: Array<{ title: string | null }>) => hits.map((h) => h.title);

describe("what matches", () => {
  test("a title, however it is typed", () => {
    const index = new SearchIndex([set({ title: "Überblick" })]);

    expect(index.search("uberblick")).toHaveLength(1);
    expect(index.search("Überblick")).toHaveLength(1);
    expect(index.search("UBERBLICK")).toHaveLength(1);
  });

  test("an umlaut spelled out the way a keyboard without one forces", () => {
    // Two conventions, both in daily use: drop the dots ("uberblick") or
    // spell them out ("ueberblick"). A search that answers only the first is
    // a search half the people who try it give up on.
    const index = new SearchIndex([set({ title: "Überblick" })]);

    expect(index.search("ueberblick")).toHaveLength(1);
    expect(index.search("uberblick")).toHaveLength(1);
    expect(index.search("Überblick")).toHaveLength(1);
  });

  test("a summary found by a spelled-out umlaut still shows its excerpt", () => {
    const index = new SearchIndex([
      set({ title: "Interpretation", summary: "Die Volatilität steigt im Chart." }),
    ]);

    const [hit] = index.search("volatilitaet");

    expect(hit?.matched).toBe("summary");
    expect(hit?.excerpt).toContain("Volatilität");
  });

  test("part of a word, not only the start of one", () => {
    // "Kontoeröffnung" should be findable by someone who only remembers
    // "eroffnung".
    const index = new SearchIndex([set({ title: "Kontoeröffnung" })]);

    expect(index.search("eroffnung")).toHaveLength(1);
  });

  test("a show or course name", () => {
    const index = new SearchIndex([
      set({ title: "Einführung", show: "Geldhochschule" }),
      set({ title: "Dominus", show: "Spartacus", kind: "ep" }),
    ]);

    expect(titles(index.search("spartacus"))).toEqual(["Dominus"]);
  });

  test("the folder a lesson sits in", () => {
    const index = new SearchIndex([
      set({ title: "Kontoeröffnung", path: "Basislektionen/5. Broker" }),
      set({ title: "Interpretation", path: "Ausbildung Trading/3. Signal" }),
    ]);

    expect(titles(index.search("broker"))).toEqual(["Kontoeröffnung"]);
  });

  test("the text of a summary", () => {
    const index = new SearchIndex([
      set({ title: "Interpretation", summary: "Wie man ein Signal im Chart liest." }),
      set({ title: "Mobile App" }),
    ]);

    expect(titles(index.search("chart"))).toEqual(["Interpretation"]);
  });

  test("every term has to match somewhere, not just one of them", () => {
    const index = new SearchIndex([
      set({ title: "Kontoeröffnung", path: "Basislektionen/5. Broker" }),
      set({ title: "Broker Vergleich", path: "Basislektionen/1. Start" }),
    ]);

    // Only the first sits in Broker *and* is about opening an account.
    expect(titles(index.search("broker kontoeroffnung"))).toEqual(["Kontoeröffnung"]);
  });

  test("terms may match different fields of the same set", () => {
    const index = new SearchIndex([
      set({ title: "Interpretation", path: "Ausbildung Trading/3. Signal" }),
    ]);

    expect(index.search("signal interpretation")).toHaveLength(1);
  });
});

describe("what does not match", () => {
  test("an empty query finds nothing rather than everything", () => {
    const index = new SearchIndex([set(), set(), set()]);

    expect(index.search("")).toEqual([]);
    expect(index.search("   ")).toEqual([]);
  });

  test("a term nothing carries finds nothing", () => {
    const index = new SearchIndex([set({ title: "Einführung" })]);

    expect(index.search("kryptowahrung")).toEqual([]);
  });
});

describe("the order results arrive in", () => {
  test("a title match comes before a folder match", () => {
    const index = new SearchIndex([
      set({ title: "Kontoeröffnung", path: "Basislektionen/5. Broker" }),
      set({ title: "Broker Vergleich", path: "Basislektionen/1. Start" }),
    ]);

    expect(titles(index.search("broker"))).toEqual(["Broker Vergleich", "Kontoeröffnung"]);
  });

  test("a folder match comes before a summary match", () => {
    const index = new SearchIndex([
      set({ title: "Interpretation", summary: "Der Broker spielt hier keine Rolle." }),
      set({ title: "Kontoeröffnung", path: "Basislektionen/5. Broker" }),
    ]);

    expect(titles(index.search("broker"))).toEqual(["Kontoeröffnung", "Interpretation"]);
  });

  test("each hit says which field earned it", () => {
    const index = new SearchIndex([
      set({ title: "Broker Vergleich" }),
      set({ title: "Kontoeröffnung", path: "Basislektionen/5. Broker" }),
      set({ title: "Interpretation", summary: "Beim Broker einloggen." }),
    ]);

    expect(index.search("broker").map((h) => h.matched)).toEqual(["title", "path", "summary"]);
  });
});

describe("showing why a summary matched", () => {
  test("a summary hit carries the words around the match", () => {
    const long = "Vorwort. ".repeat(40) + "Der Broker verlangt eine Verifizierung. " + "Ende. ".repeat(40);
    const index = new SearchIndex([set({ title: "Kontoeröffnung", summary: long })]);

    const [hit] = index.search("broker");

    expect(hit?.excerpt).toContain("Broker");
    expect(hit?.excerpt!.length).toBeLessThan(260);
  });

  test("markdown in a summary reads as prose in the excerpt", () => {
    // Some summaries are markdown. An excerpt is a sentence shown to a
    // person, not a document, so the markers are noise.
    const index = new SearchIndex([
      set({ summary: "Ein Satz. **Optionen:** geben dem *Käufer* das ## Recht dazu." }),
    ]);

    const [hit] = index.search("optionen");

    expect(hit?.excerpt).toContain("Optionen:");
    expect(hit?.excerpt).not.toContain("**");
    expect(hit?.excerpt).not.toContain("##");
  });

  test("the window lands on the match however far into the summary it sits", () => {
    // The searched copy is not the original: umlauts spell out one character
    // longer, and every run of markup and blank lines collapses to a single
    // space. An offset taken from it drifts further the more text precedes
    // the match, so the window has to be anchored on the word itself.
    const before = "**Für größere Märkte** prüfen wir zunächst die Händler.\n\n## Weiter\n\n".repeat(30);
    const index = new SearchIndex([
      set({ summary: before + "Die Volatilität entscheidet. " + "Schluss. ".repeat(20) }),
    ]);

    const [hit] = index.search("volatilitaet");

    expect(hit?.excerpt).toContain("Volatilität");
  });

  test("a title hit needs no excerpt, because the title is already shown", () => {
    const index = new SearchIndex([set({ title: "Broker Vergleich" })]);

    expect(index.search("broker")[0]?.excerpt).toBeNull();
  });
});

describe("keeping a result list usable", () => {
  test("it stops well short of returning the library", () => {
    const many = Array.from({ length: 200 }, (_, i) => set({ title: `Einführung ${i}` }));
    const index = new SearchIndex(many);

    expect(index.search("einfuhrung").length).toBeLessThanOrEqual(50);
  });
});
