// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { FluechtigBoard } from "../src/protocol/FluechtigBoard";
import { area, inset, inside, streetBlocks } from "../src/tv/fluechtig/city-blocks";
import { fieldRings, planLines } from "../src/tv/fluechtig/plan-lines";
import { fieldRadius, stadtplanArt } from "../src/tv/fluechtig/stadtplan-art";

/** A 4×3 street grid, 100 apart, numbered row by row: six square blocks. */
const spots = Array.from({ length: 12 }, (_, i) => ({ x: 100 + (i % 4) * 100, y: 100 + Math.floor(i / 4) * 100 }));
const streets = [
  ...[1, 2, 3, 5, 6, 7, 9, 10, 11].map((a) => ({ a, b: a + 1 })),
  ...[1, 2, 3, 4, 5, 6, 7, 8].map((a) => ({ a, b: a + 4 })),
];

test("the blocks of a street grid are its squares, without the outside", () => {
  const blocks = streetBlocks(spots, streets);
  expect(blocks.length).toBe(6);
  for (const b of blocks) expect(Math.abs(area(b))).toBe(10000);
});

test("a block drawn inset leaves the street's width on every side", () => {
  const [block] = streetBlocks(spots, streets);
  const shape = inset(block, 5)!;
  expect(Math.abs(area(shape))).toBeCloseTo(8100);
  for (const p of shape) expect(inside(p, block)).toBe(true);
  expect(inset(block, 60)).toBeNull();
});

const board: FluechtigBoard = {
  width: 500,
  height: 400,
  spots,
  links: [...streets.map((l) => ({ ...l, kind: "taxi" as const })), { a: 1, b: 3, kind: "bus" }, { a: 3, b: 11, kind: "ubahn" }, { a: 5, b: 8, kind: "schatten" }],
  river: [
    { x: -50, y: 150 },
    { x: 550, y: 150 },
  ],
};

test("the same city always gets the same plan, with its parks' trees inside the city", () => {
  const art = stadtplanArt(board);
  expect(art).toEqual(stadtplanArt(board));
  expect(art.radius).toBe(fieldRadius(spots));
  expect(art.tones.join("").split("Z").length - 1 + art.parks.split("Z").length - 1).toBe(6);
  for (const t of art.trees) expect(t.x > 100 && t.x < 400 && t.y > 100 && t.y < 300).toBe(true);
  expect(art.ferryName).not.toBeNull();
});

test("a field's ring is its strongest line, and the lines are drawn once per kind", () => {
  const rings = fieldRings(board);
  expect([rings.get(1), rings.get(3), rings.get(5), rings.get(2)]).toEqual(["bus", "ubahn", "schatten", undefined]);
  const lines = planLines(board);
  expect(lines.streets.split("M").length - 1).toBe(17);
  expect(lines.bus).toContain("Q");
  expect(lines.jetties.split("M").length - 1).toBe(2);
});
