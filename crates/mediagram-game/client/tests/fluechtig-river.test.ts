// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { ferryLane, riverY, smoothPath } from "../src/tv/fluechtig/fluechtig-river";

const river = [
  { x: -40, y: 200 },
  { x: 100, y: 240 },
  { x: 300, y: 220 },
  { x: 500, y: 260 },
];

test("the river's middle is read off its points, and holds past both ends", () => {
  expect([riverY(river, 100), riverY(river, 300)]).toEqual([240, 220]);
  expect(riverY(river, 200)).toBe(230);
  expect([riverY(river, -500), riverY(river, 900)]).toEqual([200, 260]);
});

test("a ferry lane runs from one pier's jetty to the other's, on the water all the way", () => {
  const lane = ferryLane(river, { x: 80, y: 190 }, { x: 306, y: 250 });
  const points = lane.slice(1).split("L").map((p) => p.split(" ").map(Number));
  expect(points[0]).toEqual([80, Number(riverY(river, 80).toFixed(1))]);
  expect(points.at(-1)).toEqual([306, Number(riverY(river, 306).toFixed(1))]);
  for (const [x, y] of points) expect(Math.abs(y - riverY(river, x))).toBeLessThan(0.1);
  expect(points.length).toBeGreaterThan(8);
});

test("the drawn river passes through every point it was given", () => {
  const d = smoothPath(river);
  expect(d.startsWith("M-40 200C")).toBe(true);
  for (const p of river.slice(1)) expect(d).toContain(`,${p.x} ${p.y}`);
});
