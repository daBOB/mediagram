import { afterEach, describe, expect, test } from "bun:test";
import { formatSizeInput, parseSizeInput, signOutTelegram, lockSettings } from "../public/lib/settings-api.js";

describe("parseSizeInput", () => {
  test("parses a unit suffix", () => {
    expect(parseSizeInput("8G")).toBe(8 * 1024 ** 3);
    expect(parseSizeInput("512M")).toBe(512 * 1024 ** 2);
    expect(parseSizeInput("1T")).toBe(1024 ** 4);
  });

  test("parses a plain number as bytes", () => {
    expect(parseSizeInput("1024")).toBe(1024);
  });

  test("tolerates a trailing B and surrounding space", () => {
    expect(parseSizeInput(" 8GB ")).toBe(8 * 1024 ** 3);
  });

  test("rejects nonsense", () => {
    expect(parseSizeInput("")).toBeNull();
    expect(parseSizeInput("eight gigs")).toBeNull();
    expect(parseSizeInput("-1G")).toBeNull();
  });
});

describe("formatSizeInput", () => {
  test("picks the largest unit that divides evenly", () => {
    expect(formatSizeInput(8 * 1024 ** 3)).toBe("8G");
    expect(formatSizeInput(512 * 1024 ** 2)).toBe("512M");
  });

  test("falls back to a plain byte count when nothing divides evenly", () => {
    expect(formatSizeInput(1025)).toBe("1025");
  });

  test("round-trips through parseSizeInput", () => {
    for (const bytes of [512 * 1024 ** 2, 2 * 1024 ** 3, 8 * 1024 ** 3]) {
      expect(parseSizeInput(formatSizeInput(bytes))).toBe(bytes);
    }
  });

  test("a non-positive or non-finite value formats as empty", () => {
    expect(formatSizeInput(0)).toBe("");
    expect(formatSizeInput(-5)).toBe("");
    expect(formatSizeInput(NaN)).toBe("");
  });
});

describe("bodiless writes still declare JSON", () => {
  const originalFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  /**
   * Every non-DELETE write needs `content-type: application/json` even with
   * an empty body (`refuseUnsafeBrowserWrite` on the server refuses anything
   * else with 415) — a bodiless POST that forgot this once slipped through
   * (`signOutTelegram`, caught by the stub harness walkthrough, not by this
   * suite until now).
   */
  test.each([
    ["signOutTelegram", signOutTelegram],
    ["lockSettings", lockSettings],
  ])("%s declares application/json", async (_name, call) => {
    let seen: RequestInit | undefined;
    globalThis.fetch = (async (_url: string, options: RequestInit) => {
      seen = options;
      return new Response("{}", { status: 200 });
    }) as typeof fetch;

    await call();
    expect((seen?.headers as Record<string, string>)?.["content-type"]).toBe("application/json");
  });
});
