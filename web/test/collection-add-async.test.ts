import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { addTitles } from "../public/lib/catalog/collection-add.js";

/** The DOM IO used by this control; events and deferred HTTP responses remain real. */
class ElementBoundary extends EventTarget {
  className = "";
  value = "";
  hidden = false;
  children: ElementBoundary[] = [];
  private text = "";

  get textContent(): string { return this.text + this.children.map((child) => child.textContent).join(""); }
  set textContent(value: string) { this.text = value; this.children = []; }
  append(...children: ElementBoundary[]) { this.children.push(...children); }
  setAttribute(_name: string, _value: string) {}
  focus() {}
}

let priorDocument: PropertyDescriptor | undefined;
let priorFetch: PropertyDescriptor | undefined;
let elements: ElementBoundary[];

function element(className: string) {
  const found = elements.find((node) => node.className === className);
  if (!found) throw new Error(`Missing ${className}`);
  return found;
}

function input(query: string) {
  const field = element("add-search");
  field.value = query;
  field.dispatchEvent(new Event("input"));
}

function deferredSearch() {
  const response = Promise.withResolvers<Response>();
  const requested = Promise.withResolvers<string>();
  Object.defineProperty(globalThis, "fetch", { configurable: true, value: (url: unknown) => {
    requested.resolve(String(url));
    return response.promise;
  } });
  return { response, requested };
}

const settled = () => new Promise<void>((resolve) => setTimeout(resolve, 0));
const matches = (title: string) => Response.json({ hits: [{ setId: title, title }] });

beforeEach(() => {
  priorDocument = Object.getOwnPropertyDescriptor(globalThis, "document");
  priorFetch = Object.getOwnPropertyDescriptor(globalThis, "fetch");
  elements = [];
  Object.defineProperty(globalThis, "document", { configurable: true, value: {
    createElement: () => {
      const node = new ElementBoundary();
      elements.push(node);
      return node;
    },
  } });
  addTitles({ id: "list", items: [] }, () => {});
  element("quiet").dispatchEvent(new Event("click"));
});

afterEach(() => {
  input("");
  if (priorDocument) Object.defineProperty(globalThis, "document", priorDocument);
  else Reflect.deleteProperty(globalThis, "document");
  if (priorFetch) Object.defineProperty(globalThis, "fetch", priorFetch);
  else Reflect.deleteProperty(globalThis, "fetch");
});

describe("collection search responses", () => {
  test("distinguishes HTTP failure from a successful search with no matches", async () => {
    const failed = deferredSearch();
    input("missing");
    expect(await failed.requested.promise).toBe("/api/search?q=missing");
    failed.response.resolve(new Response(null, { status: 503 }));
    await settled();
    expect(element("add-results").textContent).toBe("Could not reach the library.");

    const empty = deferredSearch();
    input("none");
    await empty.requested.promise;
    empty.response.resolve(Response.json({ hits: [] }));
    await settled();
    expect(element("add-results").textContent).toBe("Nothing matches.");
  });

  for (const outcome of ["success", "failure"] as const) {
    test(`clearing the query discards a pending ${outcome}`, async () => {
      const pending = deferredSearch();
      input("old");
      await pending.requested.promise;
      input("");
      if (outcome === "success") pending.response.resolve(matches("Old title"));
      else pending.response.reject(new Error("Connection lost"));
      await settled();
      expect(element("add-results").textContent).toBe("");
    });

    test(`closing the picker discards a pending ${outcome}`, async () => {
      const pending = deferredSearch();
      input("old");
      await pending.requested.promise;
      element("quiet").dispatchEvent(new Event("click"));
      if (outcome === "success") pending.response.resolve(matches("Old title"));
      else pending.response.reject(new Error("Connection lost"));
      await settled();
      expect(element("add-panel").hidden).toBe(true);
      expect(element("add-results").textContent).toBe("");
    });
  }

  test("a new keystroke invalidates old work before its debounce fires", async () => {
    const old = deferredSearch();
    input("old");
    await old.requested.promise;
    input("new");
    old.response.resolve(matches("Old title"));
    await settled();
    expect(element("add-results").textContent).toBe("");
  });

  test("an older search cannot replace newer results", async () => {
    const old = deferredSearch();
    input("old");
    await old.requested.promise;
    const latest = deferredSearch();
    input("new");
    await latest.requested.promise;
    latest.response.resolve(matches("New title"));
    await settled();
    old.response.resolve(matches("Old title"));
    await settled();
    expect(element("add-results").textContent).toBe("New titleadd");
  });
});
