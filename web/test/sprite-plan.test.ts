/** Covers `sprite-plan`: where a title's preview frames sit in one image. */

import { describe, expect, test } from "bun:test";
import { COLUMNS, spritePlan, tileAt } from "../public/lib/sprite-plan.js";

describe("the layout for a title", () => {
  test("a 59-minute film gets about as many tiles as it aims for", () => {
    const plan = spritePlan(3539)!;
    expect(plan.interval).toBe(30);
    expect(plan.tiles).toBe(118);
    expect(plan.columns).toBe(10);
    expect(plan.rows).toBe(12);
  });

  test("a four-minute lesson gets a finer interval, not fewer tiles", () => {
    const plan = spritePlan(252)!;
    expect(plan.interval).toBe(3);
    expect(plan.tiles).toBe(84);
  });

  test("a very short title does not get an interval below two seconds", () => {
    // Finer than this is a sheet of near-identical frames.
    expect(spritePlan(10)!.interval).toBe(2);
    expect(spritePlan(1)!.interval).toBe(2);
  });

  test("and a very long one does not get one above a minute", () => {
    // Ten hours would otherwise ask for a five-minute interval, which is not
    // a preview of anything.
    expect(spritePlan(36000)!.interval).toBe(60);
  });

  test("a title shorter than one interval still has one tile", () => {
    const plan = spritePlan(1)!;
    expect(plan.tiles).toBe(1);
    expect(plan.columns).toBe(1);
    expect(plan.rows).toBe(1);
  });
});

describe("a runtime nobody knows", () => {
  test("has no layout, rather than a guessed one", () => {
    // A guessed length puts every preview in the wrong place, which looks like
    // a broken player rather than an imprecise one.
    for (const duration of [undefined, null, 0, -1, Number.NaN, "long"]) {
      expect(spritePlan(duration as never)).toBeNull();
    }
  });

  test("and asking for a tile of it is nothing too", () => {
    expect(tileAt(null, 30)).toBeNull();
  });
});

describe("which tile a position lands in", () => {
  const plan = spritePlan(3539)!;

  test("the start is the first tile", () => {
    expect(tileAt(plan, 0)).toEqual({ index: 0, x: 0, y: 0 });
  });

  test("a position inside the first interval is still the first tile", () => {
    expect(tileAt(plan, 29)!.index).toBe(0);
    expect(tileAt(plan, 30)!.index).toBe(1);
  });

  test("the offsets walk across and then down", () => {
    expect(tileAt(plan, 30 * 9)).toMatchObject({ index: 9, x: 9 * 160, y: 0 });
    expect(tileAt(plan, 30 * 10)).toMatchObject({ index: 10, x: 0, y: 90 });
  });

  test("the very last second does not fall off the end of the grid", () => {
    // 3539 / 30 is exactly 118, which is one past a zero-based 118-tile grid.
    const last = tileAt(plan, 3539)!;
    expect(last.index).toBe(plan.tiles - 1);
    expect(last.index).toBeLessThan(plan.tiles);
  });

  test("and neither does a position past the end", () => {
    expect(tileAt(plan, 99999)!.index).toBe(plan.tiles - 1);
  });

  test("a position that is not one is the start", () => {
    for (const at of [undefined, null, -5, Number.NaN]) {
      expect(tileAt(plan, at as never)!.index).toBe(0);
    }
  });

  test("every tile of a full row is in the same row", () => {
    const ys = new Set<number>();
    for (let i = 0; i < COLUMNS; i++) ys.add(tileAt(plan, i * plan.interval)!.y);
    expect(ys.size).toBe(1);
  });
});
