import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { collectionGrid, movieGrid, setGrid } from "../public/lib/catalog/shelf-view.js";
import { GRID, LIST } from "../public/lib/catalog/shelf-mode.js";

const priorDocument = Object.getOwnPropertyDescriptor(globalThis, "document");

beforeEach(() => {
  // Empty shelves only create their container; keep the DOM boundary local.
  Object.defineProperty(globalThis, "document", {
    configurable: true,
    value: { createElement: () => ({ className: "", textContent: "" }) },
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
