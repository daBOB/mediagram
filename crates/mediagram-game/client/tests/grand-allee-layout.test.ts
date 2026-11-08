// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { fieldAt, fieldPoint } from "../src/tv/grand-allee/grand-allee-layout";
import { boardData, testBoard } from "./grand-allee-fixture";

// the hotels' model files are their names folded: Kirschblüte is kirschbluete, Belle Époque belle_epoque
const fold = (s: string) => s.replace(/ü/g, "ue").replace(/ö/g, "oe").replace(/ä/g, "ae").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/ /g, "_");

test("the layout has the rules' 40 fields and the ten hotels' plots, in hotel order", async () => {
  const b = await testBoard(), rules = boardData();
  expect(b.fields.length).toBe(rules.fields.length);
  expect(b.plots.map((p) => p.hotel)).toEqual(rules.hotels.map((h) => h.id));
  expect(b.plots.map((p) => p.name)).toEqual(rules.hotels.map((h) => fold(h.name)));
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

test("between fields the road runs straight from centre to centre, and wraps after the last", async () => {
  const b = await testBoard(), last = b.fields[39], first = b.fields[0], mid = fieldPoint(b, 39.5);
  expect(mid.x).toBeCloseTo((last.x + first.x) / 2);
  expect(mid.z).toBeCloseTo((last.z + first.z) / 2);
  expect(fieldPoint(b, 40).x).toBeCloseTo(first.x);
  expect(fieldPoint(b, -1).z).toBeCloseTo(last.z);
  expect(fieldAt(b, 40.4)).toBe(first);
});

test("a field's turn lays three's +x along the walk", async () => {
  const b = await testBoard();
  b.fields.forEach((f, i) => {
    // the heading is the curve's at the field's centre; in the sharpest bend the chord to the next centre is 38 degrees off it
    const next = b.fields[(i + 1) % 40], dx = next.x - f.x, dz = next.z - f.z, l = Math.hypot(dx, dz);
    expect((Math.cos(f.rot) * dx - Math.sin(f.rot) * dz) / l).toBeGreaterThan(Math.cos((45 * Math.PI) / 180));
  });
});

test("every hotel stands beside its own frontage", async () => {
  const b = await testBoard(), rules = boardData();
  rules.hotels.forEach((h) => {
    const plot = b.plots[h.id], near = Math.min(...b.fields.slice(h.first, h.last + 1).map((f) => Math.hypot(f.x - plot.x, f.z - plot.z)));
    expect(near).toBeLessThan(Math.max(plot.w, plot.d));
  });
});
