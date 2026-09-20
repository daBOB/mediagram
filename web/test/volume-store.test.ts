/** Covers `volume-store`: a remembered volume, and a store that refuses. */

import { afterEach, describe, expect, test } from "bun:test";
import { readVolume, writeVolume } from "../public/lib/volume-store.js";

/** Stands in for `localStorage`, which `bun:test` has no window to hold. */
function store(initial: Record<string, string> = {}, throws = false) {
  const held = { ...initial };
  const localStorage = {
    getItem(key: string) {
      if (throws) throw new Error("site data blocked");
      return key in held ? held[key]! : null;
    },
    setItem(key: string, value: string) {
      if (throws) throw new Error("site data blocked");
      held[key] = value;
    },
  };
  (globalThis as { window?: unknown }).window = { localStorage };
  return held;
}

afterEach(() => {
  delete (globalThis as { window?: unknown }).window;
});

describe("reading one back", () => {
  test("what was written", () => {
    store({ "mediagram.volume": JSON.stringify({ volume: 0.4, muted: false }) });
    expect(readVolume()).toEqual({ volume: 0.4, muted: false });
  });

  test("nothing written yet is full and unmuted", () => {
    store();
    expect(readVolume()).toEqual({ volume: 1, muted: false });
  });

  test("muted is remembered separately from how loud it was", () => {
    // Unmuting should give back the volume it was at, not full.
    store({ "mediagram.volume": JSON.stringify({ volume: 0.3, muted: true }) });
    expect(readVolume()).toEqual({ volume: 0.3, muted: true });
  });
});

describe("a store someone has been editing", () => {
  test("a volume outside nought-to-one is not an instruction", () => {
    for (const volume of [-1, 2, "loud", null]) {
      store({ "mediagram.volume": JSON.stringify({ volume }) });
      expect(readVolume().volume).toBe(1);
    }
  });

  test("nought is a volume, and is kept", () => {
    store({ "mediagram.volume": JSON.stringify({ volume: 0 }) });
    expect(readVolume().volume).toBe(0);
  });

  test("anything but true is not muted", () => {
    store({ "mediagram.volume": JSON.stringify({ volume: 1, muted: "yes" }) });
    expect(readVolume().muted).toBe(false);
  });

  test("not JSON at all", () => {
    store({ "mediagram.volume": "{{{" });
    expect(readVolume()).toEqual({ volume: 1, muted: false });
  });
});

describe("a store that refuses", () => {
  test("reading throws, and the player still has a volume", () => {
    // A private window does not return null here — it throws, and an uncaught
    // throw would take the bar's controls down with it.
    store({}, true);
    expect(readVolume()).toEqual({ volume: 1, muted: false });
  });

  test("writing throws, and nothing else does", () => {
    store({}, true);
    expect(() => writeVolume({ volume: 0.5, muted: false })).not.toThrow();
  });

  test("and with no window at all", () => {
    expect(readVolume()).toEqual({ volume: 1, muted: false });
    expect(() => writeVolume({ volume: 0.5, muted: false })).not.toThrow();
  });
});

describe("writing", () => {
  test("keeps both halves", () => {
    const held = store();
    writeVolume({ volume: 0.25, muted: true });
    expect(JSON.parse(held["mediagram.volume"]!)).toEqual({ volume: 0.25, muted: true });
  });
});
