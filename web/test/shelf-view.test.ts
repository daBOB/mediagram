import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { collectionGrid, emptyState, movieGrid, setGrid } from "../public/lib/catalog/shelf-view.js";
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

describe("emptyState", () => {
  test("a kid is told to wait for a rating at its own limit, not to upload", () => {
    expect(emptyState("anime", { kidsLimit: 12 }).textContent).toBe("Nothing rated FSK 12 or under yet.");
    expect(emptyState("movies", { kidsLimit: 6 }).textContent).toBe("Nothing rated FSK 6 or under yet.");
  });

  test("anime points at the edit command, not an upload one — there is nothing to upload", () => {
    const empty = emptyState("anime") as unknown as { className: string; children: unknown[] };
    expect(empty.className).toBe("empty");
    expect(empty.children).toContainEqual(
      expect.objectContaining({ textContent: "mediagram edit <set-id> --anime yes" }),
    );
  });
});
