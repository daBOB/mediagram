import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { collectionGrid, emptyState, movieGrid, setGrid } from "../public/lib/catalog/shelf-view.js";
import { plateOf } from "../public/lib/catalog/plate.js";
import { SECTIONS } from "../public/lib/catalog/sections.js";
import { GRID, LIST } from "../public/lib/catalog/shelf-mode.js";

const priorDocument = Object.getOwnPropertyDescriptor(globalThis, "document");

beforeEach(() => {
  // Empty shelves only create their container; `emptyState` also appends a
  // few text nodes and a `<code>`, so the stub node collects what it is
  // given rather than only carrying a class and a text.
  Object.defineProperty(globalThis, "document", {
    configurable: true,
    value: {
      createElement: () => ({
        className: "", textContent: "", children: [] as unknown[],
        append(...items: unknown[]) { this.children.push(...items); },
      }),
    },
  });
});

afterEach(() => {
  if (priorDocument) Object.defineProperty(globalThis, "document", priorDocument);
  else Reflect.deleteProperty(globalThis, "document");
});

type Options = { mode?: "list" | "grid" };
const grids = [
  ["movies", (options?: Options) => movieGrid([], () => {}, options)],
  ["sets", (options?: Options) => setGrid([], () => {}, options)],
  ["collections", (options?: Options) => collectionGrid("series", [], () => {}, options)],
] as const;

describe.each(grids)("%s shelf layout", (_name, render) => {
  test("accepts grid through the shared options shape", () => {
    expect(render({ mode: GRID }).className).toBe("grid plates");
  });

  test("uses list mode when omitted or explicitly selected", () => {
    expect(render().className).toBe("grid");
    expect(render({}).className).toBe("grid");
    expect(render({ mode: LIST }).className).toBe("grid");
  });
});

describe("a book's cover", () => {
  type Node = { className: string; children: Node[] };
  const thumbs = (root: Node): Node[] =>
    [root, ...root.children.flatMap((child) => (child && typeof child === "object" ? thumbs(child) : []))]
      .filter((node) => node.className?.startsWith("thumb"));

  // A card touches more of the element API than the shelf tests' stub offers.
  const fullStub = () => {
    const make = (): Node => {
      const node = { className: "", textContent: "", children: [] as Node[], style: {}, dataset: {} } as Node & Record<string, unknown>;
      node.append = (...items: Node[]) => void node.children.push(...items);
      for (const name of ["addEventListener", "setAttribute", "remove"]) node[name] = () => {};
      node.classList = { add() {}, toggle() {}, remove() {} };
      return node;
    };
    Object.defineProperty(globalThis, "document", { configurable: true, value: { createElement: make, createTextNode: make } });
  };

  test("is drawn whole on a watch-state shelf, where a film's poster is not", () => {
    fullStub();
    const set = (kind: string) => ({ setId: kind, title: kind, kind, poster: "p" }) as never;
    const grid = setGrid([set("book"), set("movie")], () => {}) as unknown as Node;
    expect(thumbs(grid).map((node) => node.className)).toEqual(["thumb square", "thumb"]);
  });
});

describe("a book's cover on a viewer's own list", () => {
  test("is drawn whole there too", () => {
    Object.defineProperty(globalThis, "document", {
      configurable: true,
      value: { createElement: () => ({ className: "", children: [], append() {}, setAttribute() {} }) },
    });
    const book = plateOf({ setId: "b", title: "B", kind: "book", poster: null } as never);
    const film = plateOf({ setId: "f", title: "F", kind: "movie", poster: null } as never);
    expect([book.className, film.className]).toEqual(["thumb square", "thumb"]);
  });
});

describe("emptyState", () => {
  test("a kid is told to wait for a rating at its own limit, not to upload", () => {
    expect(emptyState("anime", { kidsLimit: 12 }).textContent).toBe("Nothing rated FSK 12 or under yet.");
    expect(emptyState("movies", { kidsLimit: 6 }).textContent).toBe("Nothing rated FSK 6 or under yet.");
  });

  test("a section this profile is not shown says only that it is empty: no upload hint, no age limit", () => {
    expect(emptyState("anime", { hidden: ["anime"] }).textContent).toBe(SECTIONS.anime.empty);
    expect(emptyState("movies", { kidsLimit: 12, hidden: ["movies"] }).textContent).toBe(SECTIONS.movies.empty);
    expect(emptyState("movies", { kidsLimit: 12, hidden: ["anime"] }).textContent).toBe("Nothing rated FSK 12 or under yet.");
  });

  test("anime points at the edit command, not an upload one — there is nothing to upload", () => {
    const empty = emptyState("anime") as unknown as { className: string; children: unknown[] };
    expect(empty.className).toBe("empty");
    expect(empty.children).toContainEqual(
      expect.objectContaining({ textContent: "mediagram edit <set-id> --anime yes" }),
    );
  });
});
