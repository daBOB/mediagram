/**
 * The words the page shows while it starts and while the server fetches
 * artwork (`startup-progress.js`, `artwork-progress.js`). The DOM halves are
 * a line of text and a bar; what they say is decided here.
 */

import { describe, expect, test } from "bun:test";
import { artworkSentence, timeLeft } from "../public/lib/artwork-progress.js";
import { startupFraction, startupSentence } from "../public/lib/startup-progress.js";

describe("the artwork fetch's words", () => {
  test("name the step and its count, and the time left once the pace is known", () => {
    expect(artworkSentence({ step: "lookup", done: 2, total: 640, elapsedMs: 1000 })).toBe("Looking up titles · 2 of 640");
    expect(artworkSentence({ step: "download", done: 212, total: 3473, elapsedMs: 60_000 }))
      .toBe("Downloading artwork · 212 of 3,473 · about 16 min left");
  });

  test("an estimate waits for five items and three seconds", () => {
    expect(timeLeft(4, 100, 60_000)).toBeNull();
    expect(timeLeft(50, 100, 2_000)).toBeNull();
    expect(timeLeft(90, 100, 30_000)).toBe("less than a minute left");
  });
});

describe("the startup's words", () => {
  test("count the catalog's megabytes, with its size when the server gave one", () => {
    expect(startupSentence({ step: "download", received: 3_100_000, total: 22_600_000 }))
      .toBe("Downloading the catalog · 3.1 of 22.6 MB");
    expect(startupFraction({ step: "download", received: 11_300_000, total: 22_600_000 })).toBe(0.5);
  });

  test("a size the bytes have already passed is no size", () => {
    const step = { step: "download", received: 2_000_000, total: 1_400_000 } as const;
    expect(startupSentence(step)).toBe("Downloading the catalog · 2.0 MB");
    expect(startupFraction(step)).toBeNull();
  });

  test("the sorting names the titles", () => {
    expect(startupSentence({ step: "sort", titles: 28_040 })).toBe("Sorting 28,040 titles into shelves…");
    expect(startupFraction({ step: "sort", titles: 28_040 })).toBeNull();
  });
});
