// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { RANKS, SUITS } from "../src/ui/card-face";
import { BACK_CELL, COLS, ROWS, cellOf, worthCell } from "../src/tv/cards/cards-print";
import { DISCARD_AT, fanCard, pileAt, seatSpot, yawFacing } from "../src/tv/cards/cards-layout";
import { pairCard } from "../src/tv/cards/cards-durak";
import { textsFor } from "../src/tv/cards/cards-texts";

test("Durak pre-draws its banner, trump note and seat tags", () => {
  const t = textsFor("Durak");
  expect(t.banner).toContain(" hat alles geschlagen – Bito!");
  expect(t.note).toContain("Trumpf ♠ Pik");
  expect(t.marker).toContain("Verteidiger");
});

test("six Durak pairs lie apart and a defence card lies on its attack", () => {
  const spots = Array.from({ length: 6 }, (_, p) => pairCard(p, 0));
  expect(new Set(spots.map((s) => `${s.x}|${s.z}`)).size).toBe(6);
  const [a, d] = [pairCard(2, 0), pairCard(2, 1)];
  expect(d.y).toBeGreaterThan(a.y);
  expect(Math.abs(d.x - a.x)).toBeLessThan(1.25);
});

test("every card has its own atlas cell and the back comes after them", () => {
  const cells = SUITS.flatMap((suit) => RANKS.map((rank) => cellOf({ suit, rank })));
  expect(new Set(cells).size).toBe(52);
  expect(Math.max(...cells)).toBeLessThan(BACK_CELL);
  expect(cellOf(null)).toBe(BACK_CELL);
});

test("the first seat sits at the bottom and faces the middle", () => {
  const s = seatSpot(0, 4);
  expect(s.z).toBeGreaterThan(0);
  expect(Math.abs(s.x)).toBeLessThan(1e-6);
  expect(s.dz).toBeLessThan(0);
  // a card's top edge points to -z at yaw 0, so facing -z needs no turn
  expect(yawFacing(0, -1)).toBeCloseTo(0);
});

test("seats of any table size stand apart", () => {
  for (let n = 2; n <= 8; n++) {
    const spots = Array.from({ length: n }, (_, i) => seatSpot(i, n));
    for (let i = 0; i < n; i++)
      for (let j = i + 1; j < n; j++) expect(Math.hypot(spots[i].x - spots[j].x, spots[i].z - spots[j].z)).toBeGreaterThan(3);
  }
});

test("a fan stays inside its seat's length however many cards it holds", () => {
  const s = seatSpot(2, 7);
  const first = fanCard(s, 0, 20), last = fanCard(s, 19, 20);
  expect(Math.hypot(first.x - last.x, first.z - last.z)).toBeLessThanOrEqual(s.fan + 1e-6);
});

test("the table pre-draws what Mau-Mau says", () => {
  const t = textsFor("Mau-Mau");
  expect(t.banner).toContain(" vergisst Mau – Strafkarte!");
  expect(t.banner).toContain(" wünscht Herz");
  expect(t.note).toContain("Zieh 8");
});

test("a lone pile lies where the discards do, several lie in a row centred on them", () => {
  expect(pileAt(0, 1)).toEqual(DISCARD_AT);
  const row = [0, 1, 2].map((i) => pileAt(i, 3));
  expect(row[1].x).toBeCloseTo(DISCARD_AT.x);
  expect(row[1].x - row[0].x).toBeCloseTo(row[2].x - row[1].x);
  expect(row[2].x - row[0].x).toBeGreaterThan(2 * 1.25);
});

test("every text the Schwimmen host sends for seats and the table is pre-drawn", () => {
  const t = textsFor("Schwimmen");
  expect(t.banner).toContain(" klopft!");
  expect(t.note).toContain("Feuer – 31!");
  expect(t.marker).toContain("●●● · klopft");
});

test("a Schwimmen reveal needs no new texture: loss markers are pre-drawn and values come from the number atlas", () => {
  const t = textsFor("Schwimmen");
  for (const m of ["●●○ · −1", "●○○ · −1", "schwimmt · −1", "raus"]) expect(t.marker).toContain(m);
  // half points: whole values map to their own number, 30½ to the last cell, 31 to 31
  expect([0, 44, 60, 62].map(worthCell)).toEqual([0, 22, 30, 31]);
  expect(worthCell(61)).toBe(COLS * ROWS - 1);
});

test("every Mau-Mau draw banner is pre-drawn, from no card to a stack of eight", () => {
  const t = textsFor("Mau-Mau");
  for (const m of [" zieht keine Karte", " zieht eine Karte", " zieht 2 Karten", " zieht 8 Karten"]) expect(t.banner).toContain(m);
});
