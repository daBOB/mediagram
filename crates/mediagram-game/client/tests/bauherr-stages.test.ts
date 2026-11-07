// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { parseWorld, type WorldRec } from "../src/tv/bauherr/bauherr-data";
import { shownRanges } from "../src/tv/bauherr/bauherr-stages";
import { pawnPlace } from "../src/tv/bauherr/bauherr-pawn-place";
import type { BauherrScene } from "../src/protocol/BauherrScene";
import type { BauherrTurn } from "../src/protocol/BauherrTurn";

// the baked town as committed, against the stage counts in the rules
const rules = readFileSync(new URL("../../src/bauherr_board.rs", import.meta.url), "utf8");
const stages = [...rules.matchAll(/costs: &\[([\d, ]+)\]/g)].map((m) => m[1].split(",").length);
const world: WorldRec = parseWorld(JSON.parse(readFileSync(new URL("../public/bauherr/world.json", import.meta.url), "utf8")), stages);

test("every hotel has geometry for every stage the rules give it", () => {
  expect(stages).toEqual([3, 4, 4, 6, 4, 6, 5, 5]);
  world.hotels.forEach((h, i) => expect(h.stages).toHaveLength(stages[i]));
  // a stage no longer than the file's mesh, and heights to clip a rising stage by
  for (const h of world.hotels) for (const s of h.stages) {
    expect(s.p[1]).toBeGreaterThan(0);
    expect(s.axis === "y" ? s.top : s.x1 - s.x0).toBeGreaterThan(0);
  }
});

test("parseWorld refuses a file with the wrong stage count", () => {
  expect(() => parseWorld(JSON.parse(JSON.stringify(world)), [2, ...stages.slice(1)])).toThrow(/stages/);
  expect(() => parseWorld({}, stages)).toThrow();
});

test("a hotel's stages are laid out one after another, so showing n of them is one range", () => {
  for (const h of world.hotels) {
    expect(h.sale[0] + h.sale[1]).toBe(h.overlay[0]);
    expect(h.overlay[0] + h.overlay[1]).toBe(h.stages[0].p[0]);
    h.stages.slice(1).forEach((s, k) => expect(s.p[0]).toBe(h.stages[k].p[0] + h.stages[k].p[1]));
    h.stages.slice(1).forEach((s, k) => expect(s.o[0]).toBe(h.stages[k].o[0] + h.stages[k].o[1]));
  }
});

test("a lot for sale shows its meadow and board; a bought one the building site, the owner's fence and sign", () => {
  const h = world.hotels[3];
  expect(shownRanges(h, false, 0).base).toEqual(h.sale);
  expect(shownRanges(h, false, 0).owner[1]).toBe(0);
  expect(shownRanges(h, true, 0).base).toEqual(h.overlay);
  expect(shownRanges(h, true, 0).owner).toEqual([0, h.own0[1]]);
});

test("each stage built adds its walls, owner pieces and shadow, up to the hotel's last", () => {
  for (const h of world.hotels) {
    let walls = 0, pieces = h.own0[1];
    h.stages.forEach((s, k) => {
      walls += s.p[1];
      pieces += s.o[1];
      const shown = shownRanges(h, true, k + 1);
      expect(shown.base).toEqual([h.stages[0].p[0], walls]);
      expect(shown.owner).toEqual([0, pieces]);
    });
    const all = shownRanges(h, true, h.stages.length);
    expect(all.shadow[1]).toBeGreaterThan(0);
    expect(shownRanges(h, true, 1).shadow[1]).toBeLessThanOrEqual(all.shadow[1]);
    // a stage still rising has its shadow drawn but its walls come from the rise mesh
    expect(shownRanges(h, true, 1, 2).shadow[1]).toBeGreaterThanOrEqual(shownRanges(h, true, 1).shadow[1]);
  }
});

test("the plot sign comes after every stage's owner pieces, so a built stage never draws it", () => {
  for (const h of world.hotels) {
    const last = h.stages[h.stages.length - 1].o;
    expect(h.sign[1]).toBeGreaterThan(0);
    expect(h.sign[0]).toBe(last[0] + last[1]);
    expect(shownRanges(h, true, h.stages.length).owner[1]).toBeLessThanOrEqual(h.sign[0]);
  }
});

const seats = [{ player: 7, field: 12, cash: 0, worth: 0, out: false }];
const scene = (turn: BauherrTurn) => ({ seats, turn }) as unknown as BauherrScene;

test("a pawn stands on its field, and hops one field per hop_ms while its owner walks", () => {
  const walking: BauherrTurn = { stage: "walking", player: 7, value: 4, from: 8, to: 12, started_at: 1000, hop_ms: 300, bonus: false };
  expect(pawnPlace(scene({ stage: "roll", player: 7 }), 0, 5000)).toEqual({ u: 12, lift: 0 });
  expect(pawnPlace(scene(walking), 0, 900)).toEqual({ u: 8, lift: 0 });
  expect(pawnPlace(scene(walking), 0, 1150)).toEqual({ u: 8.5, lift: expect.closeTo(0.55, 2) });
  expect(pawnPlace(scene(walking), 0, 1300)).toEqual({ u: 9, lift: expect.closeTo(0, 5) });
  expect(pawnPlace(scene(walking), 0, 9000)).toEqual({ u: 12, lift: 0 });
  // another player's walk does not move this pawn
  expect(pawnPlace(scene({ ...walking, player: 3 }), 0, 1150)).toEqual({ u: 12, lift: 0 });
});
