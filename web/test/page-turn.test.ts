/**
 * Where a page opens: at its top when gone to, where it was left when come
 * back to with Back or Forward.
 */

import { expect, test } from "bun:test";

test("a page gone to opens at its top; one come back to opens where it was left", async () => {
  // Session history as far as the module uses it: one state per entry, the
  // current one moved by going somewhere new, Back and Forward.
  const entries: unknown[] = [null];
  let at = 0;
  const history = {
    scrollRestoration: "auto",
    get state() { return entries[at]; },
    replaceState(state: unknown) { entries[at] = state; },
  };
  const window = { scrollY: 0, scrollTo(_x: number, y: number) { window.scrollY = y; } };
  const originals = new Map(["history", "window"].map((name) => [name, Object.getOwnPropertyDescriptor(globalThis, name)]));
  Object.defineProperty(globalThis, "history", { value: history, configurable: true, writable: true });
  Object.defineProperty(globalThis, "window", { value: window, configurable: true, writable: true });
  try {
    const { turnPage } = await import("../public/lib/page-turn.js");
    const classes = new Set<string>();
    const main = { classList: { add: (name: string) => classes.add(name), remove: (name: string) => classes.delete(name) } };
    const turn = () => turnPage(main, () => {}, false);
    const goTo = () => { entries.splice(at + 1, Infinity, null); at++; turn(); };

    expect(history.scrollRestoration).toBe("manual");
    window.scrollY = 1100;
    goTo();
    expect(window.scrollY).toBe(0);
    expect(classes.has("turning")).toBe(true);

    window.scrollY = 300;
    at--; turn();
    expect(window.scrollY).toBe(1100);

    at++; turn();
    expect(window.scrollY).toBe(300);

    // Going somewhere new from here is a fresh page again, not the one Back left.
    goTo();
    expect(window.scrollY).toBe(0);
  } finally {
    for (const [name, descriptor] of originals) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else Reflect.deleteProperty(globalThis, name);
    }
  }
});
