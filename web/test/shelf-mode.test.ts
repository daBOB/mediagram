import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { GRID, LIST, modeFrom, setShelfMode, shelfMode } from "../public/lib/catalog/shelf-mode.js";

/** A `localStorage` that can be told to behave like a blocked one. */
function fakeStorage(options: { throws?: boolean } = {}) {
  const held = new Map<string, string>();
  return {
    held,
    getItem(key: string) {
      if (options.throws) throw new Error("site data is blocked");
      return held.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      if (options.throws) throw new Error("site data is blocked");
      held.set(key, value);
    },
    removeItem(key: string) {
      if (options.throws) throw new Error("site data is blocked");
      held.delete(key);
    },
  };
}

const originalWindow = (globalThis as { window?: unknown }).window;
afterEach(() => {
  (globalThis as { window?: unknown }).window = originalWindow;
});

function useStorage(storage: ReturnType<typeof fakeStorage>) {
  (globalThis as { window?: unknown }).window = { localStorage: storage };
  return storage;
}

describe("reading a stored mode", () => {
  test("takes grid only when that is what was written", () => {
    expect(modeFrom("grid")).toBe(GRID);
    expect(modeFrom("list")).toBe(LIST);
  });

  test("falls back to the list for anything it does not recognise", () => {
    // A value written by a later version of the page must not leave a viewer
    // with no shelf at all.
    expect(modeFrom(null)).toBe(LIST);
    expect(modeFrom("")).toBe(LIST);
    expect(modeFrom("wall-of-plates")).toBe(LIST);
  });
});

describe("remembering the choice", () => {
  let storage: ReturnType<typeof fakeStorage>;
  beforeEach(() => {
    storage = useStorage(fakeStorage());
  });

  test("starts on the list, which is what this catalogue was designed as", () => {
    expect(shelfMode()).toBe(LIST);
  });

  test("keeps grid across a read", () => {
    setShelfMode(GRID);
    expect(shelfMode()).toBe(GRID);
  });

  test("stores the default as its absence rather than as a value", () => {
    setShelfMode(GRID);
    expect(storage.held.size).toBe(1);
    setShelfMode(LIST);
    // A viewer who never chose and a viewer who chose the list are the same
    // viewer, and clearing site data returns both to the same place.
    expect(storage.held.size).toBe(0);
    expect(shelfMode()).toBe(LIST);
  });

  test("answers with the mode it settled on", () => {
    expect(setShelfMode(GRID)).toBe(GRID);
    expect(setShelfMode("nonsense")).toBe(LIST);
  });
});

describe("a browser with storage blocked", () => {
  beforeEach(() => {
    useStorage(fakeStorage({ throws: true }));
  });

  test("still shows a shelf rather than throwing", () => {
    // A private window throws on the read instead of answering null.
    expect(shelfMode()).toBe(LIST);
  });

  test("still accepts a choice, which lasts the page load", () => {
    expect(() => setShelfMode(GRID)).not.toThrow();
    expect(setShelfMode(GRID)).toBe(GRID);
  });
});
