// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { InkLog, strokesOf } from "../src/ink/ink-log";
import { ERASER_SCALE, lineWidthPx } from "../src/ink/ink-style";
import { paintTail } from "../src/ink/paint-ink";
import type { DrawOp } from "../src/protocol/DrawOp";

const line = (points: number[], color = 0, width = 1): DrawOp => ({ t: "line", color, width, points });
const more = (points: number[]): DrawOp => ({ t: "more", points });

test("from 0 starts afresh", () => {
  const log = new InkLog();
  expect(log.apply({ turn: 1, from: 0, ops: [line([0, 0])] })).toBe("reset");
  expect(log.apply({ turn: 1, from: 0, ops: [line([5, 5]), more([6, 6])] })).toBe("reset");
  expect(log.ops.length).toBe(2);
  expect(log.turn).toBe(1);
});

test("a continuation appends", () => {
  const log = new InkLog();
  log.apply({ turn: 1, from: 0, ops: [line([0, 0])] });
  expect(log.apply({ turn: 1, from: 1, ops: [more([1, 1])] })).toBe("append");
  expect(log.ops.length).toBe(2);
});

test("a gap is ignored", () => {
  const log = new InkLog();
  log.apply({ turn: 1, from: 0, ops: [line([0, 0])] });
  expect(log.apply({ turn: 1, from: 3, ops: [more([1, 1])] })).toBe("ignored");
  expect(log.ops.length).toBe(1);
});

test("subscribers hear reset on a replacement and append with from on a continuation", () => {
  const log = new InkLog();
  const heard: [string, number][] = [];
  const off = log.subscribe((c, from) => heard.push([c, from]));
  log.apply({ turn: 1, from: 0, ops: [line([0, 0])] });
  log.apply({ turn: 1, from: 1, ops: [more([1, 1])] });
  log.apply({ turn: 1, from: 9, ops: [more([1, 1])] });
  off();
  log.apply({ turn: 1, from: 0, ops: [] });
  expect(heard).toEqual([["reset", 0], ["append", 1]]);
});

test("a new turn from 0 resets", () => {
  const log = new InkLog();
  log.apply({ turn: 1, from: 0, ops: [line([0, 0]), more([1, 1])] });
  expect(log.apply({ turn: 2, from: 0, ops: [line([2, 2])] })).toBe("reset");
  expect(log.turn).toBe(2);
  expect(log.ops.length).toBe(1);
  expect(log.apply({ turn: 1, from: 1, ops: [more([1, 1])] })).toBe("ignored");
});

test("line then more is one stroke", () => {
  const s = strokesOf([line([0, 0, 10, 10], 2, 1), more([20, 20])]);
  expect(s).toEqual([{ color: 2, width: 1, points: [0, 0, 10, 10, 20, 20] }]);
});

test("undo drops the last stroke", () => {
  expect(strokesOf([line([0, 0]), line([1, 1]), { t: "undo" }]).length).toBe(1);
});

test("clear empties", () => {
  expect(strokesOf([line([0, 0]), line([1, 1]), { t: "clear" }])).toEqual([]);
});

test("more after undo extends nothing", () => {
  const s = strokesOf([line([0, 0]), line([1, 1]), { t: "undo" }, more([9, 9])]);
  expect(s).toEqual([{ color: 0, width: 1, points: [0, 0] }]);
});

test("the eraser is three times wider", () => {
  expect(ERASER_SCALE).toBe(3);
  expect(lineWidthPx(5, 1, 1000)).toBeCloseTo(lineWidthPx(0, 1, 1000) * 3);
  expect(lineWidthPx(0, 1, 1000)).toBeCloseTo(10);
});

function recorder() {
  const calls: string[] = [];
  const ctx = new Proxy({ calls } as Record<string, unknown>, {
    get: (t, k: string) => (k in t ? t[k] : (...a: unknown[]) => void calls.push(`${k}(${a.join(",")})`)),
    set: () => true,
  });
  return { calls, ctx: ctx as unknown as CanvasRenderingContext2D };
}

test("paintTail paints only the new ops and asks for a repaint on undo", () => {
  const ops: DrawOp[] = [line([0, 0, 10000, 0]), more([10000, 10000])];
  const r = recorder();
  expect(paintTail(r.ctx, ops, 1, 1600, 900)).toBe(true);
  expect(r.calls).toContain("moveTo(1600,0)");
  expect(r.calls).toContain("lineTo(1600,900)");
  expect(r.calls).not.toContain("moveTo(0,0)");
  expect(paintTail(recorder().ctx, [...ops, { t: "undo" }], 2, 1600, 900)).toBe(false);
});

test("paintTail ignores a more whose stroke was undone earlier", () => {
  const ops: DrawOp[] = [line([0, 0]), { t: "undo" }, more([5000, 5000])];
  const r = recorder();
  expect(paintTail(r.ctx, ops, 2, 1600, 900)).toBe(true);
  expect(r.calls).toEqual([]);
});
