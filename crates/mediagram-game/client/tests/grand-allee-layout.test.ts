// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { fieldAt, fieldPoint, type Spot } from "../src/tv/grand-allee/grand-allee-layout";
import { boardData, testBoard } from "./grand-allee-fixture";

// the hotels' model files are their names folded: Kirschblüte is kirschbluete, Belle Époque belle_epoque
const fold = (s: string) => s.replace(/ü/g, "ue").replace(/ö/g, "oe").replace(/ä/g, "ae").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/ /g, "_");
const dist = (a: Spot, b: Spot) => Math.hypot(a.x - b.x, a.z - b.z);
/** Even-odd rule. */
const inside = (p: Spot, poly: Spot[]) =>
  poly.reduce((hit, a, i) => {
    const b = poly[(i + 1) % poly.length];
    return (a.z > p.z) !== (b.z > p.z) && p.x < a.x + ((p.z - a.z) * (b.x - a.x)) / (b.z - a.z) ? !hit : hit;
  }, false);

test("the layout has the rules' 40 fields and the ten hotels' plots in hotel order, each on its frontage and its side of the road", async () => {
  const b = await testBoard(), rules = boardData();
  expect(b.fields.length).toBe(rules.fields.length);
  expect(b.plots.map((p) => p.hotel)).toEqual(rules.hotels.map((h) => h.id));
  expect(b.plots.map((p) => p.name)).toEqual(rules.hotels.map((h) => fold(h.name)));
  expect(b.plots.map((p) => [p.first, p.last])).toEqual(rules.hotels.map((h) => [h.first, h.last]));
  // the walk runs clockwise: the park (hotels 6-9) is on its right, the outer hotels on its left
  expect(b.plots.map((p) => p.side)).toEqual(rules.hotels.map((h) => (h.id >= 6 ? 1 : -1)));
});

test("board north is three's -z and east stays +x: the TV looks north", async () => {
  const b = await testBoard(), raw = b.json.fields[7];
  expect(b.fields[7].x).toBe(raw.x);
  expect(b.fields[7].z).toBe(-raw.y);
  // the north road (fields 4-19) is further from the TV than the south road (24-39)
  expect(b.fields[10].z).toBeLessThan(b.fields[30].z);
});

test("the hotels stand at 70 percent, square to the board, facing the TV", async () => {
  for (const p of (await testBoard()).plots) {
    expect(p.scale).toBe(0.7);
    expect(p.rot).toBe(0);
  }
});

test("the walk follows each field's middle line: through its centre, on to where the next one starts, and round after the last", async () => {
  const b = await testBoard(), f = b.fields[7], next = b.fields[8];
  expect(dist(fieldPoint(b, 7), f)).toBeLessThan(0.01);
  expect(dist(fieldPoint(b, 7.5), f.mid[f.mid.length - 1])).toBeLessThan(0.01);
  expect(dist(fieldPoint(b, 7.5), next.mid[0])).toBeLessThan(0.01);
  expect(dist(fieldPoint(b, 8), next)).toBeLessThan(0.01);
  expect(dist(fieldPoint(b, 40), b.fields[0])).toBeLessThan(0.01);
  expect(dist(fieldPoint(b, -1), b.fields[39])).toBeLessThan(0.01);
  expect(fieldAt(b, 40.4)).toBe(b.fields[0]);
  // on the curve: every point of the walk lies in the middle of the road, the middle line's points a metre apart
  for (let u = 0; u < 40; u += 0.05) expect(Math.min(...b.fields.flatMap((g) => g.mid).map((m) => dist(m, fieldPoint(b, u))))).toBeLessThan(0.6);
});

test("a field's turn lays three's +x along the walk at its centre", async () => {
  const b = await testBoard();
  for (const f of b.fields) {
    const a = f.mid[2], c = f.mid[4], l = dist(a, c);
    expect((Math.cos(f.rot) * (c.x - a.x) - Math.sin(f.rot) * (c.z - a.z)) / l).toBeGreaterThan(0.99);
  }
});

test("every plot's land touches the road along its frontage, on its side, and holds its hotel", async () => {
  const b = await testBoard();
  for (const p of b.plots) {
    const start = b.fields[p.first], end = b.fields[p.last], n = start.mid.length;
    // the road's edge on the plot's side where the frontage starts and ends: the outline's left edge runs forward, its right edge back
    const edge = (f: typeof start, k: number) => (p.side > 0 ? f.outline[f.outline.length - 1 - k] : f.outline[k]);
    expect([p.name, dist(p.land[0], edge(start, 0)) < 1]).toEqual([p.name, true]);
    expect([p.name, p.land.some((q) => dist(q, edge(end, n - 1)) < 1)]).toEqual([p.name, true]);
    expect([p.name, inside(p, p.land)]).toEqual([p.name, true]);
  }
});
