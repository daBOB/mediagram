// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import {
  FIELDS, FIELD_COUNT, FIELD_KINDS, FIELD_SPACING, GROUND, HOTEL_FIELDS, HOTEL_NAMES, LOTS, LOT_D, LOT_W, ROAD_WIDTH, roadLine, roadPoint,
} from "../src/tv/bauherr/bauherr-layout";

// the rules are the source of truth: the layout only places what bauherr_board.rs defines
const rules = readFileSync(new URL("../../src/bauherr_board.rs", import.meta.url), "utf8");
const rulesHotels = [...rules.matchAll(/Hotel \{ name: "(\w+)".*?fields: \[([\d, ]+)\].*?costs: &\[([\d, ]+)\]/g)].map((m) => ({
  name: m[1], fields: m[2].split(",").map(Number), stages: m[3].split(",").length,
}));

test("the layout places the rules' fields, kinds and hotels", () => {
  expect(rules.match(/const KINDS: &str = "([^"]+)"/)![1]).toBe(FIELD_KINDS);
  expect(FIELD_KINDS.length).toBe(FIELD_COUNT);
  expect(rulesHotels.map((h) => h.name)).toEqual([...HOTEL_NAMES]);
  expect(rulesHotels.map((h) => h.fields)).toEqual(HOTEL_FIELDS.map((f) => [...f]));
});

test("all 36 fields have their own place on the road, evenly spaced and inside the ground print", () => {
  expect(FIELDS).toHaveLength(36);
  FIELDS.forEach((f, i) => {
    const next = FIELDS[(i + 1) % 36];
    const gap = Math.hypot(next.x - f.x, next.z - f.z);
    expect(gap).toBeGreaterThan(FIELD_SPACING * 0.7);
    expect(gap).toBeLessThan(FIELD_SPACING * 1.05);
    expect(f.x).toBeGreaterThan(GROUND.x0 + 3);
    expect(f.x).toBeLessThan(GROUND.x1 - 3);
    expect(f.z).toBeGreaterThan(GROUND.z0 + 3);
    expect(f.z).toBeLessThan(GROUND.z1 - 3);
  });
  expect(new Set(FIELDS.map((f) => `${f.x.toFixed(2)},${f.z.toFixed(2)}`)).size).toBe(36);
});

test("the road is closed: a walk past field 35 arrives at field 0, and between fields it follows the road", () => {
  const a = roadPoint(0), b = roadPoint(36), mid = roadPoint(35.5);
  expect(Math.hypot(a.x - b.x, a.z - b.z)).toBeLessThan(1e-6);
  expect(Math.hypot(mid.x - (FIELDS[35].x + FIELDS[0].x) / 2, mid.z - (FIELDS[35].z + FIELDS[0].z) / 2)).toBeLessThan(0.3);
});

// distance from a point to a lot's rectangle
const lotDistance = (lot: (typeof LOTS)[number], x: number, z: number) => {
  const c = Math.cos(lot.rot), s = Math.sin(lot.rot), dx = x - lot.x, dz = z - lot.z;
  return Math.hypot(Math.max(0, Math.abs(dx * c - dz * s) - LOT_W / 2), Math.max(0, Math.abs(dx * s + dz * c) - LOT_D / 2));
};

test("each hotel's entrance fields sit visibly next to its lot, and no lot is on the road", () => {
  const line = roadLine(0.1);
  for (const lot of LOTS) {
    const name = HOTEL_NAMES[lot.plot];
    for (const f of HOTEL_FIELDS[lot.plot]) expect(lotDistance(lot, FIELDS[f].x, FIELDS[f].z), `${name} field ${f}`).toBeLessThan(5.5);
    // the nearest of them is right beside the lot
    expect(Math.min(...HOTEL_FIELDS[lot.plot].map((f) => lotDistance(lot, FIELDS[f].x, FIELDS[f].z)))).toBeLessThan(2.0);
    expect(Math.min(...line.map((p) => lotDistance(lot, p.x, p.z))), `${name} clear of the road`).toBeGreaterThan(ROAD_WIDTH / 2 + 0.15);
  }
});

test("lots do not overlap", () => {
  for (const a of LOTS) for (const b of LOTS) {
    if (a.plot >= b.plot) continue;
    const c = Math.cos(a.rot), s = Math.sin(a.rot);
    const corners = [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([i, j]) => [a.x + ((i * LOT_W) / 2) * c + ((j * LOT_D) / 2) * s, a.z - ((i * LOT_W) / 2) * s + ((j * LOT_D) / 2) * c]);
    for (const [x, z] of corners) expect(lotDistance(b, x, z), `${HOTEL_NAMES[a.plot]} and ${HOTEL_NAMES[b.plot]}`).toBeGreaterThan(0);
  }
});
