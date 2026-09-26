import { describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { GROUPS } from "../src/state/schema";
import { Settings } from "../src/state/settings";
import { applyBudget, MAX_CACHE_BUDGET_BYTES, MIN_CACHE_BUDGET_BYTES, startBudget, validateBudget } from "../src/cache/budget";

function settingsOver(db: Database | null): Settings {
  return new Settings(db);
}

describe("startBudget", () => {
  test("a stored value beats the environment", () => {
    const db = new Database(":memory:");
    for (const group of GROUPS) for (const statement of group) db.exec(statement);
    const settings = settingsOver(db);
    settings.setCacheMaxBytes(MIN_CACHE_BUDGET_BYTES);
    expect(startBudget(settings, 8 * 1024 ** 3)).toBe(MIN_CACHE_BUDGET_BYTES);
    db.close();
  });

  test("without a stored value, the environment stands, including zero", () => {
    expect(startBudget(settingsOver(null), 8 * 1024 ** 3)).toBe(8 * 1024 ** 3);
    expect(startBudget(settingsOver(null), 0)).toBe(0);
  });
});

describe("validateBudget", () => {
  test("accepts the floor and above, up to the ceiling", () => {
    expect(validateBudget(MIN_CACHE_BUDGET_BYTES)).toBe(true);
    expect(validateBudget(MAX_CACHE_BUDGET_BYTES)).toBe(true);
  });

  test("rejects below the floor, above the ceiling, zero, and non-integers", () => {
    expect(validateBudget(MIN_CACHE_BUDGET_BYTES - 1)).toBe(false);
    expect(validateBudget(MAX_CACHE_BUDGET_BYTES + 1)).toBe(false);
    expect(validateBudget(0)).toBe(false);
    expect(validateBudget(1.5 * MIN_CACHE_BUDGET_BYTES + 0.5)).toBe(false);
  });
});

describe("applyBudget", () => {
  test("persists first, then applies to the cache, and refreshes held only when it shrank", async () => {
    const calls: string[] = [];
    const settings = {
      setCacheMaxBytes: (n: number) => { calls.push(`persist:${n}`); },
    };
    const cache = {
      budget: MIN_CACHE_BUDGET_BYTES,
      setBudget: async (n: number) => {
        calls.push(`apply:${n}`);
        cache.budget = n;
        return { freedBytes: 1024 };
      },
    };
    const held = { refresh: async () => { calls.push("refresh"); } };

    const result = await applyBudget(settings, cache, held, MIN_CACHE_BUDGET_BYTES);

    expect(calls).toEqual([`persist:${MIN_CACHE_BUDGET_BYTES}`, `apply:${MIN_CACHE_BUDGET_BYTES}`, "refresh"]);
    expect(result).toEqual({ budget: MIN_CACHE_BUDGET_BYTES, freedBytes: 1024 });
  });

  test("growing the budget frees nothing and does not refresh held", async () => {
    const calls: string[] = [];
    const settings = { setCacheMaxBytes: () => {} };
    const cache = {
      budget: MIN_CACHE_BUDGET_BYTES * 2,
      setBudget: async () => ({ freedBytes: 0 }),
    };
    const held = { refresh: async () => { calls.push("refresh"); } };

    const result = await applyBudget(settings, cache, held, MIN_CACHE_BUDGET_BYTES * 2);

    expect(calls).toEqual([]);
    expect(result.freedBytes).toBe(0);
  });

  test("works without a held tracker (caching off)", async () => {
    const settings = { setCacheMaxBytes: () => {} };
    const cache = { budget: MIN_CACHE_BUDGET_BYTES, setBudget: async () => ({ freedBytes: 4096 }) };
    const result = await applyBudget(settings, cache, undefined, MIN_CACHE_BUDGET_BYTES);
    expect(result).toEqual({ budget: MIN_CACHE_BUDGET_BYTES, freedBytes: 4096 });
  });
});
